package org.rsmod.content.other.treasure.trails

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid

internal data class ActiveTrail(val slot: Int, val item: InvObj, val state: TrailState, val clue: TrailClue)

@Singleton
internal class TrailTargets @Inject constructor(private val progress: TrailProgress) {
    fun active(player: Player): List<ActiveTrail> = player.inv.objs.mapIndexedNotNull { slot, item ->
        item ?: return@mapIndexedNotNull null
        val state = progress.state(item) ?: return@mapIndexedNotNull null
        ActiveTrail(slot, item, state, progress.catalog.clues.getValue(state.row))
    }
    fun npc(player: Player, npc: Npc): ActiveTrail? = active(player).firstOrNull { active ->
        active.clue.targets.any { row ->
            val fields = progress.catalog.fields(row)
            fields.table == "cluehelper_target_npc" &&
                (npc.type.id in fields.ints("npc") || npc.visType.id in fields.ints("npc") || npc.visType.id in fields.ints("fallback_npc")) &&
                near(npc.coords, fields.int("coord"), 32)
        }
    }
    fun loc(player: Player, loc: BoundLocInfo): ActiveTrail? = active(player).firstOrNull { active ->
        active.clue.targets.any { row ->
            val fields = progress.catalog.fields(row)
            when (fields.table) {
                "cluehelper_target_loc" -> (loc.id in fields.ints("loc") || loc.id in fields.ints("fallback_loc")) && near(loc.coords, fields.int("coord"), 0)
                "cluehelper_target_key" -> loc.id in fields.ints("loc") && near(loc.coords, fields.int("loc_coord"), 0)
                else -> false
            }
        }
    }
    fun dig(player: Player): ActiveTrail? = active(player).firstOrNull { active ->
        active.clue.kind !in listOf("emote", "falobard", "music", "skillchallenge") && active.clue.targets.any { row ->
            val fields = progress.catalog.fields(row)
            val radius = if (active.clue.kind != "hotcold") 0 else if (active.clue.tier == TrailTier.BEGINNER) 3 else 4
            fields.table == "cluehelper_target_coord" && near(player.coords, fields.int("coord"), radius)
        }
    }
    fun near(coords: CoordGrid, packed: Int, radius: Int): Boolean {
        if (packed < 0) return false
        val target = CoordGrid(packed)
        return coords.level == target.level && coords.chebyshevDistance(target) <= radius
    }
}
