package org.rsmod.content.bosses.tormenteddemon

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import kotlin.coroutines.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.Mockito.*
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.interact.HeldInteractions
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.worn.HeldEquipOp
import org.rsmod.api.route.BoundValidator
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.HeldOp
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.*
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@ResourceLock("ServerCacheManager")
class TormentedTempleTest {
    @Test fun `native scroll action reaches the approved cave arrival and consumes only one scroll`() {
        val f = Fixture(); f.player.inv[0] = InvObj("obj.teleportscroll_guthixian_temple", 2)
        f.scroll(); assertEquals(2, f.player.inv.count("obj.teleportscroll_guthixian_temple"))
        f.finish()
        assertEquals(CoordGrid(4061, 4464, 0), f.player.coords)
        assertEquals(1, f.player.inv.count("obj.teleportscroll_guthixian_temple"))
    }

    @Test fun `teleport denial at start or after delay preserves scroll and location`() {
        for (late in listOf(true, false)) {
            val f = Fixture(); val start = f.player.coords; f.player.inv[0] = InvObj("obj.teleportscroll_guthixian_temple")
            if (!late) f.denial = "Blocked"
            f.scroll(); f.denial = "Blocked"; f.finish()
            assertEquals(start, f.player.coords)
            assertEquals(1, f.player.inv.count("obj.teleportscroll_guthixian_temple"))
        }
    }

    @Test fun `native climb then transformed skull door enters and hole exits Temple`() {
        val f = Fixture()
        f.loc("loc.luc2_gt_temple_wall_climb1", CoordGrid(4061, 4554)); f.finish()
        assertEquals(CoordGrid(4061, 4553, 1), f.player.coords)
        f.result = null; f.loc("loc.luc2_gt_temple_wall_climb2", CoordGrid(4060, 4552, 1)); f.finish()
        assertEquals(CoordGrid(4060, 4551, 2), f.player.coords)
        f.result = null; f.loc("loc.luc2_gt_main_temple_door", CoordGrid(4062, 4548, 2)); f.finish()
        assertEquals(TormentedTempleScript.CAVE_ENTRANCE, f.player.coords)
        assertEquals(2, f.player.vars["varbit.td_multiway_indicator"])
        f.result = null; f.loc("loc.luc2_gt_wallkit_entrance_hole", CoordGrid(4061, 4467)); f.finish()
        assertEquals(CoordGrid(4063, 4547, 2), f.player.coords)
        assertEquals(0, f.player.vars["varbit.td_multiway_indicator"])
    }

    @Test fun `all chamber centers use the canonical single double triple limits`() {
        for (c in listOf(CoordGrid(4139, 4383), CoordGrid(4041, 4420), CoordGrid(4042, 4450))) assertEquals(1, TormentedTemple.limit(c))
        for (c in listOf(CoordGrid(4076, 4429), CoordGrid(4105, 4469), CoordGrid(4141, 4465), CoordGrid(4149, 4451), CoordGrid(4151, 4425))) assertEquals(2, TormentedTemple.limit(c))
        for (c in listOf(CoordGrid(4041, 4379), CoordGrid(4087, 4375))) assertEquals(3, TormentedTemple.limit(c))
        assertFalse(TormentedTemple.contains(CoordGrid(4063, 4557)))
    }
    private class Fixture {
        val bus = EventBus(); val collision = CollisionFlagMap(); val areas = mock(AreaChecker::class.java)
        var denial: String? = null
        val validator = PlayerTeleportValidator(setOf(PlayerTeleportValidateHook { _, _, _ -> denial }))
        val player = Player().apply {
            inv = Inventory(ServerCacheManager.getInventory("inv.inv".asRSCM())!!, arrayOfNulls(28))
            coords = CoordGrid(3222, 3218); currentMapClock = 100; processedMapClock = 100
        }
        val coroutine = GameCoroutine("Temple native access")
        var result: Result<Unit>? = null
        val context = ProtectedAccessContextFactory.empty().copy(getEventBus = { bus }, getCollision = { collision }, getAreaChecker = { areas }, getTeleportValidator = { validator })
        val locs = LocInteractions(mock(BoundValidator::class.java), bus)
        init {
            for (c in listOf(CoordGrid(3222, 3218), TormentedTempleScript.TELEPORT_DESTINATION, TormentedTempleScript.ENTRANCE, TormentedTempleScript.CAVE_ENTRANCE, CoordGrid(4061, 4553, 1), CoordGrid(4060, 4551, 2), CoordGrid(4063, 4547, 2))) collision.allocateIfAbsent(c.x, c.z, c.level)
            val scripts = ScriptContext(bus, CheatCommandMap(), EngineQueueCache())
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { scripts.startup() }
            with(TormentedTempleScript(validator, areas, collision)) { scripts.startup() }
        }
        fun scroll() = launch {
            val constructor = HeldInteractions::class.java.declaredConstructors.single().apply { isAccessible = true }
            val held = constructor.newInstance(bus, mock(constructor.parameterTypes[1]), mock(constructor.parameterTypes[2]), HeldEquipOp(bus)) as HeldInteractions
            held.interact(this, player.inv, 0, HeldOp.Op1)
        }
        fun loc(symbol: String, coords: CoordGrid) = launch {
            val type = ServerCacheManager.getObject(symbol.asRSCM())!!
            val loc = BoundLocInfo(LocInfo(2, coords, LocEntity(type.id, 10, 0)), type)
            bus.publish(this, locs.opTrigger(player, loc, InteractionOp.Op1) ?: error("Missing $symbol native op"))
        }
        fun launch(action: suspend ProtectedAccess.() -> Unit) {
            player.activeCoroutine = coroutine
            val block: suspend () -> Unit = { action(ProtectedAccess(player, coroutine, context)) }
            block.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(value: Result<Unit>) { result = value }
            })
            result?.getOrThrow()
        }
        fun finish() { repeat(20) { result?.getOrThrow(); if (result != null) return; player.currentMapClock++; player.processedMapClock = player.currentMapClock; coroutine.advance() }; fail<Unit>("Unfinished Temple route") }
    }
    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
