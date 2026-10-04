package org.rsmod.content.other.treasure.trails

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import kotlin.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@ResourceLock("ServerCacheManager")
class TrailBoxesTest {
    @Test fun `all scroll box tiers open through native item events into matching clues`() {
        for (tier in TrailTier.entries) {
            val f = Fixture()
            f.player.inv[0] = InvObj(checkNotNull(ServerCacheManager.getItem(tier.box)), 2)
            f.open()
            assertEquals(1, f.player.inv.count("obj.league_clue_box_${tier.key}"))
            val clue = f.player.inv.objs.filterNotNull().single { it.id != tier.box }
            val state = checkNotNull(f.progress.state(clue))
            assertEquals(tier, f.progress.catalog.clues.getValue(state.row).tier)
            assertTrue(state.total in tier.steps)
            assertEquals(0, state.completed)
            assertEquals(0, f.player.inv.count("obj.trail_reward_casket_${tier.key}"))
        }
    }

    @Test fun `full inventory preserves stacked boxes and permits replacing the last box`() {
        for (tier in TrailTier.entries) {
            val f = Fixture()
            for (i in 1..27) f.player.inv[i] = InvObj("obj.abyssal_whip")
            f.player.inv[0] = InvObj(checkNotNull(ServerCacheManager.getItem(tier.box)), 2)
            val before = f.player.inv.objs.toList()
            assertFalse(f.progress.openBox(f.player, f.player.inv, 0, tier))
            assertEquals(before, f.player.inv.objs.toList())
            f.player.inv[0] = f.player.inv[0]!!.copy(count = 1)
            assertTrue(f.progress.openBox(f.player, f.player.inv, 0, tier))
            assertNotNull(f.progress.state(f.player.inv[0]!!))
            assertEquals(27, f.player.inv.count("obj.abyssal_whip"))
        }
    }

    @Test fun `progress survives item copying and caskets appear only after the final step`() {
        for (tier in TrailTier.entries) {
            val f = Fixture()
            f.player.inv[0] = InvObj(checkNotNull(ServerCacheManager.getItem(tier.box)))
            f.open()
            val total = f.progress.state(f.player.inv[0]!!)!!.total
            repeat(total) { done ->
                val original = f.player.inv[0]!!.copy()
                f.player.inv[0] = original
                assertEquals(done, f.progress.state(original)!!.completed)
                assertTrue(f.progress.advance(f.player, f.player.inv, 0, original))
                assertFalse(f.progress.advance(f.player, f.player.inv, 0, original))
                assertEquals(if (done == total - 1) 1 else 0, f.player.inv.count("obj.trail_reward_casket_${tier.key}"))
            }
        }
    }
    private class Fixture {
        val events = EventBus()
        val progress = TrailProgress(TrailCatalog(), DefaultGameRandom(42))
        val player = Player().apply { inv = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())), arrayOfNulls(28)) }
        init {
            val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { context.startup() }
            with(TrailScrollScript(progress)) { context.startup() }
        }
        fun open() {
            val coroutine = GameCoroutine("clue-box-test")
            val access = ProtectedAccess(player, coroutine, ProtectedAccessContextFactory.empty().copy(getEventBus = { events }))
            val item = player.inv[0]!!
            val type = checkNotNull(ServerCacheManager.getItem(item.id))
            var result: Result<Unit>? = null
            val block: suspend () -> Unit = { events.publish(access, HeldObjEvents.Op1(0, item, type, player.inv)) }
            block.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(value: Result<Unit>) { result = value }
            })
            checkNotNull(result).getOrThrow()
        }
    }
    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
