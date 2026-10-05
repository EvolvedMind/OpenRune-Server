package org.rsmod.content.other.treasure.trails

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.stat.stat
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.entity.Player

@Singleton
internal class TrailRequirements @Inject constructor(private val catalog: TrailCatalog) {
    fun missing(player: Player, clue: TrailClue): String? {
        if (clue.tier == TrailTier.MASTER && (clue.kind == "hotcold" || clue.row in TrailCatalog.hotColdIntros) &&
            !QuestRequirements.hasCompleted(player, "quest_makinghistory")) return "Complete Making History before starting this search."
        for (row in clue.requirements) {
            val f = catalog.fields(row)
            when (f.table) {
                "cluehelper_requirement_quest" -> {
                    val key = RSCM.getReverseMapping(RSCMType.DBROW, f.int("quest")).removePrefix("dbrow.")
                    if (!QuestRequirements.hasCompleted(player, key) &&
                        (Quest.get(key)?.getQuestStage(player) ?: 0) < f.int("varstate")) return f.string("description")
                }
                "cluehelper_requirement_stat" -> {
                    val stat = RSCM.getReverseMapping(RSCMType.STAT, f.int("stat"))
                    if (player.stat(stat) < f.int("level")) return "You need level ${f.int("level")} ${stat.removePrefix("stat.")} for this clue."
                }
                "cluehelper_requirement_obj" -> {
                    val count = f.int("count").coerceAtLeast(1)
                    val items = if (f.int("inv") == "inv.worn".asRSCM()) player.worn.objs else player.inv.objs
                    if (items.filterNotNull().filter { it.id in f.ints("item") }.sumOf { it.count } < count) return "You need ${f.string("description")} for this clue."
                }
                "cluehelper_requirement_obj_param_trail_item" -> {
                    val group = f.int("item_group")
                    if ((player.inv.objs + player.worn.objs).filterNotNull().none { group(it.id) == group }) return "You need ${f.string("description")} for this clue."
                }
            }
        }
        for (row in clue.fields.ints("outfit")) {
            val outfit = catalog.fields(row)
            for ((name, slot) in slots) {
                val ids = outfit.ints("wearpos_$name")
                val groups = outfit.ints("wearpos_param_$name")
                val item = player.worn[slot]
                if ((ids.isNotEmpty() || groups.isNotEmpty()) && (item?.id ?: -1) !in ids && (item == null || group(item.id) !in groups)) return outfit.string("description")
            }
            val any = outfit.ints("wearpos_param_any")
            if (any.isNotEmpty() && player.worn.objs.filterNotNull().none { group(it.id) in any }) return outfit.string("description")
        }
        return null
    }
    private fun group(item: Int) = (ServerCacheManager.getItem(item)?.paramsRaw?.get("param.trail_equipment_group".asRSCM()) as? Number)?.toInt()
    private val slots = mapOf("hat" to 0, "back" to 1, "front" to 2, "rhand" to 3, "torso" to 4, "lhand" to 5, "legs" to 7, "hands" to 9, "feet" to 10, "ring" to 12, "quiver" to 13)
}
