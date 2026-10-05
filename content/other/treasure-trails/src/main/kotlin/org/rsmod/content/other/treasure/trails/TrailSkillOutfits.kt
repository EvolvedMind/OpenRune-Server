package org.rsmod.content.other.treasure.trails

import dev.openrune.rscm.RSCM.asRSCM
import org.rsmod.game.entity.Player

/** Task equipment is checked during production, not again when returning to Sherlock. */
internal object TrailSkillOutfits {
    val sets by lazy {
        mapOf(
            "dbrow.cluehelper_skillchallenge_master_11".asRSCM() to outfit(
                listOf("motherlode_reward_hat", "motherlode_reward_hat_gold"),
                listOf("motherlode_reward_top", "motherlode_reward_top_gold", "varrock_armour_elite"),
                listOf("motherlode_reward_legs", "motherlode_reward_legs_gold"),
                listOf("motherlode_reward_boots", "motherlode_reward_boots_gold"),
            ),
            "dbrow.cluehelper_skillchallenge_master_20".asRSCM() to outfit(
                listOf("trawler_reward_hat", "spirit_angler_hat"),
                listOf("trawler_reward_top", "spirit_angler_top"),
                listOf("trawler_reward_legs", "spirit_angler_legs"),
                listOf("trawler_reward_boots", "spirit_angler_boots"),
            ),
            "dbrow.cluehelper_skillchallenge_master_21".asRSCM() to outfit(
                listOf("ramble_lumberjack_hat", "forestry_lumberjack_hat"),
                listOf("ramble_lumberjack_top", "forestry_lumberjack_top"),
                listOf("ramble_lumberjack_legs", "forestry_lumberjack_legs"),
                listOf("ramble_lumberjack_boots", "forestry_lumberjack_boots"),
            ),
        )
    }

    fun matches(player: Player, row: Int): Boolean = sets[row]?.all { (slot, accepted) -> player.worn[slot]?.id in accepted } ?: true

    private fun outfit(hat: List<String>, top: List<String>, legs: List<String>, boots: List<String>) =
        listOf(0 to hat, 4 to top, 7 to legs, 10 to boots).associate { (slot, names) -> slot to names.map { "obj.$it".asRSCM() }.toSet() }
}
