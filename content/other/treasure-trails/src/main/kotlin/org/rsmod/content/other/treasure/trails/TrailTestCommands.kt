package org.rsmod.content.other.treasure.trails

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.invtx.*
import org.rsmod.api.player.output.mes
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onCommand
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Admin-only fixtures: inventory additions are atomic and never equip or replace existing items. */
internal class TrailTestCommands @Inject constructor(
    private val progress: TrailProgress,
    private val random: GameRandom,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("cluekit") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Add spade, coordinate tools and basic clue equipment to inventory"
            cheat { if (args.isEmpty()) give(player, kit.map { InvObj(it) }) else help(player) }
        }
        onCommand("cluetest") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Clue test items: kit, box, scroll, casket, task, eel, gem, elf, watermelon, info"
            cheat { run(player, args) }
        }
    }

    private fun run(player: Player, args: List<String>) {
        val action = args.firstOrNull()?.lowercase()
        if (action in actionFixtures) {
            if (args.size != 1) return help(player)
            val fixture = actionFixtures.getValue(action!!)
            val clue = progress.catalog.clues.getValue(fixture.row.asRSCM())
            if (giveClue(player, clue, if (clue.row == TrailTornParts.ROW) 0 else TrailSkillChallenges.ASSIGNED, 1, fixture.items.map { (symbol, count) -> InvObj(symbol, count) })) player.mes(fixture.message)
            return
        }
        when (action) {
            "kit" -> if (args.size == 1) give(player, kit.map { InvObj(it) }) else help(player)
            "info" -> if (args.size == 1) info(player) else help(player)
            "box", "casket" -> {
                val tier = tier(args.getOrNull(1) ?: "beginner")
                val count = if (args.size <= 2) 1 else args.getOrNull(2)?.toIntOrNull()
                if (args.size !in 1..3 || tier == null || count == null || count !in 1..28) return help(player)
                give(player, listOf(InvObj(checkNotNull(ServerCacheManager.getItem(if (action == "box") tier.box else tier.casket)), count)))
            }
            "scroll" -> {
                val tier = tier(args.getOrNull(1) ?: "beginner")
                if (args.size !in 1..3 || tier == null) return help(player)
                val kind = args.getOrNull(2)?.lowercase()
                val candidates = progress.catalog.forTier(tier).filter { kind == null || it.kind == kind }
                if (candidates.isEmpty()) { player.mes("No starting clues of that kind for ${tier.key}."); return }
                val clue = if (kind == null) progress.choose(tier) else candidates[random.of(candidates.size)]
                giveClue(player, clue, phase = 0, steps = random.of(tier.steps))
            }
            "task" -> {
                val tier = tier(args.getOrNull(1))
                val index = args.getOrNull(2)?.lowercase()
                if (args.size != 3 || tier == null || index == null || !index.matches(Regex("[a-z0-9_]+"))) return help(player)
                val row = runCatching { "dbrow.cluehelper_skillchallenge_${tier.key}_$index".asRSCM() }.getOrNull()
                val clue = row?.let { progress.catalog.clues[it] }
                if (clue == null || clue.kind != "skillchallenge" || clue.tier != tier) {
                    player.mes("Unknown skill task. Examples: ::cluetest task elite 9 / ::cluetest task master 18")
                    return
                }
                giveClue(player, clue, phase = if (clue.row == TrailTornParts.ROW) 0 else TrailSkillChallenges.ASSIGNED, steps = 1)
                player.mes("Task fixtures are single-step tests. All 60 skill tasks have action handling; live acceptance and other clue routes are separate.")
            }
            "eel", "gem", "elf", "watermelon" -> {
                if (args.size != 1) return help(player)
                val row = when (action) { "eel" -> TrailSkillChallenges.sacredEelTask; "elf" -> TrailSkillChallenges.elfTask; "watermelon" -> TrailSkillChallenges.watermelonTask; else -> TrailSkillChallenges.gemStallTask }
                val extras = when (action) {
                    "eel" -> listOf(InvObj("obj.knife"), InvObj("obj.snakeboss_eel", 3))
                    "watermelon" -> listOf(InvObj("obj.rake"), InvObj("obj.dibber"), InvObj("obj.spade"), InvObj("obj.watermelon_seed", 3))
                    else -> emptyList()
                }
                if (giveClue(player, progress.catalog.clues.getValue(row), TrailSkillChallenges.ASSIGNED, 1, extras)) {
                    player.mes(if (action == "eel") "Use the knife on a sacred eel, then return to Sherlock. Cooking 72 is required."
                        else if (action == "watermelon") "Rake an allotment, plant the watermelon seeds, then return to Sherlock. Farming 47 is required. Falador farm: 3056,3309."
                        else if (action == "elf") "Pickpocket an elf in Lletya or Prifddinas, then return to Sherlock. Thieving 85 is required."
                        else "Steal from the gem stall in Ardougne market (2667,3303), then return to Sherlock. Thieving 75 is required.")
                }
            }
            else -> help(player)
        }
    }

    private fun giveClue(player: Player, clue: TrailClue, phase: Int, steps: Int, extras: List<InvObj> = emptyList()): Boolean {
        if (progress.ownsClue(player, clue.tier)) {
            player.mes("You already hold or bank a ${clue.tier.key} clue. Finish or drop that clue before creating another test scroll.")
            return false
        }
        val item = InvObj(checkNotNull(ServerCacheManager.getItem(progress.catalog.item(clue))), 1, TrailState(clue.row, steps, phase = phase).encode())
        if (!give(player, listOf(item) + extras)) return false
        player.mes("${clue.tier.key} test clue, row ${clue.row}: ${clue.text}")
        return true
    }

    private fun give(player: Player, items: List<InvObj>): Boolean {
        val result = player.invTransaction(player.inv) {
            val inventory = select(player.inv)
            for (item in items) add(inventory, item.id, item.count, item.vars, strict = true)
        }
        player.mes(if (result.success) "Clue test items added to your inventory." else "Not enough inventory space. Nothing was added or removed.")
        return result.success
    }

    private fun info(player: Player) {
        val active = player.inv.objs.filterNotNull().mapNotNull { progress.state(it) }
        if (active.isEmpty()) { player.mes("No initialized clue in your inventory. Open a scroll box or use ::cluetest scroll [tier]."); return }
        for (state in active) {
            val clue = progress.catalog.clues.getValue(state.row)
            player.mes("${clue.tier.key} ${clue.kind}: row ${state.row}, step ${state.completed + 1}/${state.total}, phase ${state.phase}.")
            player.mes(clue.text)
        }
    }

    private fun help(player: Player) {
        player.mes("::cluekit | ::cluetest box [tier] [1-28] | ::cluetest casket [tier] [1-28]")
        player.mes("::cluetest scroll [tier] [kind] | ::cluetest task [tier] [index] | ::cluetest info")
        player.mes("::cluetest eel / gem / elf / watermelon: assigned one-step tests. Tiers: beginner (default), easy, medium, hard, elite, master.")
        player.mes("::cluetest enchant / chest / lamp / shayzien / tablet / cremate / mage / shade: native action tests, then return to Sherlock.")
        player.mes("::cluetest aerial / skullball / ape / rellekka / warlock / chin / tecu / reanimate / spirit / parts: final native task tests.")
    }

    private fun tier(value: String?) = TrailTier.entries.firstOrNull { it.key == value?.lowercase() }

    companion object {
        private data class ActionFixture(val row: String, val items: List<Pair<String, Int>>, val message: String)
        private val actionFixtures = mapOf(
            "aerial" to ActionFixture("dbrow.cluehelper_skillchallenge_elite_3", listOf("obj.aerial_fishing_gloves_bird" to 1, "obj.fish_chunks" to 100, "obj.knife" to 1), "Wear the cormorant glove and catch a mottled eel on Molch Island (1367,3632). Fishing 73, Hunter 68. Return to Sherlock."),
            "skullball" to ActionFixture("dbrow.cluehelper_skillchallenge_elite_4", listOf("obj.ring_of_charos" to 1), "Wear the ring, talk to Skullball Boss (3549,9867,0), and score a goal. Agility 25. Return to Sherlock."),
            "ape" to ActionFixture("dbrow.cluehelper_skillchallenge_elite_5", listOf("obj.mm_monkey_greegree_for_small_ninja_monkey" to 1), "Wield the greegree and complete every Ape Atoll obstacle in order, starting at 2754,2742. Agility 48. Return to Sherlock."),
            "rellekka" to ActionFixture("dbrow.cluehelper_skillchallenge_master_9", listOf("obj.graceful_hood", "obj.graceful_cape", "obj.graceful_top", "obj.graceful_legs", "obj.graceful_gloves", "obj.graceful_boots").map { it to 1 }, "Wear all six graceful pieces for the entire Rellekka lap. Start at 2625,3677. Agility 80. Return to Sherlock."),
            "warlock" to ActionFixture("dbrow.cluehelper_skillchallenge_elite_11", listOf("obj.hunting_butterfly_net" to 1, "obj.butterfly_jar" to 1), "Catch a black warlock with a net and jar (e.g. 1233,3745). Hunter 45. Return to Sherlock."),
            "chin" to ActionFixture("dbrow.cluehelper_skillchallenge_elite_12", listOf("obj.hunting_box_trap" to 3), "Lay a box trap beside a red chinchompa, wait for a catch, then Check it (e.g. 1316,3168). Hunter 63. Return to Sherlock."),
            "tecu" to ActionFixture("dbrow.cluehelper_skillchallenge_master_vm01", listOf("obj.rope" to 1, "obj.net" to 1), "Set a net trap on a young tree at 1470,3087, then Check the catch. Hunter 79. Return to Sherlock."),
            "reanimate" to ActionFixture("dbrow.cluehelper_skillchallenge_master_23", listOf("obj.arceuus_corpse_abyssal" to 1, "obj.naturerune" to 4, "obj.soulrune" to 4, "obj.bloodrune" to 2), "Cast Master Reanimation on the head near the Dark Altar, then kill your creature. Arceuus book, Magic 90, Slayer 85. Return to Sherlock."),
            "spirit" to ActionFixture("dbrow.cluehelper_skillchallenge_master_2", listOf("obj.spirit_tree_seed" to 1, "obj.plantpot_compost" to 1, "obj.trowel" to 1, "obj.watering_can_8" to 1, "obj.rake" to 1, "obj.spade" to 1), "Plant and water the seed in the filled pot; let the sapling grow, then plant it in a spirit-tree patch. Farming 83. After growth and health-check, travel TO your tree."),
            "parts" to ActionFixture("dbrow.cluehelper_skillchallenge_master_25", emptyList(), "Read the clue, solve its three cryptic visits, then Combine the three matching torn parts. This step advances directly without Sherlock."),
            "enchant" to ActionFixture("dbrow.cluehelper_skillchallenge_elite_1", listOf("obj.dragonstone_ring" to 1, "obj.cosmicrune" to 1, "obj.waterrune" to 15, "obj.earthrune" to 15), "Cast Lvl-5 Enchant on the ring (standard spellbook, Magic 68), then return to Sherlock."),
            "chest" to ActionFixture("dbrow.cluehelper_skillchallenge_elite_7", emptyList(), "Search for traps on the Ardougne Castle chest: 2588,3291,1 or 2588,3302,1. Thieving 72."),
            "lamp" to ActionFixture("dbrow.cluehelper_skillchallenge_elite_17", listOf("obj.dorgesh_light_bulb" to 1), "Fix a broken lamp in Dorgesh-Kaan, e.g. 2699,5294,1. Firemaking 52."),
            "shayzien" to ActionFixture("dbrow.cluehelper_skillchallenge_elite_23", listOf("obj.lovakite_bar" to 4, "obj.hammer" to 1), "Use lovakite bars on an anvil and smith a tier 2+ Shayzien platebody. Smithing 63+."),
            "tablet" to ActionFixture("dbrow.cluehelper_skillchallenge_master_3", listOf("obj.arceuus_essence_block_dark" to 1, "obj.lawrune" to 2, "obj.soulrune" to 2, "obj.bloodrune" to 1), "Use the Arceuus lectern at 1679,3765,0. Arceuus spellbook and Magic 83 are required."),
            "cremate" to ActionFixture("dbrow.cluehelper_skillchallenge_master_17", listOf("obj.shade_bones5" to 1, "obj.magic_logs_pyre" to 1, "obj.tinderbox" to 1), "Build a funeral pyre in Mort'ton, e.g. 3462,3282,0. Firemaking 80."),
            "mage" to ActionFixture("dbrow.cluehelper_skillchallenge_master_5", listOf("obj.saradomin_staff" to 1), "Wear the Saradomin staff and kill a Saradomin spiritual mage in God Wars Dungeon. Slayer 83."),
            "shade" to ActionFixture("dbrow.cluehelper_skillchallenge_master_24", emptyList(), "Kill a Fiyr shade inside Mort'ton's catacombs, e.g. 3460,9695,0, then return to Sherlock."),
        )
        val kit = listOf("obj.spade", "obj.trail_sextant", "obj.trail_watch", "obj.trail_chart", "obj.rope", "obj.knife", "obj.tinderbox", "obj.hammer", "obj.chisel")
    }
}
