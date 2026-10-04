package org.rsmod.content.other.treasure.trails

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.events.skilling.SkillingActionCompleteEvent
import org.rsmod.api.player.events.skilling.SkillingActionContext
import org.rsmod.api.player.events.skilling.SkillingProductSource
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@ResourceLock("ServerCacheManager")
class TrailSkillChallengesTest {
    @Test fun `Sherlock assigns an elite task without advancing or rerolling an assigned clue`() {
        val f = Fixture()
        f.clue(TrailCatalog.sherlockIntro, 0)
        val initial = f.player.inv[0]!!
        assertTrue(f.progress.assignSherlock(f.player, 0, initial))
        val assigned = f.state()
        assertEquals("skillchallenge", f.progress.catalog.clues.getValue(assigned.row).kind)
        assertEquals(9, assigned.phase)
        assertEquals(2, assigned.completed)
        assertEquals(6, assigned.total)
        assertFalse(f.progress.assignSherlock(f.player, 0, initial))
        assertFalse(f.progress.assignSherlock(f.player, 0, f.player.inv[0]!!))
        f.player.inv[0] = f.player.inv[0]!!.copy()
        assertEquals(assigned, f.state())
    }

    @Test fun `crafting tasks require assignment and the correct successful primary product`() {
        for ((row, expected) in TrailSkillChallenges.products.filterValues { it.first == "stat.crafting" }) {
            val f = Fixture()
            f.clue(row, 0)
            f.product(expected.second)
            assertEquals(0, f.state().phase)
            assertTrue(f.progress.phase(f.player, 0, f.player.inv[0]!!, 9))
            f.product("obj.abyssal_whip")
            f.product(expected.second, skill = "stat.smithing")
            f.product(expected.second, count = 0)
            f.product(expected.second, bonus = true)
            assertEquals(9, f.state().phase)
            f.product(expected.second)
            assertEquals(10, f.state().phase)
            assertEquals(2, f.state().completed)
            f.product(expected.second)
            assertEquals(10, f.state().phase)
        }
    }

    @Test fun `a correct product below the required skill level does not finish the task`() {
        val f = Fixture()
        f.clue("dbrow.cluehelper_skillchallenge_master_6".asRSCM(), 9)
        f.player.statMap.setCurrentLevel("stat.crafting", 1)
        f.product("obj.unstrung_dragonstone_amulet")
        assertEquals(9, f.state().phase)
    }

    private class Fixture {
        val events = EventBus()
        val progress = TrailProgress(TrailCatalog(), DefaultGameRandom(42))
        val player = Player().apply {
            inv = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())), arrayOfNulls(28))
            worn = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.worn".asRSCM())), arrayOfNulls(14))
            statMap.setCurrentLevel("stat.crafting", 99)
        }
        init {
            val script = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { script.startup() }
            with(TrailSkillChallenges(progress, TrailTargets(progress), TrailRequirements(progress.catalog))) { script.startup() }
        }
        fun clue(row: Int, phase: Int) {
            val clue = progress.catalog.clues.getValue(row)
            player.inv[0] = InvObj(checkNotNull(ServerCacheManager.getItem(progress.catalog.item(clue))), 1, TrailState(row, 6, 2, phase).encode())
        }
        fun state() = checkNotNull(progress.state(player.inv[0]!!))
        fun product(item: String, skill: String = "stat.crafting", count: Int = 1, bonus: Boolean = false) {
            events.publish(SkillingActionCompleteEvent(player, SkillingActionContext.Product(skill, item, count, 0.0, SkillingProductSource.Crafting, bonus)))
        }
    }
    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
