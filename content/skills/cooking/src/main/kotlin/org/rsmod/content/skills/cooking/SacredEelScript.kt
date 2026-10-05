package org.rsmod.content.skills.cooking

import dev.openrune.rscm.RSCM.asRSCM
import org.rsmod.api.invtx.*
import org.rsmod.api.player.events.skilling.SkillingActionCompleteEvent
import org.rsmod.api.player.events.skilling.SkillingActionContext
import org.rsmod.api.player.events.skilling.SkillingProductSource
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeldU
import org.rsmod.content.skills.Material
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillMultiEntry
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SacredEelScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeldU("obj.knife", EEL) {
            if (stat("stat.cooking") < 72) {
                mes("You need level 72 Cooking to dissect sacred eels.")
                return@onOpHeldU
            }
            val entry = SkillMultiEntry(SCALES, listOf(Material(EEL)))
            openSkillMulti(SkillMultiConfig(verb = "dissect", entries = listOf(entry))) { selection ->
                repeat(selection.amount) {
                    if (!dissectSacredEel()) return@openSkillMulti
                    delay(3)
                }
            }
        }
    }

    companion object {
        const val EEL = "obj.snakeboss_eel"
        const val SCALES = "obj.snakeboss_scale"
        internal fun scaleRange(level: Int): IntRange {
            require(level >= 72)
            val minimum = 3 + ((level - 72) / 8).coerceAtMost(4)
            return minimum..minimum + 2
        }
    }
}

/** Commit the entire replacement before XP or clue credit; the knife is retained. */
internal fun ProtectedAccess.dissectSacredEel(): Boolean {
    val level = stat("stat.cooking")
    if (level < 72 || !inv.contains("obj.knife") || !inv.contains(SacredEelScript.EEL)) return false
    val range = SacredEelScript.scaleRange(level)
    val count = random.of(range.first, range.last)
    val result = player.invTransaction(inv) {
        val inventory = select(inv)
        delete(inventory, SacredEelScript.EEL.asRSCM(), 1)
        add(inventory, SacredEelScript.SCALES.asRSCM(), count)
    }
    if (!result.success) return false
    val xp = 100.0 + 3 * count
    statAdvance("stat.cooking", xp)
    publish(SkillingActionCompleteEvent(player, SkillingActionContext.Product("stat.cooking", SacredEelScript.SCALES, count, xp, SkillingProductSource.SacredEel)))
    return true
}
