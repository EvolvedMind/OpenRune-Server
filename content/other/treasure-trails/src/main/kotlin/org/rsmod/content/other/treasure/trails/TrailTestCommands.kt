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
            desc = "Clue test items: kit, box, scroll, casket, task, eel, gem, info"
            cheat { run(player, args) }
        }
    }

    private fun run(player: Player, args: List<String>) {
        val action = args.firstOrNull()?.lowercase()
        when (action) {
            "kit" -> if (args.size == 1) give(player, kit.map { InvObj(it) }) else help(player)
            "info" -> if (args.size == 1) info(player) else help(player)
            "box", "casket" -> {
                val tier = tier(args.getOrNull(1))
                val count = if (args.size == 2) 1 else args.getOrNull(2)?.toIntOrNull()
                if (args.size !in 2..3 || tier == null || count == null || count !in 1..28) return help(player)
                give(player, listOf(InvObj(checkNotNull(ServerCacheManager.getItem(if (action == "box") tier.box else tier.casket)), count)))
            }
            "scroll" -> {
                val tier = tier(args.getOrNull(1))
                if (args.size !in 2..3 || tier == null) return help(player)
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
                giveClue(player, clue, phase = TrailSkillChallenges.ASSIGNED, steps = 1)
                player.mes("Task fixtures are single-step tests. Some catalog tasks are still unimplemented; see the coverage document.")
            }
            "eel", "gem" -> {
                if (args.size != 1) return help(player)
                val row = if (action == "eel") TrailSkillChallenges.sacredEelTask else TrailSkillChallenges.gemStallTask
                val extras = if (action == "eel") listOf(InvObj("obj.knife"), InvObj("obj.snakeboss_eel", 3)) else emptyList()
                if (giveClue(player, progress.catalog.clues.getValue(row), TrailSkillChallenges.ASSIGNED, 1, extras)) {
                    player.mes(if (action == "eel") "Use the knife on a sacred eel, then return to Sherlock. Cooking 72 is required."
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
        if (active.isEmpty()) { player.mes("No initialized clue in your inventory. Open a scroll box or use ::cluetest scroll <tier>."); return }
        for (state in active) {
            val clue = progress.catalog.clues.getValue(state.row)
            player.mes("${clue.tier.key} ${clue.kind}: row ${state.row}, step ${state.completed + 1}/${state.total}, phase ${state.phase}.")
            player.mes(clue.text)
        }
    }

    private fun help(player: Player) {
        player.mes("::cluekit | ::cluetest box <tier> [1-28] | ::cluetest casket <tier> [1-28]")
        player.mes("::cluetest scroll <tier> [kind] | ::cluetest task <tier> <index> | ::cluetest info")
        player.mes("::cluetest eel / ::cluetest gem: assigned one-step tests. Tiers: beginner, easy, medium, hard, elite, master.")
    }

    private fun tier(value: String?) = TrailTier.entries.firstOrNull { it.key == value?.lowercase() }

    companion object {
        val kit = listOf("obj.spade", "obj.trail_sextant", "obj.trail_watch", "obj.trail_chart", "obj.rope", "obj.knife", "obj.tinderbox", "obj.hammer", "obj.chisel")
    }
}
