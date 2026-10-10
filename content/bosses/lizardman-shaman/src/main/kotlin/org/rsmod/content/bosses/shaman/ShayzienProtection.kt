package org.rsmod.content.bosses.shaman

import dev.openrune.rscm.RSCM.asRSCM
import org.rsmod.game.entity.Player

internal object ShayzienProtection {
    private val pieces = listOf("obj.shayzien_helm_5", "obj.shayzien_body_5", "obj.shayzien_legs_5",
        "obj.shayzien_gloves_5", "obj.shayzien_boots_5").map { it.asRSCM() }.toSet()

    fun acidDamage(player: Player, damage: Int): Int {
        val worn = player.worn.objs.filterNotNull().map { it.id }.toSet()
        val count = pieces.count { it in worn }
        return damage * (5 - count) / 5
    }
}
