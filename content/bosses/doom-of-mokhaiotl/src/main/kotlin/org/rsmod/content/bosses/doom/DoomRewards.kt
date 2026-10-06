package org.rsmod.content.bosses.doom

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dtx.core.ArgMap
import dtx.core.RollResult
import dtx.core.flatten
import dtx.core.with
import jakarta.inject.Inject
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.DropTableRegistry
import org.rsmod.api.droptable.KillRollContext
import org.rsmod.api.droptable.rollCount
import org.rsmod.api.random.GameRandom
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj

/** One native roll path for real kills and administrator samples. */
internal class DoomRewards @Inject constructor(
    private val registry: DropTableRegistry,
    private val random: GameRandom,
) {
    fun roll(player: Player, npc: Npc, level: Int): List<InvObj> {
        require(level >= 1)
        val previous = player.vars[LEVEL_VARP]
        VarPlayerIntMapSetter.set(player, LEVEL_VARP, level - 1)
        try {
            val table = checkNotNull(registry.forNpc("npc.dom_boss")) { "Doom drop table is missing" }
            val result = table.roll(player, ArgMap(KillRollContext.npc with npc)).flatten()
            val drops = when (result) {
                is RollResult.Single -> listOf(result.result)
                is RollResult.ListOf -> result.results
                else -> emptyList()
            }
            val rewards = mutableListOf<InvObj>()
            fun award(drop: DropRollItem) {
                if (drop.isNothing || !drop.condition(player)) return
                val base = drop.rollCount(random)
                val count = scaledCount(base, level)
                val obj = drop.transformObj(player) ?: drop.obj
                rewards += InvObj(checkNotNull(ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))), count)
                drop.bonusDrops.forEach(::award)
            }
            drops.forEach(::award)
            val tears = guaranteedTears(level)
            if (tears > 0) rewards += InvObj("obj.demon_tear", tears)
            return rewards
        } finally {
            VarPlayerIntMapSetter.set(player, LEVEL_VARP, previous)
        }
    }
    companion object {
        const val LEVEL_VARP = "varp.dom_current_level_temp"
        val UNIQUES = setOf("obj.avernic_treads", "obj.eye_of_ayak_uncharged", "obj.mokhaiotl_cloth", "obj.dompet")
        private val MULTIPLIERS = doubleArrayOf(-0.5, -0.35, 0.0, 0.05, 0.10, 0.12, 0.14, 0.17, 0.20)
        fun scaledCount(base: Int, level: Int): Int {
            require(level >= 1 && base >= 0)
            return (base.toLong() + (base * MULTIPLIERS[minOf(level, MULTIPLIERS.size) - 1]).toLong())
                .coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
        }
        fun guaranteedTears(level: Int): Int = if (level < 3) 0 else minOf(50L + 10L * (level - 3), 100L).toInt()
    }
}
