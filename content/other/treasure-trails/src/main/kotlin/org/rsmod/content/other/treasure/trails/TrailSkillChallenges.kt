package org.rsmod.content.other.treasure.trails

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.player.events.skilling.SkillingActionCompleteEvent
import org.rsmod.api.player.events.skilling.SkillingActionContext
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Award hooks run after successful production, never for bank withdrawals or spawned items. */
internal class TrailSkillChallenges @Inject constructor(
    private val progress: TrailProgress,
    private val targets: TrailTargets,
    private val requirements: TrailRequirements,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<SkillingActionCompleteEvent> {
            val product = context as? SkillingActionContext.Product ?: return@onEvent
            if (product.isBonus || product.count <= 0) return@onEvent
            for (active in targets.active(player)) {
                if (active.clue.kind != "skillchallenge" || active.state.phase != ASSIGNED) continue
                val expected = products[active.state.row] ?: continue
                if (product.skill != expected.first || product.item.asRSCM() !in (alternatives[active.state.row] ?: setOf(expected.second)).map { it.asRSCM() }) continue
                if (requirements.missing(player, active.clue) != null) continue
                if (!TrailSkillOutfits.matches(player, active.state.row)) continue
                if (progress.phase(player, active.slot, active.item, COMPLETED)) player.mes("You have completed Sherlock's challenge. Return to him with your clue.")
            }
        }
    }
    companion object {
        const val ASSIGNED = 9
        const val COMPLETED = 10
        internal val alternatives by lazy {
            mapOf(
                "dbrow.cluehelper_skillchallenge_elite_6".asRSCM() to setOf("obj.3dose2defense", "obj.4dose2defense"),
                "dbrow.cluehelper_skillchallenge_master_10".asRSCM() to (1..4).map { "obj.antivenom$it" }.toSet(),
            )
        }
        internal val products by lazy {
            mapOf(
                "dbrow.cluehelper_skillchallenge_elite_6".asRSCM() to ("stat.herblore" to "obj.3dose2defense"),
                "dbrow.cluehelper_skillchallenge_master_10".asRSCM() to ("stat.herblore" to "obj.antivenom4"),
                "dbrow.cluehelper_skillchallenge_master_15".asRSCM() to ("stat.herblore" to "obj.brutal_2doserangerspotion"),
                "dbrow.cluehelper_skillchallenge_elite_8".asRSCM() to ("stat.crafting" to "obj.dragonhide_body"),
                "dbrow.cluehelper_skillchallenge_elite_13".asRSCM() to ("stat.mining" to "obj.mithril_ore"),
                "dbrow.cluehelper_skillchallenge_elite_15".asRSCM() to ("stat.fishing" to "obj.raw_shark"),
                "dbrow.cluehelper_skillchallenge_elite_16".asRSCM() to ("stat.woodcutting" to "obj.yew_logs"),
                "dbrow.cluehelper_skillchallenge_master_6".asRSCM() to ("stat.crafting" to "obj.unstrung_dragonstone_amulet"),
                "dbrow.cluehelper_skillchallenge_master_11".asRSCM() to ("stat.mining" to "obj.runite_ore"),
                "dbrow.cluehelper_skillchallenge_master_20".asRSCM() to ("stat.fishing" to "obj.raw_anglerfish"),
                "dbrow.cluehelper_skillchallenge_master_21".asRSCM() to ("stat.woodcutting" to "obj.redwood_logs"),
            )
        }
    }
}
