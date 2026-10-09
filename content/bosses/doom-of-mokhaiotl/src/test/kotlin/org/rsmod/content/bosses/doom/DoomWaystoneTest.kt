package org.rsmod.content.bosses.doom

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import kotlin.coroutines.*
import kotlin.coroutines.cancellation.CancellationException
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.Mockito.mock
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.interact.HeldInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.worn.HeldEquipOp
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.HeldOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@ResourceLock("ServerCacheManager")
class DoomWaystoneTest {
    @Test fun `native Channel teleports to pre-lair and consumes exactly one waystone after delay`() {
        val f = Fixture()
        f.channel()
        assertEquals(f.origin, f.player.coords)
        assertEquals(2, f.player.inv.count("obj.dom_teleport_item"))
        f.finish()
        assertEquals(DoomArena.LOBBY, f.player.coords)
        assertEquals(1, f.player.inv.count("obj.dom_teleport_item"))
        f.coroutine.advance()
        assertEquals(1, f.player.inv.count("obj.dom_teleport_item"))
    }

    @Test fun `initial and late normal teleport denial preserve location and the whole stack`() {
        for (late in listOf(false, true)) {
            val f = Fixture()
            if (!late) f.denial = "Teleport blocked"
            f.channel()
            f.denial = "Teleport blocked"
            f.finish()
            f.assertPreserved()
        }
    }

    @Test fun `unavailable destination never consumes a waystone`() {
        val f = Fixture(allocateDestination = false)
        f.channel()
        f.finish()
        f.assertPreserved()
    }

    @Test fun `final native teleport rejection does not commit prepared consumption`() {
        val f = Fixture()
        f.denyOnCheck = 3
        f.channel()
        f.finish()
        f.assertPreserved()
    }

    @Test fun `replaced stack and relocated player cannot complete a stale Channel`() {
        for (replace in listOf(false, true)) {
            val f = Fixture()
            f.channel()
            if (replace) f.player.inv[0] = InvObj("obj.dom_teleport_item", 2)
            else f.player.coords = f.origin.translateX(1)
            val location = f.player.coords
            f.finish()
            assertEquals(location, f.player.coords)
            assertEquals(2, f.player.inv.count("obj.dom_teleport_item"))
        }
    }

    @Test fun `death logout and disconnect during Channel preserve the waystone`() {
        val stops: List<(Player) -> Unit> = listOf(
            { it.statMap.setCurrentLevel("stat.hitpoints", 0) },
            { it.pendingLogout = true },
            { it.loggingOut = true },
            { it.forceDisconnect = true },
            { it.clientDisconnected.set(true) },
        )
        for (stop in stops) {
            val f = Fixture()
            f.channel()
            stop(f.player)
            f.finish()
            f.assertPreserved()
        }
    }

    @Test fun `cancelled native interaction cannot teleport or consume later`() {
        val f = Fixture()
        f.channel()
        f.coroutine.cancel()
        assertTrue(f.result?.exceptionOrNull() is CancellationException)
        repeat(6) { f.player.currentMapClock++; f.coroutine.advance() }
        f.assertPreserved()
    }

    private class Fixture(allocateDestination: Boolean = true) {
        val bus = EventBus()
        val collision = CollisionFlagMap()
        val areas = mock(AreaChecker::class.java)
        var denial: String? = null
        var denyOnCheck: Int? = null
        private var checks = 0
        val validator = PlayerTeleportValidator(setOf(PlayerTeleportValidateHook { _, _, _ ->
            checks++
            if (checks == denyOnCheck) "Teleport blocked" else denial
        }))
        val origin = CoordGrid(3222, 3218)
        val player = Player().apply {
            inv = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())), arrayOfNulls(28))
            inv[0] = InvObj("obj.dom_teleport_item", 2)
            coords = origin
            currentMapClock = 100
            processedMapClock = 100
        }
        val coroutine = GameCoroutine("Native waystone Channel")
        var result: Result<Unit>? = null
        val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { bus }, getCollision = { collision },
            getAreaChecker = { areas }, getTeleportValidator = { validator },
        )
        init {
            collision.allocateIfAbsent(origin.x, origin.z, origin.level)
            if (allocateDestination) collision.allocateIfAbsent(DoomArena.LOBBY.x, DoomArena.LOBBY.z, DoomArena.LOBBY.level)
            val scripts = ScriptContext(bus, CheatCommandMap(), EngineQueueCache())
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { scripts.startup() }
            with(DoomWaystone(validator, areas, collision)) { scripts.startup() }
        }
        fun channel() {
            val constructor = HeldInteractions::class.java.declaredConstructors.single().apply { isAccessible = true }
            val held = constructor.newInstance(bus, mock(constructor.parameterTypes[1]), mock(constructor.parameterTypes[2]), HeldEquipOp(bus)) as HeldInteractions
            player.activeCoroutine = coroutine
            val action: suspend () -> Unit = { held.interact(ProtectedAccess(player, coroutine, context), player.inv, 0, HeldOp.Op1) }
            action.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { this@Fixture.result = result }
            })
            result?.getOrThrow()
        }
        fun finish() {
            repeat(10) {
                result?.getOrThrow()
                if (result != null) return
                player.currentMapClock++
                player.processedMapClock = player.currentMapClock
                coroutine.advance()
            }
            fail<Unit>("Waystone interaction did not finish")
        }
        fun assertPreserved() {
            assertEquals(origin, player.coords)
            assertEquals(2, player.inv.count("obj.dom_teleport_item"))
        }
    }

    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
