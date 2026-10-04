package org.rsmod.content.other.treasure.trails

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.invtx.*
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

/** The persistent escrow owns rewards; the native reward inventory is only a UI projection. */
@Singleton
internal class TrailRewards @Inject constructor(private val loot: TrailLoot) {
    fun pending(player: Player): Inventory = player.invMap.getOrPut("inv.trail_pending_rewards")

    fun open(player: Player, slot: Int, original: InvObj, tier: TrailTier): List<InvObj>? {
        if (player.inv[slot] !== original || original.id != tier.casket) return null
        val pending = pending(player)
        if (pending.objs.any { it != null }) return null
        val rewards = loot.roll(tier)
        val result = player.invTransaction(player.inv, pending) {
            val from = select(player.inv)
            val into = select(pending)
            delete(from, original.id, 1, slot)
            for (reward in rewards) add(into, reward.id, reward.count, reward.vars)
        }
        return if (result.success) rewards else null
    }

    fun claim(player: Player) {
        val pending = pending(player)
        for (slot in pending.indices) {
            val item = pending[slot] ?: continue
            player.invTransfer(from = pending, into = player.inv, fromSlot = slot, count = item.count, strict = false)
        }
    }

    fun display(player: Player): Inventory {
        val display = player.invMap.getOrPut("inv.trail_rewardinv")
        val pending = pending(player)
        for (slot in display.indices) display[slot] = pending.objs.getOrNull(slot)
        return display
    }
}
