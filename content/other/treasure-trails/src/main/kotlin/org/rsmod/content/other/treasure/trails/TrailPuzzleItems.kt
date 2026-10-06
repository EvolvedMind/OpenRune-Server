package org.rsmod.content.other.treasure.trails

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.invtx.*
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj

internal object TrailPuzzleItems {
    val types by lazy {
        ServerCacheManager.getItemTypes().filter {
            (it.name.startsWith("Puzzle box (") && RSCM.getReverseMapping(RSCMType.OBJ, it.id).startsWith("obj.trail_")) ||
                it.id == "obj.light_puzzle_box".asRSCM()
        }
    }
    fun owner(state: TrailState): Int = state.copy(phase = 0).encode()
    fun owned(player: Player, state: TrailState): InvObj? = player.inv.objs.filterNotNull().firstOrNull {
        it.vars == owner(state) && types.any { type -> type.id == it.id }
    }
    fun give(player: Player, active: ActiveTrail, light: Boolean): Boolean {
        if (player.inv[active.slot] !== active.item) return false
        val box = owned(player, active.state)
        if (box != null && active.state.phase in listOf(1, 2, 6)) return true
        val phase = if (light) 6 else 1
        val item = boxType(active, light)
        return player.invTransaction(player.inv) {
            val inv = select(player.inv)
            delete(inv, active.item.id, 1, active.slot)
            add(inv, active.item.id, 1, active.state.copy(phase = phase).encode(), active.slot)
            if (box == null) add(inv, item, 1, owner(active.state))
        }.success
    }
    private fun boxType(active: ActiveTrail, light: Boolean): Int {
        if (light) return "obj.light_puzzle_box".asRSCM()
        if (active.clue.tier == TrailTier.MASTER) {
            val image = listOf("zulrah", "cerberus", "gnomechild")[active.state.row % 3]
            return "obj.trail_master_${image}_puzzlebox".asRSCM()
        }
        val symbol = RSCM.getReverseMapping(RSCMType.OBJ, active.item.id) + "_puzzlebox"
        return runCatching { symbol.asRSCM() }.getOrNull()?.takeIf { id -> types.any { it.id == id } }
            ?: if (active.clue.tier == TrailTier.HARD) "obj.trail_clue_hard_riddle014_puzzlebox".asRSCM()
            else "obj.trail_elite_riddle_puzzlebox".asRSCM()
    }
}
