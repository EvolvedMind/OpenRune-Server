package org.rsmod.content.drops.tables.monsters

import dtx.core.ArgMap
import dtx.core.RollResult
import dtx.core.Rollable
import dtx.impl.chance.ChanceRollableImpl
import dtx.impl.chance.RateBoosts
import dtx.rs.*
import dtx.table.TableHooks
import kotlin.random.Random
import org.rsmod.api.droptable.*
import org.rsmod.content.drops.clueScrollTransformObj
import org.rsmod.content.drops.tables.shared.SharedDropTables
import org.rsmod.game.entity.Player

private val treeHerbSeeds = rsPlayerWeightedTable(total = 250) {
    name("Tree-herb seed drop table")
    30 weight "obj.ranarr_seed" count 1
    28 weight "obj.snapdragon_seed" count 1
    22 weight "obj.torstol_seed" count 1
    21 weight "obj.watermelon_seed" count 15
    20 weight "obj.willow_seed" count 1
    18 weight "obj.mahogany_seed" count 1
    18 weight "obj.maple_seed" count 1
    18 weight "obj.teak_seed" count 1
    18 weight "obj.yew_seed" count 1
    14 weight "obj.papaya_tree_seed" count 1
    11 weight "obj.magic_tree_seed" count 1
    10 weight "obj.palm_tree_seed" count 1
    8 weight "obj.spirit_tree_seed" count 1
    6 weight "obj.dragonfruit_tree_seed" count 1
    4 weight "obj.celastrus_tree_seed" count 1
    4 weight "obj.redwood_tree_seed" count 1
}

@field:RegisterDropTable
@JvmField
public val tormentedDemonDropTable: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Tormented Demon Drops",
    npcs = npcs("npc.tormented_demon_1", "npc.tormented_demon_2", "npc.tormented_demon_quest_1", "npc.tormented_demon_quest_2"),
    guaranteed = rsPlayerGuaranteedTable { "obj.infernal_ashes" count 1 },
    mainTable = TormentedPrimaryDrops(),
    separateRolls = rsPlayerPrerollTable { 1 outOf 12 weight "obj.teleportscroll_guthixian_temple" count 2 },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 128 weight "obj.trail_clue_elite_combat001" count 1 transformObj { p -> p.clueScrollTransformObj("obj.trail_clue_elite_combat001") }
    },
)

/** A successful rare roll replaces the ordinary drop, rather than adding another reward. */
internal class TormentedPrimaryDrops(private val roll: (Int) -> Int = { Random.nextInt(it) }) :
    RSTable<Player, DropRollItem>, TableHooks<Player, DropRollItem> by TableHooks.Default() {
    override val tableIdentifier = "Tormented Demon ordered primary drops"
    private val synapse = DropRollItem("obj.tormented_synapse", 1)
    private val claw = DropRollItem("obj.bone_claw", 1)
    private val gland = DropRollItem("obj.smouldering_gland", 1)
    private val flesh = DropRollItem("obj.smouldering_pile_of_flesh", 1)
    private val heart = DropRollItem("obj.smouldering_heart", 1)

    // These weights are conditional on all rare rolls failing. Their observed overall
    // denominators are approximately 51 after the rare/smouldering selection above.
    private val ordinary = rsPlayerWeightedTable(total = 46) {
        name("Tormented Demon ordinary drops")
        3 weight "obj.dragon_dagger" count 1
        2 weight "obj.rune_kiteshield" count 1
        3 weight "obj.cert_battlestaff" count 1
        4 weight "obj.rune_platebody" count 1
        4 weight "obj.chaosrune" count 25..100
        2 weight "obj.soulrune" count 50..75
        4 weight "obj.rune_arrow" count 65..125
        4 weight "obj.mantaray" count 1..2
        1 weight "obj.4doseprayerrestore" count 1
        1 weight "obj.2doseprayerrestore" count 2
        2 weight "obj.malicious_ashes" count 2..3
        2 weight "obj.cert_fire_orb" count 5..7
        1 weight "obj.dragon_arrowheads" count 30..40
        4 weight SharedDropTables.combatHerb
        2 weight SharedDropTables.usefulHerb
        1 weight treeHerbSeeds
        6 weight rsPlayerWeightedTable(total = 30) {
            29 weight "obj.cert_unstrung_magic_shortbow" count 1
            1 weight "obj.unstrung_magic_longbow" count 1
        }
    }
    override val tableEntries: Collection<Rollable<Player, DropRollItem>> =
        listOf(
            ChanceRollableImpl(0.2, dropRollable(synapse)),
            ChanceRollableImpl(0.1996, dropRollable(claw)),
            ChanceRollableImpl(0.996004 * 4.0, dropRollable(gland)),
            ChanceRollableImpl(0.996004 * 4.0, dropRollable(flesh)),
            ChanceRollableImpl(0.996004 * 0.8, dropRollable(heart)),
            ChanceRollableImpl(0.996004 * 91.2, ordinary),
        )

    override fun selectResult(target: Player, otherArgs: ArgMap): RollResult<DropRollItem> {
        val boost = RateBoosts.multiplierFor(target, otherArgs).coerceAtLeast(1.0)
        val uniqueDenominator = (500 / boost).toInt().coerceAtLeast(1)
        if (roll(uniqueDenominator) == 0) return RollResult.Single(synapse)
        if (roll(uniqueDenominator) == 0) return RollResult.Single(claw)
        val smouldering = roll(125)
        if (smouldering < 5) return RollResult.Single(gland)
        if (smouldering < 10) return RollResult.Single(flesh)
        if (smouldering == 10) return RollResult.Single(heart)
        return ordinary.roll(target, otherArgs)
    }
}
