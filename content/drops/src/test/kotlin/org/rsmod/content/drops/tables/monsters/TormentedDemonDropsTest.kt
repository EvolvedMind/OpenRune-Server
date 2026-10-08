package org.rsmod.content.drops.tables.monsters

import dev.openrune.ServerCacheManager
import dtx.core.ArgMap
import dtx.core.RollResult
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.game.entity.Player

@ResourceLock("ServerCacheManager")
class TormentedDemonDropsTest {
    @Test fun `synapse win skips claw smouldering and ordinary rolls`() {
        val bounds = mutableListOf<Int>()
        val result = TormentedPrimaryDrops { bounds += it; 0 }.selectResult(Player(), ArgMap())
        assertEquals("obj.tormented_synapse", (result as RollResult.Single).result.obj)
        assertEquals(listOf(500), bounds)
    }

    @Test fun `claw rolls only after a failed synapse`() {
        val rolls = ArrayDeque(listOf(1, 0))
        val bounds = mutableListOf<Int>()
        val result = TormentedPrimaryDrops { bounds += it; rolls.removeFirst() }.selectResult(Player(), ArgMap())
        assertEquals("obj.bone_claw", (result as RollResult.Single).result.obj)
        assertEquals(listOf(500, 500), bounds)
    }

    @Test fun `all eleven smouldering outcomes select one primary item`() {
        for (value in 0..10) {
            val rolls = ArrayDeque(listOf(1, 1, value))
            val result = TormentedPrimaryDrops { rolls.removeFirst() }.selectResult(Player(), ArgMap()) as RollResult.Single
            assertEquals(when { value < 5 -> "obj.smouldering_gland"; value < 10 -> "obj.smouldering_pile_of_flesh"; else -> "obj.smouldering_heart" }, result.result.obj)
            assertEquals(1..1, result.result.count)
            assertTrue(rolls.isEmpty())
        }
    }

    @Test fun `every ordinary selection returns loot without a nothing placeholder`() {
        repeat(1000) {
            val rolls = ArrayDeque(listOf(1, 1, 124))
            val result = TormentedPrimaryDrops { rolls.removeFirst() }.selectResult(Player(), ArgMap())
            assertTrue(result is RollResult.Single)
        }
    }

    @Test fun `complete table always gives ashes alongside exactly one primary`() {
        repeat(200) {
            val results = (tormentedDemonDropTable.roll(Player(), ArgMap()) as RollResult.ListOf).results
            assertEquals(1, results.count { it.obj == "obj.infernal_ashes" && it.count == 1..1 })
            assertEquals(1, results.count { it.obj != "obj.infernal_ashes" && it.obj != "obj.teleportscroll_guthixian_temple" && !it.obj.contains("clue") && !it.obj.contains("box") })
        }
    }

    @Test fun `drop interface previews ordered chances without executing rewards`() {
        val preview = org.rsmod.api.droptable.DropTablePreview.entries(tormentedDemonDropTable)
        assertEquals(1.0 / 500, preview.single { it.item?.obj == "obj.tormented_synapse" }.baseChance, 1e-10)
        assertEquals(499.0 / 250000, preview.single { it.item?.obj == "obj.bone_claw" }.baseChance, 1e-10)
        assertEquals(0.996004 / 125, preview.single { it.item?.obj == "obj.smouldering_heart" }.baseChance, 1e-10)
        assertEquals(1.0, preview.single { it.item?.obj == "obj.infernal_ashes" }.baseChance)
    }
    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
