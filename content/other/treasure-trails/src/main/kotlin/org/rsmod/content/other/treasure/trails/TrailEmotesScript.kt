package org.rsmod.content.other.treasure.trails

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.IdentityHashMap
import org.rsmod.api.player.events.interact.ContextualNpcOp
import org.rsmod.api.player.interact.ContextualInteractions
import org.rsmod.api.player.output.mes
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.*
import org.rsmod.content.interfaces.emotes.PlayEmote
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.interact.InteractionOp
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

@Singleton
internal class TrailEmotesScript @Inject constructor(
    private val progress: TrailProgress,
    private val targets: TrailTargets,
    private val requirements: TrailRequirements,
    private val guards: TrailGuards,
    private val repo: NpcRepository,
    private val contextual: ContextualInteractions,
) : PluginScript() {
    private data class Meeting(val owner: Player, val state: Int)
    private val meetings = IdentityHashMap<Npc, Meeting>()

    override fun ScriptContext.startup() {
        onEvent<PlayEmote> { perform(player, seq.id) }
        onOpHeld1("obj.bullroarer") { anim("seq.human_bullroarer"); perform(player, "seq.human_bullroarer".asRSCM()) }
        contextual.npc("trail-uri") { player, npc, op ->
            if (meetings[npc]?.owner === player && op == InteractionOp.Op1) ContextualNpcOp(npc, KEY) else null
        }
        onProtectedEvent<ContextualNpcOp>(KEY) {
            val meeting = meetings[it.npc] ?: return@onProtectedEvent
            if (meeting.owner !== player) return@onProtectedEvent
            val active = targets.active(player).firstOrNull { clue -> clue.state.encode() == meeting.state } ?: return@onProtectedEvent
            if (active.state.phase != 8) { mes("Perform the second emote before speaking to Uri."); return@onProtectedEvent }
            val missing = requirements.missing(player, active.clue)
            if (missing != null) { mes(missing); return@onProtectedEvent }
            if (progress.advance(player, player.inv, active.slot, active.item)) {
                mes("Uri hands you the next part of your Treasure Trail.")
                meetings.remove(it.npc)
                repo.del(it.npc, Int.MAX_VALUE)
            }
        }
        onEvent<NpcStateEvents.Delete> { meetings.remove(npc) }
        onPlayerLogout { clear(player) }
    }
    override fun ScriptContext.shutdown() {
        contextual.unregister("trail-uri")
        meetings.values.map { it.owner }.distinct().forEach(::clear)
    }
    private fun perform(player: Player, sequence: Int) {
        val emote = sequences.entries.firstOrNull { (_, names) -> names.any { it.asRSCM() == sequence } }?.key ?: return
        val active = targets.active(player).firstOrNull { active ->
            active.clue.kind == "emote" && active.clue.targets.any { row ->
                val target = progress.catalog.fields(row)
                target.table == "cluehelper_target_coord" && targets.near(player.coords, target.int("coord"), 2)
            }
        } ?: return
        val missing = requirements.missing(player, active.clue)
        if (missing != null) { player.mes(missing); return }
        val required = active.clue.fields.ints("emote")
        val index = if (active.state.phase == 7) 1 else 0
        // After logout or Uri's timeout, either required emote can recall the finished meeting.
        if (active.state.phase == 8) {
            if (emote !in required) return
        } else if (required.getOrNull(index) != emote) return
        if (guards.requireFight(player, active)) return
        val phase = if (active.state.phase == 8) 8 else if (index == 0 && required.size > 1) 7 else 8
        if (!progress.phase(player, active.slot, active.item, phase)) return
        val state = progress.state(player.inv[active.slot]!!)!!
        clear(player)
        val uri = Npc(checkNotNull(ServerCacheManager.getNpc("npc.trail_${active.clue.tier.key}_uri".asRSCM())), player.coords.translate(1, 0))
        meetings[uri] = Meeting(player, state.encode())
        repo.add(uri, 100)
    }
    fun clear(player: Player) {
        for (npc in meetings.filterValues { it.owner === player }.keys.toList()) {
            meetings.remove(npc)
            repo.del(npc, Int.MAX_VALUE)
        }
    }
    companion object {
        private const val KEY = 0x555249L
        private val base = listOf("yes", "no", "bow", "angry", "think", "wave", "shrug", "cheer", "beckon", "laugh", "jump_with_joy", "yawn", "dance", "dance_scottish", "dance_spin", "dance_headbang", "cry", "blow_kiss", "panic", "ya_boo_sucks", "clap", "fremmenik_salute")
        private val sequences = base.mapIndexed { index, name -> index to listOf("seq.emote_$name", "seq.emote_${name}_loop") }.toMap() + mapOf(
            -1 to listOf("seq.human_bullroarer"),
            23 to listOf("seq.human_cave_goblin_dance", "seq.human_cave_goblin_dance_loop"),
            29 to listOf("seq.emote_stampfeet", "seq.emote_stampfeet_loop"),
            30 to listOf("seq.emote_panic_flap", "seq.emote_panic_flap_loop"),
            31 to listOf("seq.emote_slap_head", "seq.emote_slap_head_loop"),
            55 to listOf("seq.human_emote_crabdance", "seq.human_emote_crabdance_loop"),
        )
    }
}
