package org.rsmod.content.skills.magic.utility

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.player.events.skilling.SkillingActionCompleteEvent
import org.rsmod.api.player.events.skilling.SkillingActionContext
import org.rsmod.api.player.events.skilling.SkillingProductSource
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillMultiEntry
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class BarrowsTabletScript @Inject constructor(private val spells: MagicSpellRegistry, private val runes: MagicRuneManager) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.arceuus_lectern") {
            val loc = it.loc
            if (!requirements()) return@onOpLoc1
            openSkillMulti(SkillMultiConfig(verb = "make", entries = listOf(SkillMultiEntry(OUTPUT)),
                maxCountProvider = { inventory, _ -> inventory.count(ESSENCE) })) { selected ->
                repeat(selected.amount) { if (!make(loc)) return@openSkillMulti }
            }
        }
        onOpLoc2("loc.arceuus_lectern") { make(it.loc) }
        onOpLocU("loc.arceuus_lectern", ESSENCE) { make(it.loc) }
    }
    private fun ProtectedAccess.requirements(): Boolean {
        val spell = spells.allSpells().single { it.name == "Barrows Teleport" }
        if (!inv.contains(ESSENCE)) { mes("You need a dark essence block to make this tablet."); return false }
        return runes.canCastSpell(player, spell)
    }
    private suspend fun ProtectedAccess.make(loc: BoundLocInfo): Boolean {
        arriveDelay(); faceLoc(loc)
        if (!requirements()) return false
        val start = coords
        if (coords.level != loc.coords.level || coords.chebyshevDistance(loc.coords) > 2) return false
        val slot = inv.indexOfFirst { it?.id == ESSENCE.asRSCM() }
        val original = inv[slot] ?: return false
        anim("seq.human_use_lectern"); delay(3)
        if (coords != start || inv[slot] !== original) return false
        val spell = spells.allSpells().single { it.name == "Barrows Teleport" }
        if (!produceSpell(player, runes, spell, ESSENCE.asRSCM(), slot, OUTPUT.asRSCM())) return false
        statAdvance("stat.magic", 90.0)
        publish(SkillingActionCompleteEvent(player, SkillingActionContext.Product("stat.magic", OUTPUT, 1, 90.0, SkillingProductSource.TabletMaking)))
        return true
    }
    companion object { const val ESSENCE = "obj.arceuus_essence_block_dark"; const val OUTPUT = "obj.teletab_barrows" }
}
