package org.rsmod.content.other.treasure.trails

import dev.openrune.rscm.RSCM.asRSCM

enum class TrailTier(val steps: IntRange, val rewardRolls: IntRange) {
    BEGINNER(1..3, 1..3), EASY(2..4, 2..4), MEDIUM(3..5, 3..5),
    HARD(4..6, 4..6), ELITE(5..7, 4..6), MASTER(6..8, 5..7);

    val key get() = name.lowercase()
    val box get() = "obj.league_clue_box_$key".asRSCM()
    val casket get() = "obj.trail_reward_casket_$key".asRSCM()
}
