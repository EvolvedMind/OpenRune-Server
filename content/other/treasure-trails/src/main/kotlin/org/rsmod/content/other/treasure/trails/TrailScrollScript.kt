package org.rsmod.content.other.treasure.trails

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.game.inv.Inventory
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class TrailScrollScript @Inject constructor(
    private val progress: TrailProgress,
    private val puzzles: TrailPuzzleScript = TrailPuzzleScript(progress),
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (tier in TrailTier.entries) onOpHeld1("obj.league_clue_box_${tier.key}") {
            if (!progress.openBox(player, it.inventory, it.slot, tier)) mes("You need more inventory space to open this scroll box.")
        }
        val ids = progress.catalog.itemRows.keys + listOf("obj.trail_clue_beginner".asRSCM(), "obj.trail_clue_master".asRSCM())
        for (id in ids.distinct()) {
            val type = checkNotNull(ServerCacheManager.getItem(id))
            onOpHeld1(type) { read(it.inventory, it.slot) }
            onOpHeld2(type) {
                val state = progress.initialize(player, it.inventory, it.slot) ?: return@onOpHeld2
                mes("You have completed ${state.completed} steps on this Treasure Trail.")
            }
        }
    }
    private fun ProtectedAccess.read(inventory: Inventory, slot: Int) {
        val state = progress.initialize(player, inventory, slot) ?: return
        val clue = progress.catalog.clues.getValue(state.row)
        if (state.phase == 1 || state.phase == 6) {
            val item = inventory[slot] ?: return
            puzzles.show(this, ActiveTrail(slot, item, state, clue), state.phase == 6)
            return
        }
        VarPlayerIntMapSetter.set(player, "varp.cluehelper_infobox_clue", state.row)
        ifOpenMain("interface.trail_cluetext")
        val text = when (clue.kind) {
            "anagram" -> "This anagram reveals who to speak to next:<br><br>${clue.text}"
            "cipher" -> "The cipher reveals who to speak to next:<br><br>${clue.text}"
            "falobard" -> "Falo the bard wishes to see the item described below:<br><br>${clue.text}"
            "hotcold" -> "Use your strange device to find the buried treasure."
            "music" -> "Play ${progress.catalog.fields(clue.fields.int("music")).string("displayname")} for Cecilia."
            else -> clue.text
        }
        ifSetText("component.trail_cluetext:text", text)
    }
}
