package org.rsmod.content.other.treasure.trails

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.DBRowType
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Singleton

@Singleton
class TrailCatalog {
    val clues: Map<Int, TrailClue> by lazy {
        kinds.flatMap { kind ->
            ServerCacheManager.getRowsForTable("dbtable.cluehelper_clue_$kind".asRSCM()).mapNotNull { row ->
                val fields = TrailFields(row)
                val difficulty = fields.int("difficulty")
                if (difficulty !in TrailTier.entries.indices) null else
                    TrailClue(row.id, kind, TrailTier.entries[difficulty], fields)
            }
        }.associateBy { it.row }
    }
    val itemRows: Map<Int, Int> by lazy {
        ServerCacheManager.getItemTypes().mapNotNull { item ->
            val row = (item.paramsRaw?.get("param.trail_cluehelper_row".asRSCM()) as? Number)?.toInt()
            if (item.name.startsWith("Clue scroll (") && clues.containsKey(row)) item.id to row!! else null
        }.toMap()
    }
    val rowItems: Map<Int, Int> by lazy { itemRows.entries.associate { it.value to it.key } }
    fun item(clue: TrailClue): Int = when (clue.tier) {
        TrailTier.BEGINNER -> "obj.trail_clue_beginner".asRSCM()
        TrailTier.MASTER -> "obj.trail_clue_master".asRSCM()
        else -> checkNotNull(rowItems[if (clue.tier == TrailTier.ELITE && clue.kind == "skillchallenge") sherlockIntro else clue.row]) { "No scroll item for clue ${clue.row}" }
    }
    fun forTier(tier: TrailTier) = clues.values.filter { it.tier == tier && (tier == TrailTier.BEGINNER || tier == TrailTier.MASTER || rowItems.containsKey(it.row)) }
    fun fields(row: Int) = TrailFields(checkNotNull(ServerCacheManager.getDbrow(row)))
    companion object {
        val sherlockIntro get() = "dbrow.cluehelper_cryptic_elite_sherlock".asRSCM()
        val kinds = listOf("anagram", "map", "cipher", "coordinate", "cryptic", "emote", "fairyring", "falobard", "hotcold", "music", "skillchallenge")
    }
}

data class TrailClue(val row: Int, val kind: String, val tier: TrailTier, val fields: TrailFields) {
    val targets get() = fields.ints("target")
    val requirements get() = fields.ints("requirements")
    val text get() = fields.string("clue_text")
}

class TrailFields(private val row: DBRowType) {
    val table: String get() = RSCM.getReverseMapping(RSCMType.DBTABLE, row.tableId).removePrefix("dbtable.")
    fun values(name: String): List<Any> {
        val key = "dbcol.$table:$name"
        val column = runCatching { key.asRSCM() and 65535 }.getOrNull() ?: return emptyList()
        val explicit = row.columnTypes?.getOrNull(column)
        return explicit?.filterNotNull() ?: emptyList()
    }
    fun ints(name: String): List<Int> = values(name).filterIsInstance<Number>().map { it.toInt() }
    fun int(name: String): Int = ints(name).firstOrNull() ?: -1
    fun string(name: String): String = values(name).filterIsInstance<String>().firstOrNull().orEmpty()
}
