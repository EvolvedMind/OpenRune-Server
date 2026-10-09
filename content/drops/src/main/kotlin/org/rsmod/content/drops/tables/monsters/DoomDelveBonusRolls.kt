package org.rsmod.content.drops.tables.monsters

import dtx.core.ArgMap
import dtx.core.RollResult
import dtx.core.Rollable
import dtx.core.Single
import dtx.impl.chance.ChanceRollableImpl
import dtx.impl.chance.RateBoosts
import dtx.rs.RSTable
import dtx.table.TableHooks
import kotlin.math.floor
import kotlin.random.Random
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.game.entity.Player

/** OSRS per-kill odds; levels beyond 9 keep the best rates. */
internal object DoomDelveRates {
    fun uniqueDenominator(level: Int): Int? {
        require(level >= 1)
        return when (level) {
            1 -> null
            2 -> 2500
            3 -> 2000
            4 -> 1350
            5 -> 810
            6 -> 765
            7 -> 720
            8 -> 630
            else -> 540
        }
    }

    fun petDenominator(level: Int): Int? {
        require(level >= 1)
        return when (level) {
            in 1..5 -> null
            6 -> 1000
            7 -> 750
            8 -> 500
            else -> 250
        }
    }

    fun eliteClueDenominator(level: Int): Int {
        require(level >= 1)
        return if (level <= 2) 75 else 50
    }
}

/**
 * Delve bonuses use the same reward route for real Doom kills and ::testloot/::doomsim.
 * The three main uniques form one exclusive roll, while Dom and the elite clue roll independently.
 * Preview entries retain unlock-level base odds; effective odds depend on the current delve.
 */
internal class DoomDelveBonusRolls(
    private val roll: (Int) -> Int = { Random.nextInt(it) },
    private val multiplier: (Player, ArgMap) -> Double = { player, args ->
        RateBoosts.multiplierFor(player, args)
    },
) : RSTable<Player, DropRollItem>, TableHooks<Player, DropRollItem> by TableHooks.Default() {
    override val tableIdentifier: String = "Doom of Mokhaiotl delve bonuses"

    private val cloth = DropRollItem("obj.mokhaiotl_cloth", 1)
    private val eye = DropRollItem("obj.eye_of_ayak_uncharged", 1)
    private val treads = DropRollItem("obj.avernic_treads", 1)
    private val pet = DropRollItem("obj.dompet", 1)
    private val clue = DropRollItem("obj.trail_elite_emote_exp1", 1)
    private val mainUniques = listOf(cloth, eye, treads)

    // Static metadata for the existing drop-table preview. Gameplay uses selectResult below.
    override val tableEntries: Collection<Rollable<Player, DropRollItem>> =
        listOf(
            ChanceRollableImpl(100.0 / 2500, Single(cloth)),
            ChanceRollableImpl(100.0 / 2000, Single(eye)),
            ChanceRollableImpl(100.0 / 1350, Single(treads)),
            ChanceRollableImpl(100.0 / 1000, Single(pet)),
            ChanceRollableImpl(100.0 / 75, Single(clue)),
        )

    override fun selectResult(target: Player, otherArgs: ArgMap): RollResult<DropRollItem> {
        val delve = target.vars["varp.dom_current_level_temp"] + 1
        val boost = multiplier(target, otherArgs).coerceAtLeast(0.0001)
        val results = mutableListOf<DropRollItem>()
        val available = mainUniques.take((delve - 1).coerceIn(0, mainUniques.size))

        DoomDelveRates.uniqueDenominator(delve)?.let { base ->
            // One shared roll gives each unlocked unique exactly 1/denominator.
            val bound = boostedDenominator(base, boost).coerceAtLeast(available.size)
            val choice = roll(bound)
            if (choice in available.indices) results += available[choice]
        }
        DoomDelveRates.petDenominator(delve)?.let { base ->
            if (roll(boostedDenominator(base, boost)) == 0) results += pet
        }
        // Elite clue odds are not affected by the generic unique drop-rate multiplier.
        if (roll(DoomDelveRates.eliteClueDenominator(delve)) == 0) results += clue

        return RollResult.ListOf(results)
    }

    private fun boostedDenominator(base: Int, boost: Double): Int =
        floor(base / boost).toInt().coerceAtLeast(1)
}
