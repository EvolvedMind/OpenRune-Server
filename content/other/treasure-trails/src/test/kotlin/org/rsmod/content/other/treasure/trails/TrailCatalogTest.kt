package org.rsmod.content.other.treasure.trails

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock

@ResourceLock("ServerCacheManager")
class TrailCatalogTest {
    @Test fun `cache clues cover all tiers and resolve their targets`() {
        val catalog = TrailCatalog()
        assertEquals(997, catalog.clues.size)
        for (tier in TrailTier.entries) {
            val clues = catalog.forTier(tier)
            assertTrue(clues.isNotEmpty(), tier.name)
            for (clue in clues) {
                assertNotNull(ServerCacheManager.getItem(catalog.item(clue)), "${clue.row}")
                assertTrue(clue.targets.isNotEmpty() || clue.text == "Combine the torn clue scroll parts.", "${clue.row}")
                clue.targets.forEach { assertNotNull(ServerCacheManager.getDbrow(it), "${clue.row}:$it") }
            }
        }
    }

    @Test fun `item state survives encoding and rejects corrupt values`() {
        for (total in 1..8) for (done in 0 until total) for (phase in 0..15) {
            val state = TrailState(65535, total, done, phase)
            assertEquals(state, TrailState.decode(state.encode()))
        }
        for (bits in listOf(0, -1, 65535, 0x11110001)) assertNull(TrailState.decode(bits))
    }
    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
