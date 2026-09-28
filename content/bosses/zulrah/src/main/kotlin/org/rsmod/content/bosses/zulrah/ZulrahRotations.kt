/*
 * Rotation action data adapted from PufferAI/PufferLib, MIT License.
 * Copyright (c) 2022 PufferAI. See NOTICE.md in this module.
 * Source commit: 6ffa5b10dbbbe4d1e8288367c7d9d3acd3bad4a2
 */
package org.rsmod.content.bosses.zulrah

enum class ZulrahForm {
    Ranged,
    Melee,
    Magic,
}

enum class ZulrahPosition {
    North,
    South,
    East,
    West,
}

enum class ZulrahAction {
    Attack,
    Cloud,
    Snakeling,
}

data class ZulrahPhase(
    val form: ZulrahForm,
    val position: ZulrahPosition,
    val actions: List<ZulrahAction>,
    val jadFirstRanged: Boolean? = null,
    val durationTicks: Int? = null,
)

/** Complete reference rotations; these are not inferred from the partial Alora recording. */
object ZulrahRotations {
    private val A = ZulrahAction.Attack
    private val C = ZulrahAction.Cloud
    private val S = ZulrahAction.Snakeling

    val rotation1: List<ZulrahPhase> =
        listOf(
            phase(
                ZulrahForm.Ranged,
                ZulrahPosition.North,
                28,
                C, C, C, C,
            ),
            phase(
                ZulrahForm.Melee,
                ZulrahPosition.North,
                21,
                A, A,
            ),
            phase(
                ZulrahForm.Magic,
                ZulrahPosition.North,
                18,
                A, A, A, A,
            ),
            phase(
                ZulrahForm.Ranged,
                ZulrahPosition.South,
                39,
                A, A, A, A, A, S, S, C, C, S, S,
            ),
            phase(
                ZulrahForm.Melee,
                ZulrahPosition.North,
                22,
                A, A,
            ),
            phase(
                ZulrahForm.Magic,
                ZulrahPosition.West,
                20,
                A, A, A, A, A,
            ),
            phase(
                ZulrahForm.Ranged,
                ZulrahPosition.South,
                28,
                C, C, C, S, S, S, S,
            ),
            phase(
                ZulrahForm.Magic,
                ZulrahPosition.South,
                36,
                A, A, A, A, A, S, C, S, C, S,
            ),
            phase(
                ZulrahForm.Ranged,
                ZulrahPosition.West,
                48,
                A, A, A, A, A, A, A, A, A, A, C, C, C, C,
                jadFirstRanged = true,
            ),
            phase(
                ZulrahForm.Melee,
                ZulrahPosition.North,
                21,
                A, A,
            ),
            phase(
                ZulrahForm.Ranged,
                ZulrahPosition.North,
                28,
                A, A, A, A, A, C, C, C, C,
            ),
        )

    val rotation2: List<ZulrahPhase> =
        listOf(
            phase(
                ZulrahForm.Ranged,
                ZulrahPosition.North,
                28,
                C, C, C, C,
            ),
            phase(
                ZulrahForm.Melee,
                ZulrahPosition.North,
                21,
                A, A,
            ),
            phase(
                ZulrahForm.Magic,
                ZulrahPosition.North,
                18,
                A, A, A, A,
            ),
            phase(
                ZulrahForm.Ranged,
                ZulrahPosition.West,
                28,
                C, C, C, S, S, S, S,
            ),
            phase(
                ZulrahForm.Magic,
                ZulrahPosition.South,
                39,
                A, A, A, A, A, S, S, C, C, S, S,
            ),
            phase(
                ZulrahForm.Melee,
                ZulrahPosition.North,
                21,
                A, A,
            ),
            phase(
                ZulrahForm.Ranged,
                ZulrahPosition.East,
                20,
                A, A, A, A, A,
            ),
            phase(
                ZulrahForm.Magic,
                ZulrahPosition.South,
                36,
                A, A, A, A, A, S, C, S, C, S,
            ),
            phase(
                ZulrahForm.Ranged,
                ZulrahPosition.West,
                48,
                A, A, A, A, A, A, A, A, A, A, C, C, C, C,
                jadFirstRanged = true,
            ),
            phase(
                ZulrahForm.Melee,
                ZulrahPosition.North,
                21,
                A, A,
            ),
            phase(
                ZulrahForm.Ranged,
                ZulrahPosition.North,
                28,
                A, A, A, A, A, C, C, C, C,
            ),
        )

