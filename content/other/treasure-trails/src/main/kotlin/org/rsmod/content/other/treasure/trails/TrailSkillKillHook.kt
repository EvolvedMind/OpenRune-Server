package org.rsmod.content.other.treasure.trails

import dev.openrune.ServerCacheManager
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
            if (active.state.row == shadeTask && (npc.coords.level != 0 || npc.coords.x !in 3456..3519 || npc.coords.z !in 9664..9727)) continue
            if (active.state.row == spiritualTask && !representsGod(player, npc.type.id)) continue
            if (requirements.missing(player, active.clue) != null) continue
            if (progress.phase(player, active.slot, active.item, TrailSkillChallenges.COMPLETED)) {
                player.mes("You have completed Sherlock's challenge. Return to him with your clue.")
            }
        }
    }

    companion object {
        val towerTask get() = "dbrow.cluehelper_skillchallenge_master_4".asRSCM()
        val shadeTask get() = "dbrow.cluehelper_skillchallenge_master_24".asRSCM()
        val spiritualTask get() = "dbrow.cluehelper_skillchallenge_master_5".asRSCM()
        val targetsByRow by lazy {
            mapOf(
                "dbrow.cluehelper_skillchallenge_elite_10".asRSCM() to listOf("slayer_dustdevil", "kourend_dustdevil", "wild_cave_dustdevil"),
                towerTask to listOf("slayer_nechryael"),
                shadeTask to listOf("shade_level5", "shadeshadow_level5"),
                spiritualTask to listOf("godwars_spiritual_saradomin_mage", "godwars_spiritual_zamorak_mage", "godwars_spiritual_armadyl_mage", "godwars_spiritual_bandos_mage"),
                "dbrow.cluehelper_skillchallenge_master_19".asRSCM() to listOf("zeah_lizardshaman_1", "zeah_lizardshaman_2", "molch_lizardshaman_1"),
            ).mapValues { (_, names) -> names.map { "npc.$it".asRSCM() }.toSet() }
        }
        private val gods by lazy {
            mapOf("npc.godwars_spiritual_saradomin_mage".asRSCM() to "Saradomin", "npc.godwars_spiritual_zamorak_mage".asRSCM() to "Zamorak",
                "npc.godwars_spiritual_armadyl_mage".asRSCM() to "Armadyl", "npc.godwars_spiritual_bandos_mage".asRSCM() to "Bandos")
        }
        internal fun representsGod(player: org.rsmod.game.entity.Player, npc: Int): Boolean {
            val god = gods[npc] ?: return false
            return player.worn.objs.filterNotNull().any { item ->
                val type = ServerCacheManager.getItem(item.id) ?: return@any false
                // Native cache flags exist for Saradomin and Zamorak. The other affiliations use named equipment.
                val flag = when (god) { "Saradomin" -> 40; "Zamorak" -> 41; else -> -1 }
                (flag >= 0 && (type.paramsRaw?.get(flag) as? Number)?.toInt() == 1) ||
                    type.name.contains(god, ignoreCase = true) || (god == "Bandos" && type.name == "Ancient mace") ||
                    (god == "Armadyl" && type.name in setOf("Book of law", "Honourable blessing")) ||
                    (god == "Bandos" && type.name in setOf("Book of war", "War blessing")) ||
                    (god == "Saradomin" && type.name in setOf("Holy symbol", "Holy book", "Holy blessing")) ||
                    (god == "Zamorak" && type.name in setOf("Unholy symbol", "Unholy book", "Unholy blessing"))
            }
        }
    }
}
