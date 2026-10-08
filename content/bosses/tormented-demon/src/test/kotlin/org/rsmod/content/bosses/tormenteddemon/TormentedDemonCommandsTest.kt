package org.rsmod.content.bosses.tormenteddemon

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.or2.central.account.Rights
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.Mockito.*
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@ResourceLock("ServerCacheManager")
class TormentedDemonCommandsTest {
    @Test fun `native test command adds usable combat and crafting kits atomically`() {
        for (kit in listOf("kit", "items")) {
            val f = Fixture(); f.commands.execute(f.player, "testtd", listOf(kit))
            assertTrue(f.player.inv.objs.filterNotNull().isNotEmpty())
            if (kit == "kit") assertEquals(1, f.player.inv.count("obj.emberlight"))
            else {
                assertEquals(3, f.player.inv.count("obj.tormented_synapse"))
                assertTrue(ArclightState.ready(f.player.inv.objs.filterNotNull().single { it.id == "obj.arclight".asRSCM() }))
            }
            val full = Fixture(); for (slot in 0..27) full.player.inv[slot] = InvObj("obj.abyssal_whip")
            full.commands.execute(full.player, "testtd", listOf(kit))
            assertEquals(28, full.player.inv.count("obj.abyssal_whip"))
            assertEquals(0, full.player.inv.count("obj.tormented_synapse"))
        }
    }

    @Test fun `test destinations select one two three chambers or normal entrance`() {
        for ((arg, destination) in mapOf("1" to CoordGrid(4136, 4376), "2" to CoordGrid(4072, 4422), "3" to CoordGrid(4045, 4390), "entrance" to TormentedTempleScript.ENTRANCE)) {
            val f = Fixture(); f.commands.execute(f.player, "testtd", listOf(arg)); assertEquals(destination, f.player.coords)
        }
        val f = Fixture(); val start = f.player.coords
        f.commands.execute(f.player, "testtd", listOf("bad")); f.commands.execute(f.player, "testtd", listOf("1", "extra"))
        assertEquals(start, f.player.coords)
        f.commands.execute(f.player, "testtd", emptyList())
        assertEquals(CoordGrid(4061, 4464, 0), f.player.coords)
    }
    private class Fixture {
        val commands = CheatCommandMap(); val bus = EventBus()
        val collision = CollisionFlagMap().apply { for (c in listOf(CoordGrid(4136, 4376), CoordGrid(4072, 4422), CoordGrid(4045, 4390), TormentedTempleScript.TELEPORT_DESTINATION, TormentedTempleScript.ENTRANCE)) allocateIfAbsent(c.x, c.z, c.level) }
        val context = ProtectedAccessContextFactory.empty().copy(getEventBus = { bus }, getCollision = { collision }, getAreaChecker = { mock(AreaChecker::class.java) }, getTeleportValidator = { PlayerTeleportValidator(emptySet()) })
        val player = Player().apply { modLevel = Rights.ADMINISTRATOR; coords = CoordGrid(3222, 3218); inv = Inventory(ServerCacheManager.getInventory("inv.inv".asRSCM())!!, arrayOfNulls(28)) }
        init {
            val launcher = ProtectedAccessLauncher(mock(ProtectedAccessContextFactory::class.java).apply { `when`(create()).thenReturn(context) })
            val scripts = ScriptContext(bus, commands, EngineQueueCache())
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { scripts.startup() }
            with(TormentedDemonTestCommands(launcher)) { scripts.startup() }
        }
    }
    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
