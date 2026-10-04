package org.rsmod.content.other.treasure.trails

import kotlin.random.Random

object TrailPuzzles {
    fun adjacent(first: Int, second: Int): Boolean = first in 0..24 && second in 0..24 &&
        kotlin.math.abs(first / 5 - second / 5) + kotlin.math.abs(first % 5 - second % 5) == 1

    fun slide(board: IntArray, slot: Int): Boolean {
        if (board.size != 25 || slot !in board.indices) return false
        val blank = board.indexOf(24)
        if (!adjacent(slot, blank)) return false
        board[blank] = board[slot]; board[slot] = 24
        return true
    }
    fun solved(board: IntArray): Boolean = board.size == 25 && board.indices.all { board[it] == it }
    fun scramble(random: Random): IntArray {
        val board = IntArray(25) { it }
        var previous = -1
        repeat(500) {
            val blank = board.indexOf(24)
            val options = board.indices.filter { adjacent(it, blank) && it != previous }
            slide(board, options.random(random)); previous = blank
        }
        if (solved(board)) slide(board, 23)
        return board
    }
    fun lightButtons(seed: Int): IntArray {
        val random = Random(seed)
        return IntArray(8) { index ->
            (random.nextInt(1 shl 17) shl 8) or (1 shl index)
        }
    }
    fun lightState(seed: Int, switches: Int): Int {
        val buttons = lightButtons(seed)
        var state = 0
        for (i in buttons.indices) if ((switches and (1 shl i)) != 0) state = state xor buttons[i]
        return state
    }
}
