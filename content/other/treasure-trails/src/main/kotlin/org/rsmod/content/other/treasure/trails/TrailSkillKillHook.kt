package org.rsmod.content.other.treasure.trails

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.player.output.mes

internal class TrailSkillKillHook @Inject constructor(
    private val progress: TrailProgress,
    private val targets: TrailTargets,
    private val requirements: TrailRequirements,
    private val areas: AreaChecker,
) : NpcDeathKillHook {
    override fun onKill(context: NpcDeathKillContext) {
        val player = context.hero
        val npc = context.npc
        for (active in targets.active(player)) {
            if (active.state.phase != TrailSkillChallenges.ASSIGNED) continue
            val eligible = targetsByRow[active.state.row] ?: continue
            if (npc.type.id !in eligible) continue
            if (active.state.row == towerTask && !areas.inArea("area.slayer_tower", npc.coords)) continue
            if (requirements.missing(player, active.clue) != null) continue
            if (progress.phase(player, active.slot, active.item, TrailSkillChallenges.COMPLETED)) {
                player.mes("You have completed Sherlock's challenge. Return to him with your clue.")
            }
        }
    }

    companion object {
        val towerTask get() = "dbrow.cluehelper_skillchallenge_master_4".asRSCM()
        val targetsByRow by lazy {
            mapOf(
                "dbrow.cluehelper_skillchallenge_elite_10".asRSCM() to listOf("slayer_dustdevil", "kourend_dustdevil", "wild_cave_dustdevil"),
                towerTask to listOf("slayer_nechryael"),
                "dbrow.cluehelper_skillchallenge_master_19".asRSCM() to listOf("zeah_lizardshaman_1", "zeah_lizardshaman_2", "molch_lizardshaman_1"),
            ).mapValues { (_, names) -> names.map { "npc.$it".asRSCM() }.toSet() }
        }
    }
}
