package org.rsmod.content.other.treasure.trails

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.random.DefaultGameRandom

@ResourceLock("ServerCacheManager")
class TrailLootTest {
    @Test fun `all reward tables have valid positive weights counts and resolvable items`() {
        for ((name, table) in TrailLootData.tables) {
            assertTrue(table.entries.isNotEmpty(), name)
            for (entry in table.entries) {
                assertTrue(entry.weight > 0 && entry.min > 0 && entry.max >= entry.min, "$name $entry")
                assertEquals(1, listOf(entry.item != null, entry.table != null, entry.bundle.isNotEmpty()).count { it }, name)
                entry.table?.let { assertTrue(it in TrailLootData.tables, "$name -> $it") }
                (listOfNotNull(entry.item) + entry.bundle.map { it.first }).forEach {
                    assertNotNull(ServerCacheManager.getItem(it.asRSCM()), "$name: $it")
                }
            }
            table.tertiary.forEach {
                assertTrue(it.denominator > 0)
                assertNotNull(ServerCacheManager.getItem(it.item.asRSCM()))
            }
        }
    }

    @Test fun `all tiers roll reproducibly and fit persistent reward storage`() {
        for (tier in TrailTier.entries) {
            val first = TrailLoot(DefaultGameRandom(145))
            val second = TrailLoot(DefaultGameRandom(145))
            repeat(1000) {
                val result = first.roll(tier)
                assertEquals(result, second.roll(tier))
                assertTrue(result.isNotEmpty())
                assertTrue(result.all { it.count > 0 })
                val slots = result.sumOf { if (ServerCacheManager.getItem(it.id)!!.isStackable) 1 else it.count }
                assertTrue(slots <= 28, "$tier needs $slots slots")
            }
        }
    }
    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
