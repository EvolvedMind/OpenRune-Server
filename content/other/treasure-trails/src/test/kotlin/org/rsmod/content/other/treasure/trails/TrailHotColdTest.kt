package org.rsmod.content.other.treasure.trails

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TrailHotColdTest {
    @Test fun `beginner and master shaking radii are distinct`() {
        assertEquals("visibly shaking", TrailHotColdScript.temperature(3, TrailTier.BEGINNER))
        assertEquals("incredibly hot", TrailHotColdScript.temperature(4, TrailTier.BEGINNER))
        assertEquals("visibly shaking", TrailHotColdScript.temperature(4, TrailTier.MASTER))
        assertEquals("incredibly hot", TrailHotColdScript.temperature(5, TrailTier.MASTER))
    }

    @Test fun `all temperature boundaries classify both sides correctly`() {
        val boundaries = listOf(30 to "very hot", 70 to "hot", 100 to "warm", 150 to "cold", 200 to "very cold", 500 to "ice cold")
        var previous = "incredibly hot"
        for ((distance, label) in boundaries) {
            for (tier in listOf(TrailTier.BEGINNER, TrailTier.MASTER)) {
                assertEquals(previous, TrailHotColdScript.temperature(distance - 1, tier))
                assertEquals(label, TrailHotColdScript.temperature(distance, tier))
            }
            previous = label
        }
    }
}