    val rotation3: List<ZulrahPhase> =
        listOf(
            phase(
                ZulrahForm.Ranged,
                ZulrahPosition.North,
                28,
                C, C, C, C,
            ),
            phase(
                ZulrahForm.Ranged,
                ZulrahPosition.East,
                30,
                A, A, A, A, A, S, S, S,
            ),
            phase(
                ZulrahForm.Melee,
                ZulrahPosition.North,
                40,
                C, S, C, S, C, S, A, A,
            ),
            phase(
                ZulrahForm.Magic,
                ZulrahPosition.West,
                20,
                A, A, A, A, A,
            ),
            phase(
                ZulrahForm.Ranged,
                ZulrahPosition.South,
                20,
                A, A, A, A, A,
            ),
            phase(
                ZulrahForm.Magic,
                ZulrahPosition.East,
                20,
                A, A, A, A, A,
            ),
            phase(
                ZulrahForm.Ranged,
                ZulrahPosition.North,
                25,
                C, C, C, S, S, S,
            ),
            phase(
                ZulrahForm.Ranged,
                ZulrahPosition.West,
                20,
                A, A, A, A, A,
            ),
            phase(
                ZulrahForm.Magic,
                ZulrahPosition.North,
                36,
                A, A, A, A, A, C, C, S, S, S,
            ),
            phase(
                ZulrahForm.Ranged,
                ZulrahPosition.East,
                35,
                A, A, A, A, A, A, A, A, A, A,
                jadFirstRanged = false,
            ),
            phase(
                ZulrahForm.Magic,
                ZulrahPosition.North,
                18,
                S, S, S, S,
            ),
            phase(
                ZulrahForm.Ranged,
                ZulrahPosition.North,
                28,
                A, A, A, A, A, C, C, C, C,
            ),
        )

    val rotation4: List<ZulrahPhase> =
        listOf(
            phase(
                ZulrahForm.Ranged,
                ZulrahPosition.North,
                28,
                C, C, C, C,
            ),
            phase(
                ZulrahForm.Magic,
                ZulrahPosition.East,
                36,
                S, S, S, S, A, A, A, A, A, A,
            ),
            phase(
                ZulrahForm.Ranged,
                ZulrahPosition.South,
                24,
                A, A, A, A, C, C,
            ),
            phase(
                ZulrahForm.Magic,
                ZulrahPosition.West,
                30,
                S, S, S, S, A, A, A, A,
            ),
            phase(
                ZulrahForm.Melee,
                ZulrahPosition.North,
                28,
                A, A, C, C,
            ),
            phase(
                ZulrahForm.Ranged,
                ZulrahPosition.East,
                17,
                A, A, A, A,
            ),
            phase(
                ZulrahForm.Ranged,
                ZulrahPosition.South,
                34,
                S, S, S, S, S, S, C, C, C,
            ),
            phase(
                ZulrahForm.Magic,
                ZulrahPosition.West,
                33,
                A, A, A, A, A, S, S, S, S,
            ),
            phase(
                ZulrahForm.Ranged,
                ZulrahPosition.North,
                20,
                A, A, A, A,
            ),
            phase(
                ZulrahForm.Magic,
                ZulrahPosition.North,
                27,
                A, A, A, A, C, C, C,
            ),
            phase(
                ZulrahForm.Ranged,
                ZulrahPosition.East,
                29,
                A, A, A, A, A, A, A, A,
                jadFirstRanged = false,
            ),
            phase(
                ZulrahForm.Magic,
                ZulrahPosition.North,
                18,
                S, S, S, S,
            ),
            phase(
                ZulrahForm.Ranged,
                ZulrahPosition.North,
                28,
                A, A, A, A, A, C, C, C, C,
            ),
        )

    val all: List<List<ZulrahPhase>> = listOf(rotation1, rotation2, rotation3, rotation4)

    private fun phase(
        form: ZulrahForm,
        position: ZulrahPosition,
        ticks: Int,
        vararg actions: ZulrahAction,
        jadFirstRanged: Boolean? = null,
    ): ZulrahPhase = ZulrahPhase(form, position, actions.toList(), jadFirstRanged, ticks)
}
