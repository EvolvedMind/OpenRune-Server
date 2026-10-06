package org.rsmod.content.other.treasure.trails

import dev.openrune.rscm.RSCM.asRSCM
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj

internal object TrailCharlie {
    val products by lazy {
        listOf(
            "stat.cooking" to "obj.trout", "stat.cooking" to "obj.pike",
            "stat.fishing" to "obj.raw_herring", "stat.fishing" to "obj.raw_trout",
            "stat.mining" to "obj.iron_ore", "stat.smithing" to "obj.iron_dagger",
            "stat.crafting" to "obj.leather_armour", "stat.crafting" to "obj.leather_chaps",
        ).mapIndexed { index, product -> "dbrow.cluehelper_skillchallenge_beginner_$index".asRSCM() to product }.toMap()
    }
    fun handIn(progress: TrailProgress, player: Player, active: ActiveTrail): Boolean {
        val product = products[active.state.row] ?: return false
        if (active.state.phase !in setOf(TrailSkillChallenges.ASSIGNED, TrailSkillChallenges.COMPLETED) || player.inv.count(product.second) == 0) return false
        return progress.advance(player, player.inv, active.slot, active.item, listOf(InvObj(product.second)))
    }
}
