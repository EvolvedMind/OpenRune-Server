package org.rsmod.content.other.treasure.trails

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InvStackType
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
class TrailRewardsTest {
    @Test fun `full inventory leaves durable rewards and cannot reroll the same casket`() {
        val f = Fixture()
        for (slot in 1..27) f.player.inv[slot] = InvObj("obj.abyssal_whip")
        val original = InvObj("obj.trail_reward_casket_master")
        f.player.inv[0] = original
        assertNotNull(f.rewards.open(f.player, 0, original, TrailTier.MASTER))
        assertNull(f.rewards.open(f.player, 0, original, TrailTier.MASTER))
        val before = totals(f.player.inv, f.pending)
        f.rewards.claim(f.player)
        assertEquals(before, totals(f.player.inv, f.pending))
        assertTrue(f.pending.objs.any { it != null })
        val next = InvObj("obj.trail_reward_casket_master")
        f.player.inv[27] = next
        assertNull(f.rewards.open(f.player, 27, next, TrailTier.MASTER))
        assertSame(next, f.player.inv[27])
    }

    @Test fun `reloaded escrow can be claimed once and does not clear Barrows display`() {
        val first = Fixture()
        first.player.inv[0] = InvObj("obj.trail_reward_casket_hard")
        first.rewards.open(first.player, 0, first.player.inv[0]!!, TrailTier.HARD)
        val saved = first.pending.objs.map { it?.copy() }.toTypedArray()
        val second = Fixture()
        saved.forEachIndexed { slot, item -> second.pending[slot] = item }
        val expected = totals(second.pending)
        val display = second.player.invMap.getOrPut("inv.trail_rewardinv")
        display[0] = InvObj("obj.coins", 12345)
        second.rewards.claim(second.player)
        second.rewards.claim(second.player)
        assertEquals(expected, totals(second.player.inv))
        assertTrue(second.pending.objs.all { it == null })
        assertEquals(12345, display[0]!!.count)
    }
    private fun totals(vararg inventories: Inventory) = inventories.flatMap { it.objs.filterNotNull() }
        .groupBy { it.id }.mapValues { (_, items) -> items.sumOf { it.count } }
    private class Fixture {
        val player = Player().apply { inv = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())), arrayOfNulls(28)) }
        val pending = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.trail_rewardinv".asRSCM())).copy(size = 28, stack = InvStackType.Normal), arrayOfNulls(28))
        val rewards = TrailRewards(TrailLoot(DefaultGameRandom(47)))
        init {
            player.invMap["inv.trail_pending_rewards"] = pending
            val context = ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache())
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { context.startup() }
        }
    }
    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
