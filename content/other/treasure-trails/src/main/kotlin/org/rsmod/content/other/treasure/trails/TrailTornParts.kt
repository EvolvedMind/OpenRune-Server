package org.rsmod.content.other.treasure.trails

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.invtx.*
import org.rsmod.api.player.events.interact.ContextualNpcOp
import org.rsmod.api.player.interact.ContextualInteractions
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.*
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Three native cryptic visits award owned parts; assembly advances exactly one trail step. */
@Singleton
internal class TrailTornParts @Inject constructor(
    private val progress: TrailProgress,
    private val targets: TrailTargets,
    private val requirements: TrailRequirements,
    private val contextual: ContextualInteractions,
) : PluginScript() {
    override fun ScriptContext.startup() {
        contextual.npc(OWNER) { player, npc, op ->
            if (op == InteractionOp.Op1 && find(player, npc) != null) ContextualNpcOp(npc, KEY) else null
        }
        onProtectedEvent<ContextualNpcOp>(KEY) {
            val (active, index) = find(player, it.npc) ?: return@onProtectedEvent
            val subclue = choices(active.state)[index]
            val missing = requirements.missing(player, subclue)
            if (missing != null) { mes(missing); return@onProtectedEvent }
            give(player, active, index)
        }
        for (part in parts) {
            onOpHeld1(part) { if (inv[it.slot] === it.obj && ownedPart(player, it.slot)) combine(player) }
            onOpHeld2(part) {
                if (inv[it.slot] !== it.obj || !ownedPart(player, it.slot)) return@onOpHeld2
                val active = targets.active(player).firstOrNull { it.state.row == ROW && it.item.vars != 0 } ?: return@onOpHeld2
                val index = parts.indexOf(part)
                mes(choices(active.state)[index].text)
            }
        }
        for (a in parts.indices) for (b in a + 1 until parts.size) onOpHeldU(parts[a], parts[b]) { if (inv[it.firstSlot]?.id == it.first.id && inv[it.secondSlot]?.id == it.second.id && ownedPart(player, it.firstSlot) && ownedPart(player, it.secondSlot)) combine(player) }
    }
    override fun ScriptContext.shutdown() { contextual.unregister(OWNER) }

    fun choices(state: TrailState): List<TrailClue> {
        return choices(progress.catalog, state)
    }

    fun text(state: TrailState): String = text(progress.catalog, state)

    private fun find(player: Player, npc: Npc): Pair<ActiveTrail, Int>? {
        for (active in targets.active(player).filter { it.state.row == ROW }) {
            val index = choices(active.state).indexOfFirst { clue -> clue.targets.any { row ->
                val fields = progress.catalog.fields(row)
                (npc.type.id in fields.ints("npc") || npc.visType.id in fields.ints("npc") || npc.visType.id in fields.ints("fallback_npc")) && targets.near(npc.coords, fields.int("coord"), 32)
            } }
            if (index >= 0) return active to index
        }
        return null
    }

    private fun give(player: Player, active: ActiveTrail, index: Int): Boolean {
        if (player.inv[active.slot] !== active.item) return false
        val owner = TrailPuzzleItems.owner(active.state)
        val part = parts[index].asRSCM()
        if ((player.invMap.values + listOf(player.inv)).any { inventory -> inventory.objs.filterNotNull().any { it.id == part && it.vars == owner } }) {
            player.mes("You already have this torn clue part. Retrieve it from your bank if necessary."); return false
        }
        val result = player.invTransaction(player.inv) {
            val target = select(player.inv)
            delete(target, active.item.id, 1, active.slot)
            add(target, active.item.id, 1, active.state.copy(phase = (active.state.phase and 7) or (1 shl index)).encode(), active.slot)
            add(target, part, 1, owner)
        }
        player.mes(if (result.success) "You find torn clue part ${index + 1}. Collect and combine all three parts." else "Make room for the torn clue part.")
        return result.success
    }

    private fun ownedPart(player: Player, slot: Int): Boolean {
        val item = player.inv[slot] ?: return false
        val active = targets.active(player).firstOrNull { it.state.row == ROW } ?: return false
        return item.id in parts.map { it.asRSCM() } && item.count == 1 && item.vars == TrailPuzzleItems.owner(active.state)
    }

    private fun combine(player: Player) {
        val active = targets.active(player).firstOrNull { it.state.row == ROW } ?: return player.mes("These parts do not belong to an active master clue.")
        if (active.state.phase and 7 != 7) { player.mes("Solve all three cryptic clues first."); return }
        val owner = TrailPuzzleItems.owner(active.state)
        val pieces = parts.map { symbol -> player.inv.objs.filterNotNull().firstOrNull { it.id == symbol.asRSCM() && it.vars == owner && it.count == 1 } }
        if (pieces.any { it == null }) { player.mes("Bring all three parts belonging to this clue into your inventory."); return }
        if (progress.advance(player, player.inv, active.slot, active.item, pieces.filterNotNull())) player.mes("You combine the three torn parts and continue your Treasure Trail.")
    }

    companion object {
        val ROW get() = "dbrow.cluehelper_skillchallenge_master_25".asRSCM()
        val parts = listOf("obj.trail_master_part1", "obj.trail_master_part2", "obj.trail_master_part3")
        private const val OWNER = "treasure-trails-torn-parts"
        private const val KEY = 0x74002701L

        fun choices(catalog: TrailCatalog, state: TrailState): List<TrailClue> {
        val pool = catalog.clues.values.filter {
            it.tier == TrailTier.MASTER && it.kind == "cryptic" && it.row !in TrailCatalog.hotColdIntros && it.row != TrailCatalog.faloIntro &&
                it.targets.isNotEmpty() && it.fields.ints("challenge").isEmpty() && it.fields.ints("combat_encounter").isEmpty() &&
                it.targets.all { target -> catalog.fields(target).table == "cluehelper_target_npc" }
        }.sortedBy { it.row }
        check(pool.size >= 3)
        val offset = (state.total * 11 + state.completed * 7) % pool.size
        return (0..2).map { pool[(offset + it) % pool.size] }
    }

    fun text(catalog: TrailCatalog, state: TrailState): String = choices(catalog, state).mapIndexed { i, clue ->
        "Part ${i + 1}${if (state.phase and (1 shl i) != 0) " (found)" else ""}: ${clue.text}"
    }.joinToString("<br><br>")
    }
}
