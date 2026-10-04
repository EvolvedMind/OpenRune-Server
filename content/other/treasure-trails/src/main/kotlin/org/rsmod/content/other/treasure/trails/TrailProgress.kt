package org.rsmod.content.other.treasure.trails

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.invtx.*
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

@Singleton
class TrailProgress @Inject constructor(val catalog: TrailCatalog, private val random: GameRandom) {
    fun choose(tier: TrailTier): TrailClue {
        val groups = catalog.forTier(tier).groupBy { it.kind }.values.toList()
        val group = groups[random.of(groups.size)]
        return group[random.of(group.size)]
    }
    fun openBox(player: Player, inventory: Inventory, slot: Int, tier: TrailTier): Boolean {
        val original = inventory[slot] ?: return false
        if (original.id != tier.box) return false
        val clue = choose(tier)
        val state = TrailState(clue.row, random.of(tier.steps))
        return replace(player, inventory, slot, original, InvObj(checkNotNull(ServerCacheManager.getItem(catalog.item(clue))), 1, state.encode()))
    }
    fun state(item: InvObj): TrailState? {
        val state = TrailState.decode(item.vars)
        if (state != null && catalog.clues.containsKey(state.row) && catalog.item(catalog.clues.getValue(state.row)) == item.id) return state
        return null
    }
    fun initialize(player: Player, inventory: Inventory, slot: Int): TrailState? {
        val original = inventory[slot] ?: return null
        state(original)?.let { return it }
        val clue = catalog.itemRows[original.id]?.let { catalog.clues[it] } ?: when (original.id) {
            "obj.trail_clue_beginner".asRSCM() -> choose(TrailTier.BEGINNER)
            "obj.trail_clue_master".asRSCM() -> choose(TrailTier.MASTER)
            else -> return null
        }
        val state = TrailState(clue.row, random.of(clue.tier.steps))
        return if (replace(player, inventory, slot, original, original.copy(count = 1, vars = state.encode()))) state else null
    }
    fun advance(player: Player, inventory: Inventory, slot: Int, original: InvObj, consume: List<InvObj> = emptyList()): Boolean {
        val state = state(original) ?: return false
        val clue = catalog.clues.getValue(state.row)
        val output = if (state.completed + 1 == state.total) InvObj(checkNotNull(ServerCacheManager.getItem(clue.tier.casket)), 1) else {
            val next = choose(clue.tier)
            InvObj(checkNotNull(ServerCacheManager.getItem(catalog.item(next))), 1, TrailState(next.row, state.total, state.completed + 1).encode())
        }
        return replace(player, inventory, slot, original, output, consume)
    }
    fun phase(player: Player, slot: Int, original: InvObj, phase: Int): Boolean {
        val state = state(original) ?: return false
        return replace(player, player.inv, slot, original, original.copy(count = 1, vars = state.copy(phase = phase).encode()))
    }
    fun replace(player: Player, inventory: Inventory, slot: Int, original: InvObj, output: InvObj, consume: List<InvObj> = emptyList()): Boolean {
        if (inventory[slot] !== original) return false
        val exactSlots = consume.filter { it.vars != 0 }.associateWith { item -> inventory.objs.indexOfFirst { it === item } }
        if (exactSlots.values.any { it < 0 }) return false
        return player.invTransaction(inventory) {
            val target = select(inventory)
            delete(target, original.id, 1, slot)
            for (item in consume) delete(target, item.id, item.count, exactSlots[item])
            add(target, output.id, output.count, output.vars, if (original.count == 1) slot else null)
        }.success
    }
}
