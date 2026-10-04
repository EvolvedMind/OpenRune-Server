package org.rsmod.content.other.treasure.trails

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.random.GameRandom
import org.rsmod.game.inv.InvObj

internal data class TrailLootEntry(val weight: Int, val min: Int, val max: Int, val item: String? = null, val table: String? = null, val bundle: List<Pair<String, Int>> = emptyList())
internal data class TrailTertiary(val denominator: Int, val item: String)
internal data class TrailLootTable(val entries: List<TrailLootEntry>, val tertiary: List<TrailTertiary> = emptyList())

@Singleton
internal class TrailLoot @Inject constructor(private val random: GameRandom) {
    fun roll(tier: TrailTier): List<InvObj> = rollTable(tier.name.lowercase().replaceFirstChar { it.uppercase() } + "Casket")
    fun rollTable(name: String): List<InvObj> {
        val totals = linkedMapOf<Int, Int>()
        fun add(symbol: String, count: Int) {
            val item = checkNotNull(ServerCacheManager.getItem(symbol.asRSCM()))
            val output = if (count > 1 && !item.isStackable && item.certlink > 0) checkNotNull(ServerCacheManager.getItem(item.certlink)) else item
            totals[output.id] = (totals[output.id] ?: 0) + count
        }
        fun visit(key: String, depth: Int) {
            check(depth < 16) { "Recursive clue loot table: $key" }
            val table = TrailLootData.tables.getValue(key)
            var roll = random.of(table.entries.sumOf { it.weight })
            val chosen = table.entries.first { entry -> roll -= entry.weight; roll < 0 }
            val count = random.of(chosen.min, chosen.max)
            when {
                chosen.item != null -> add(chosen.item, count)
                chosen.table != null -> repeat(count) { visit(chosen.table, depth + 1) }
                else -> repeat(count) { chosen.bundle.forEach { (item, qty) -> add(item, qty) } }
            }
            for (tertiary in table.tertiary) if (random.of(tertiary.denominator) == 0) add(tertiary.item, 1)
        }
        visit(name, 0)
        return totals.map { (id, count) -> InvObj(checkNotNull(ServerCacheManager.getItem(id)), count) }
    }
}
