package org.rsmod.content.other.treasure.trails

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock

@ResourceLock("ServerCacheManager")
class TrailMapsTest {
    @Test fun `named maps resolve to drawable native interfaces and real map clues`() {
        val catalog = TrailCatalog()
        assertEquals(8, TrailMaps.interfaces.size)
        for ((item, modal) in TrailMaps.interfaces) {
            val clue = catalog.clues.getValue(catalog.itemRows.getValue(item))
            assertEquals("map", clue.kind)
            val view = checkNotNull(ServerCacheManager.getInterface(modal.asRSCM()))
            assertTrue(view.components.values.any { it.type == 6 && it.model >= 0 })
            assertTrue(view.components.values.any { it.op.contains("Close") && it.onOp?.firstOrNull() == 29 })
        }
    }
    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
