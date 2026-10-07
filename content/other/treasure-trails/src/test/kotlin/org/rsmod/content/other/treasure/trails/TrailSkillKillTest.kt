package org.rsmod.content.other.treasure.trails

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.Mockito
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@ResourceLock("ServerCacheManager")
class TrailSkillKillTest {
    @Test fun `only assigned kills complete a task without advancing the trail`() {
        for ((row, types) in TrailSkillKillHook.targetsByRow.filterKeys { it != TrailSkillKillHook.shadeTask && it != TrailSkillKillHook.spiritualTask }) for (type in types) {
            val f = Fixture(row)
            val npc = Npc(checkNotNull(ServerCacheManager.getNpc(type)), CoordGrid(3435, 3565, 2))
            Mockito.`when`(f.areas.inArea("area.slayer_tower", npc.coords)).thenReturn(true)
            f.kill(npc)
            assertEquals(0, f.state().phase)
            f.assign()
            f.kill(Npc("npc.zeah_lizardshaman_spawn", npc.coords))
            assertEquals(9, f.state().phase)
            f.kill(npc)
            assertEquals(10, f.state().phase)
            assertEquals(2, f.state().completed)
            val completed = f.player.inv[0]!!
            f.kill(npc)
            assertSame(completed, f.player.inv[0])
        }
    }

    @Test fun `Nechryael task checks the dead NPC location rather than the player location`() {
        val f = Fixture(TrailSkillKillHook.towerTask)
        f.assign()
        val npc = Npc("npc.slayer_nechryael", CoordGrid(3435, 3565, 2))
        f.player.coords = CoordGrid(3200, 3200, 0)
        f.kill(npc)
        assertEquals(9, f.state().phase)
        Mockito.`when`(f.areas.inArea("area.slayer_tower", npc.coords)).thenReturn(true)
        f.kill(npc)
        assertEquals(10, f.state().phase)
        Mockito.verify(f.areas, Mockito.times(2)).inArea("area.slayer_tower", npc.coords)
    }

    @Test fun `kill credit belongs only to the credited player and respects skill requirements`() {
        val f = Fixture("dbrow.cluehelper_skillchallenge_elite_10".asRSCM())
        f.assign()
        val npc = Npc("npc.slayer_dustdevil", CoordGrid(3200, 3200, 0))
        val other = Fixture("dbrow.cluehelper_skillchallenge_elite_10".asRSCM())
        other.assign()
        f.hook.onKill(NpcDeathKillContext(other.player, npc, 0))
        assertEquals(9, f.state().phase)
        assertEquals(10, other.state().phase)
        f.player.statMap.setCurrentLevel("stat.slayer", 1)
        f.kill(npc)
        assertEquals(9, f.state().phase)
    }

    private class Fixture(row: Int) {
        val progress = TrailProgress(TrailCatalog(), DefaultGameRandom(42))
        val areas = Mockito.mock(AreaChecker::class.java)
        val player = Player().apply {
            inv = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())), arrayOfNulls(28))
            worn = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.worn".asRSCM())), arrayOfNulls(14))
            statMap.setCurrentLevel("stat.slayer", 99)
        }
        val hook = TrailSkillKillHook(progress, TrailTargets(progress), TrailRequirements(progress.catalog), areas)
        init {
            val scripts = ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache())
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { scripts.startup() }
            val clue = progress.catalog.clues.getValue(row)
            player.inv[0] = InvObj(checkNotNull(ServerCacheManager.getItem(progress.catalog.item(clue))), 1, TrailState(row, 6, 2).encode())
        }
        fun assign() { assertTrue(progress.phase(player, 0, player.inv[0]!!, 9)) }
        fun state() = checkNotNull(progress.state(player.inv[0]!!))
        fun kill(npc: Npc) = hook.onKill(NpcDeathKillContext(player, npc, 0))
    }
    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
