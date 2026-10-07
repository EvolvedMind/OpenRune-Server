package org.rsmod.content.skills.agility

import org.rsmod.map.CoordGrid

/** Revision-240 obstacles, adapted from upstream PR 227 (98fe5d9a). */
data class CourseObstacle(
    val loc: String,
    val xp: Double,
    val animation: String,
    val ticks: Int,
    val slide: Boolean = false,
    val landing: (CoordGrid) -> CoordGrid,
)

enum class ClueAgilityCourse(val level: Int, val lapXp: Double, val obstacles: List<CourseObstacle>) {
    ApeAtoll(48, 300.0, listOf(
        CourseObstacle("loc.100_ilm_stepping_stone", 40.0, "seq.100_ilm_stepping_stone_jump", 2, true) { it.translate(-2, 0) },
        CourseObstacle("loc.100_ilm_climbable_tree", 40.0, "seq.100_ilm_climb_tree", 4) { CoordGrid(it.x, it.z, 2) },
        CourseObstacle("loc.100_ilm_monkeybars_start", 40.0, "seq.100_ilm_monkeybar_move", 6, true) { CoordGrid(it.x - 6, it.z, 0) },
        CourseObstacle("loc.100_ilm_cliff_climb_1", 60.0, "seq.100_ilm_climb_slope", 2, true) { it.translate(-2, 0) },
        CourseObstacle("loc.100_ilm_rope_swing", 100.0, "seq.100_ilm_vine_swing", 3, true) { it.translate(4, 0) },
        CourseObstacle("loc.100_ilm_agility_tree_base", 0.0, "seq.100_ilm_monkey_down_vine", 2, true) { it.translate(0, 5) },
    )),
    Rellekka(80, 695.0, listOf(
        CourseObstacle("loc.rooftops_rellekka_wallclimb", 20.0, "seq.human_reachforladder", 3) { CoordGrid(2626, 3676, 3) },
        CourseObstacle("loc.rooftops_rellekka_gap_1", 30.0, "seq.human_jump_hurdle", 3) { it.translate(-1, -4) },
        CourseObstacle("loc.rooftops_rellekka_tightrope_1", 40.0, "seq.human_walk_logbalance_loop", 8, true) { CoordGrid(2627, 3654, 3) },
        CourseObstacle("loc.rooftops_rellekka_gap_2", 85.0, "seq.human_into_sidestep", 8, true) { CoordGrid(2639, 3653, 3) },
        CourseObstacle("loc.rooftops_rellekka_gap_3", 25.0, "seq.human_jump_hurdle", 3) { it.translate(0, 4) },
        CourseObstacle("loc.rooftops_rellekka_tightrope_3", 105.0, "seq.human_walk_logbalance_loop", 8, true) { CoordGrid(2655, 3670, 3) },
        CourseObstacle("loc.rooftops_rellekka_dropoff", 0.0, "seq.agility_shortcut_wall_jumpdown", 3) { CoordGrid(2653, 3676, 0) },
    ));

    fun contains(coords: CoordGrid): Boolean = when (this) {
        ApeAtoll -> coords.x in 2740..2780 && coords.z in 2720..2765
        Rellekka -> coords.x in 2620..2660 && coords.z in 3645..3685
    }
}
