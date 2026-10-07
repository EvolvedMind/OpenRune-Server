package org.rsmod.content.skills.smithing

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.events.skilling.SkillingActionCompleteEvent
import org.rsmod.api.player.events.skilling.SkillingActionContext
import org.rsmod.api.player.events.skilling.SkillingProductSource
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.Material
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillMultiEntry
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.content.skills.smithing.util.SmithingUtils.hasHammer
import org.rsmod.game.loc.BoundLocInfo

class ShayzienSmithingScript @Inject constructor(private val xpMods: XpModifiers) {
    suspend fun ProtectedAccess.openPlatebodies(loc: BoundLocInfo) {
            if (!hasHammer()) { mes("You need a hammer to smith armour."); return }
            val available = (1..5).filter { stat("stat.smithing") >= level(it) }.map { tier ->
                SkillMultiEntry("obj.shayzien_body_$tier", listOf(Material("obj.lovakite_bar", 4)))
            }
            if (available.isEmpty()) { mes("You need level 53 Smithing to make Shayzien platebodies."); return }
            openSkillMulti(SkillMultiConfig(verb = "smith", entries = available)) { selected ->
                val tier = selected.entry.internal.substringAfterLast('_').toInt()
                repeat(selected.amount) { if (!smith(loc, tier)) return@openSkillMulti }
            }
    }
    private suspend fun ProtectedAccess.smith(loc: BoundLocInfo, tier: Int): Boolean {
        if (!hasHammer() || stat("stat.smithing") < level(tier) || inv.count("obj.lovakite_bar") < 4) return false
        val start = coords
        if (start.level != loc.coords.level || start.chebyshevDistance(loc.coords) > 2) return false
        faceLoc(loc); anim("seq.human_smithing"); delay(3)
        if (coords != start || !hasHammer() || stat("stat.smithing") < level(tier)) return false
        val output = "obj.shayzien_body_$tier"
        if (!player.invTransaction(inv) {
            val target = select(inv); delete(target, "obj.lovakite_bar".asRSCM(), 4); add(target, output.asRSCM(), 1)
        }.success) return false
        val xp = 40.0 * xpMods.get(player, "stat.smithing")
        statAdvance("stat.smithing", xp)
        publish(SkillingActionCompleteEvent(player, SkillingActionContext.Product("stat.smithing", output, 1, xp, SkillingProductSource.Smithing)))
        return true
    }
    companion object { fun level(tier: Int) = 43 + tier * 10 }
}
