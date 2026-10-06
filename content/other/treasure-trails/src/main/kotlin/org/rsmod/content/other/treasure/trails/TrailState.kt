package org.rsmod.content.other.treasure.trails

/** Item-local progress travels through inventory, bank, ground items and persistence. */
data class TrailState(val row: Int, val total: Int, val completed: Int = 0, val phase: Int = 0) {
    init {
        require(row in 1..65535)
        require(total in 1..8)
        require(completed in 0 until total)
        require(phase in 0..15)
    }
    fun encode(): Int = row or (total shl 16) or (completed shl 20) or (phase shl 24)
    companion object {
        fun decode(bits: Int): TrailState? {
            val row = bits and 65535
            val total = (bits ushr 16) and 15
            val completed = (bits ushr 20) and 15
            val phase = (bits ushr 24) and 15
            if (bits ushr 28 != 0 || row == 0 || total !in 1..8 || completed >= total) return null
            return TrailState(row, total, completed, phase)
        }
    }
}
