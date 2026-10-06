package org.rsmod.content.other.treasure.trails

import kotlin.random.Random
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TrailPuzzlesTest {
    @Test fun `slider shuffles preserve the solvable permutation invariant`() {
        repeat(200) { seed ->
            val board = TrailPuzzles.scramble(Random(seed))
            assertEquals((0..24).toList(), board.sorted())
            assertFalse(TrailPuzzles.solved(board))
            val pieces = board.filter { it != 24 }
            val inversions = pieces.indices.sumOf { i -> (i + 1 until pieces.size).count { pieces[i] > pieces[it] } }
            assertEquals(0, inversions % 2)
        }
        val board = IntArray(25) { it }
        assertFalse(TrailPuzzles.slide(board, 0)); assertTrue(TrailPuzzles.solved(board))
        assertTrue(TrailPuzzles.slide(board, 23)); assertTrue(TrailPuzzles.slide(board, 24))
        assertTrue(TrailPuzzles.solved(board)); assertFalse(TrailPuzzles.adjacent(4, 5))
    }

    @Test fun `light buttons are independent and every scramble has a reversible solution`() {
        repeat(100) { seed ->
            val states = (0..255).map { TrailPuzzles.lightState(seed, it) }
            assertEquals(256, states.toSet().size)
            assertEquals(0, states[0])
            for (bits in 1..255) {
                var state = states[bits]
                TrailPuzzles.lightButtons(seed).forEachIndexed { i, button -> if (bits and (1 shl i) != 0) state = state xor button }
                assertEquals(0, state)
            }
        }
    }
}
