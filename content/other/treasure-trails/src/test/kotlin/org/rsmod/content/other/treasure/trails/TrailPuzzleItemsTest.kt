package org.rsmod.content.other.treasure.trails

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@ResourceLock("ServerCacheManager")
class TrailPuzzleItemsTest {
    @Test fun `puzzle box issuance is atomic and reopening does not duplicate it`() {
        for (light in listOf(false, true)) {
            val f = Fixture()
            val active = f.active()
            for (slot in 1..27) f.player.inv[slot] = InvObj("obj.abyssal_whip")
            assertFalse(TrailPuzzleItems.give(f.player, active, light))
            assertSame(active.item, f.player.inv[0])
            assertNull(TrailPuzzleItems.owned(f.player, active.state))
            f.player.inv[27] = null
            assertTrue(TrailPuzzleItems.give(f.player, f.active(), light))
            val box = checkNotNull(TrailPuzzleItems.owned(f.player, active.state))
            assertEquals(if (light) 6 else 1, f.active().state.phase)
            assertTrue(TrailPuzzleItems.give(f.player, f.active(), light))
            assertSame(box, TrailPuzzleItems.owned(f.player, active.state))
            assertEquals(1, f.player.inv.objs.filterNotNull().count { it.vars == TrailPuzzleItems.owner(active.state) && it.id != active.item.id })
        }
    }

    @Test fun `handing in a puzzle consumes only the matching box and rejects stale ownership`() {
        val f = Fixture()
        assertTrue(TrailPuzzleItems.give(f.player, f.active(), false))
        val owned = checkNotNull(TrailPuzzleItems.owned(f.player, f.active().state))
        val boxSlot = f.player.inv.objs.indexOfFirst { it === owned }
        f.player.inv[boxSlot] = owned.copy(vars = owned.vars + 1)
        f.player.inv[10] = owned
        assertTrue(f.progress.phase(f.player, 0, f.player.inv[0]!!, 2))
        val currentBox = checkNotNull(TrailPuzzleItems.owned(f.player, f.active().state))
        assertTrue(f.progress.advance(f.player, f.player.inv, 0, f.player.inv[0]!!, listOf(currentBox)))
        assertNotNull(f.player.inv[boxSlot])
        assertNull(f.player.inv[10])
        val clue = f.player.inv[0]!!
        assertFalse(f.progress.advance(f.player, f.player.inv, 0, clue, listOf(currentBox)))
        assertSame(clue, f.player.inv[0])
    }

    @Test fun `copied persistent puzzle ownership survives inventory reload`() {
        val f = Fixture()
        assertTrue(TrailPuzzleItems.give(f.player, f.active(), true))
        val state = f.active().state
        for (slot in 0..27) f.player.inv[slot] = f.player.inv[slot]?.copy()
        assertNotNull(TrailPuzzleItems.owned(f.player, state))
        assertTrue(TrailPuzzleItems.give(f.player, f.active(), true))
    }

    private class Fixture {
        val progress = TrailProgress(TrailCatalog(), DefaultGameRandom(42))
        val player = Player().apply { inv = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())), arrayOfNulls(28)) }
        init {
            val context = ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache())
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { context.startup() }
            val clue = progress.catalog.forTier(TrailTier.MASTER).first()
            player.inv[0] = InvObj(checkNotNull(ServerCacheManager.getItem(progress.catalog.item(clue))), 1, TrailState(clue.row, 6).encode())
        }
        fun active(): ActiveTrail {
            val item = player.inv[0]!!
            val state = progress.state(item)!!
            return ActiveTrail(0, item, state, progress.catalog.clues.getValue(state.row))
        }
    }
    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
