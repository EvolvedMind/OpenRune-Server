package org.rsmod.content.drops.tables.monsters

import dev.openrune.ServerCacheManager
import dtx.core.ArgMap
import dtx.core.RollResult
import java.util.ArrayDeque
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.DropTablePreview
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

@ResourceLock("ServerCacheManager")
class DoomDelveBonusRollsTest {
    @Test fun `delve-specific rates reach their caps at nine`() {
        val cases = listOf(
            1 to listOf(75),
            2 to listOf(2500, 75),
            3 to listOf(2000, 50),
            4 to listOf(1350, 50),
            5 to listOf(810, 50),
            6 to listOf(765, 1000, 50),
            7 to listOf(720, 750, 50),
            8 to listOf(630, 500, 50),
            9 to listOf(540, 250, 50),
            10 to listOf(540, 250, 50),
            1000 to listOf(540, 250, 50),
        )
        for ((delve, bounds) in cases) {
            val (items, actual) = sample(delve, bounds.map { it - 1 })
            assertEquals(bounds, actual, "delve " + delve)
            assertTrue(items.isEmpty(), "delve " + delve)
        }
    }

    @Test fun `one unique slot per kill only selects items unlocked at that delve`() {
        val all = listOf("obj.mokhaiotl_cloth", "obj.eye_of_ayak_uncharged", "obj.avernic_treads")
        for (delve in listOf(2, 3, 4, 9)) {
            val unlocked = (delve - 1).coerceAtMost(3)
            for (index in 0 until unlocked) {
                val choices = mutableListOf(index)
                if (delve >= 6) choices += DoomDelveRates.petDenominator(delve)!! - 1
                choices += DoomDelveRates.eliteClueDenominator(delve) - 1
                val (items, _) = sample(delve, choices)
                assertEquals(listOf(all[index]), items.map { it.obj }, "delve " + delve)
            }
        }
    }

    @Test fun `Dom and elite clue are independent from the exclusive unique pool`() {
        val (items, bounds) = sample(9, listOf(0, 0, 0))
        assertEquals(listOf(540, 250, 50), bounds)
        assertEquals(
            listOf("obj.mokhaiotl_cloth", "obj.dompet", "obj.trail_elite_emote_exp1"),
            items.map { it.obj },
        )
        val (none, _) = sample(1, listOf(74))
        assertTrue(none.isEmpty())
    }

    @Test fun `server boost modifies main unique and pet rates but not elite clues`() {
        val (items, bounds) = sample(9, listOf(269, 124, 49), boost = 2.0)
        assertEquals(listOf(270, 125, 50), bounds)
        assertTrue(items.isEmpty())
        val (shallow, shallowBounds) = sample(2, listOf(1249, 74), boost = 2.0)
        assertEquals(listOf(1250, 75), shallowBounds)
        assertTrue(shallow.isEmpty())
    }

    @Test fun `preview retains every rare reward without rolling rewards`() {
        val previews = DropTablePreview.entries(doomOfMokhaiotlDropTable)
        for ((obj, chance) in listOf(
            "obj.mokhaiotl_cloth" to 1.0 / 2500,
            "obj.eye_of_ayak_uncharged" to 1.0 / 2000,
            "obj.avernic_treads" to 1.0 / 1350,
            "obj.dompet" to 1.0 / 1000,
            "obj.trail_elite_emote_exp1" to 1.0 / 75,
        )) {
            val preview = previews.single { it.item?.obj == obj }
            assertEquals("Separate", preview.stage)
            assertEquals(chance, preview.baseChance, 1e-12)
        }
    }

    private fun sample(
        level: Int,
        decisions: List<Int>,
        boost: Double = 1.0,
    ): Pair<List<DropRollItem>, List<Int>> {
        val player = Player()
        VarPlayerIntMapSetter.set(player, "varp.dom_current_level_temp", level - 1)
        val queue = ArrayDeque(decisions)
        val bounds = mutableListOf<Int>()
        val rolls = DoomDelveBonusRolls(
            roll = { bound ->
                bounds += bound
                assertTrue(queue.isNotEmpty(), "no result for bound " + bound)
                val result = queue.removeFirst()
                assertTrue(result in 0 until bound, "invalid result for bound " + bound)
                result
            },
            multiplier = { _, _ -> boost },
        )
        val rewards = rolls.selectResult(player, ArgMap.Empty) as RollResult.ListOf
        assertTrue(queue.isEmpty(), "unused injected rolls")
        return rewards.results to bounds
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() }
    }
}
