package org.rsmod.content.other.treasure.trails

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.music.MusicRepository
import org.rsmod.api.player.events.PlayerDigEvent
import org.rsmod.api.player.events.interact.ContextualLocOp
import org.rsmod.api.player.events.interact.ContextualNpcOp
import org.rsmod.api.player.interact.ContextualInteractions
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.*
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class TrailInteractionScript @Inject constructor(
    private val progress: TrailProgress,
    private val targets: TrailTargets,
    private val requirements: TrailRequirements,
    private val puzzles: TrailPuzzleScript,
    private val guards: TrailGuards,
    private val contextual: ContextualInteractions,
    private val music: MusicRepository,
) : PluginScript() {
    override fun ScriptContext.shutdown() { contextual.unregister("treasure-trails") }
    override fun ScriptContext.startup() {
        contextual.npc("treasure-trails") { player, npc, op ->
            if (op == InteractionOp.Op1 && targets.npc(player, npc) != null) ContextualNpcOp(npc, KEY) else null
        }
        contextual.loc("treasure-trails") { player, loc, op ->
            if (op == InteractionOp.Op1 && targets.loc(player, loc) != null) ContextualLocOp(loc, KEY) else null
        }
        onProtectedEvent<ContextualNpcOp>(KEY) {
            val active = targets.npc(player, it.npc) ?: return@onProtectedEvent
            resolve(active)
        }
        onProtectedEvent<ContextualLocOp>(KEY) {
            val active = targets.loc(player, it.loc) ?: return@onProtectedEvent
            resolve(active)
        }
        onEvent<PlayerDigEvent> {
            val active = targets.dig(player) ?: return@onEvent
            handled = true
            val missing = requirements.missing(player, active.clue)
            if (missing != null) player.mes(missing)
            else if (!guards.requireFight(player, active)) complete(player, active)
        }
    }
    private suspend fun ProtectedAccess.resolve(active: ActiveTrail) {
        if (active.state.row == TrailCatalog.sherlockIntro) {
            if (progress.assignSherlock(player, active.slot, active.item)) {
                val assigned = progress.state(player.inv[active.slot]!!)!!
                mes(progress.catalog.clues.getValue(assigned.row).text)
            }
            return
        }
        val missing = requirements.missing(player, active.clue)
        if (missing != null) { mes(missing); return }
        if (active.clue.kind == "music") {
            val requested = progress.catalog.fields(active.clue.fields.int("music")).string("displayname")
            if (music.forId(player.vars["varbit.music_curr_id"])?.displayName != requested) {
                mes("Play $requested for Cecilia, then speak to her again.")
                return
            }
        }
        if (active.clue.kind == "skillchallenge") {
            val charlie = charlieItems[active.state.row]
            if (charlie != null) {
                if (player.inv.count(charlie) == 0) { mes(active.clue.text); return }
                if (progress.advance(player, player.inv, active.slot, active.item, listOf(InvObj(charlie)))) {
                    mes("Charlie accepts your item and hands you the next part of your Treasure Trail.")
                }
                return
            }
            if (active.state.phase == TrailSkillChallenges.COMPLETED) {
                complete(player, active)
                return
            }
            if (active.state.phase != TrailSkillChallenges.ASSIGNED) {
                progress.phase(player, active.slot, active.item, TrailSkillChallenges.ASSIGNED)
            }
            mes(active.clue.text.ifEmpty { "Complete the task described by this clue before returning." })
            return
        }
        for (row in active.clue.fields.ints("challenge")) {
            val fields = progress.catalog.fields(row)
            if (fields.table == "cluehelper_challenge_box") {
                if (active.state.phase != 2 || TrailPuzzleItems.owned(player, active.state) == null) {
                    val light = when (active.state.phase) { 1 -> false; 6 -> true; else -> fields.string("description").contains("light") }
                    puzzles.show(this, active, light)
                    return
                }
            } else if (fields.table == "cluehelper_challenge_question") {
                val tuple = fields.values("question")
                val question = tuple.filterIsInstance<String>().firstOrNull() ?: return
                val answer = tuple.filterIsInstance<Number>().firstOrNull()?.toInt() ?: return
                val response = numberDialog(question)
                if (response != answer) { mes("That is not the correct answer."); return }
            }
        }
        if (guards.requireFight(player, active)) return
        complete(player, active)
    }
    private fun complete(player: Player, active: ActiveTrail) {
        val keys = active.clue.targets.map(progress.catalog::fields).filter { it.table == "cluehelper_target_key" }
            .map { InvObj(checkNotNull(ServerCacheManager.getItem(it.int("key"))), it.int("count").coerceAtLeast(1)) }
        if (keys.any { key -> player.inv.objs.filterNotNull().filter { it.id == key.id }.sumOf { it.count } < key.count }) {
            player.mes("This is locked. Find the key described by your clue.")
            return
        }
        val puzzle = if (active.state.phase == 2) listOfNotNull(TrailPuzzleItems.owned(player, active.state)) else emptyList()
        if (progress.advance(player, player.inv, active.slot, active.item, keys + puzzle)) {
            player.mes(if (active.state.completed + 1 == active.state.total) "You have completed the trail and found a reward casket!" else "You find another clue. Your Treasure Trail continues.")
        }
    }
    companion object {
        private const val KEY = 0x545241494cL
        private val charlieItems by lazy {
            listOf("trout", "pike", "raw_herring", "raw_trout", "iron_ore", "iron_dagger", "leather_armour", "leather_chaps")
                .mapIndexed { index, item -> "dbrow.cluehelper_skillchallenge_beginner_$index".asRSCM() to "obj.$item" }.toMap()
        }
    }
}
