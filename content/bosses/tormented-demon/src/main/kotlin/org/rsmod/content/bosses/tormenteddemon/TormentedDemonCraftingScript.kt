package org.rsmod.content.bosses.tormenteddemon

import dev.openrune.rscm.RSCM.asRSCM
import org.rsmod.api.invtx.*
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseSmithingLvl
import org.rsmod.api.player.stat.craftingLvl
import org.rsmod.api.player.stat.fletchingLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.smithingLvl
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLocCategoryU
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class TormentedDemonCraftingScript : PluginScript() {

    override fun ScriptContext.startup() {
        onOpHeld1("obj.duradels_notes_on_demon_slaying") {
            if (QuestRequirements.hasCompleted(player, "quest.whileguthixsleeps")) {
                player.tdCraftingState = player.tdCraftingState or 1
                mesbox("Duradel's notes explain how to make Emberlight, a scorching bow and a purging staff from a tormented synapse.")
            } else mesbox("You must complete While Guthix Sleeps before using these notes.")
        }
        for (weapon in listOf("obj.emberlight", "obj.scorching_bow", "obj.purging_staff")) {
            onOpHeld4(weapon) { event ->
                if (inv[event.slot] === event.obj && mesboxChoice("Revert this weapon? Only the tormented synapse is returned.", "Revert", "Cancel") == 1) {
                    if (inv[event.slot] === event.obj) craft(listOf(weapon to 1), "obj.tormented_synapse", event.slot)
                }
            }
        }
        onOpHeldU("obj.bone_claw", "obj.bone_claw") { combineBurningClaws() }
        onOpLocCategoryU("category.anvil", "obj.tormented_synapse") { craftSynapseAtAnvil() }
        onOpHeldU("obj.tormented_synapse", "obj.unstrung_magic_longbow") { craftScorchingBow() }
    }

    private fun ProtectedAccess.combineBurningClaws() {
        if (craft(listOf("obj.bone_claw" to 2), "obj.bone_claws")) {
            mes("You bring the two claws together and combine them.")
        }
    }

    private suspend fun ProtectedAccess.craftSynapseAtAnvil() {
        if (!canCraftSynapse()) return
        if (!hasHammer()) {
            mesbox("You need a hammer to work metal with an anvil.")
            return
        }
        when {
            (invTotal(inv, "obj.arclight") > 0 || invTotal(inv, "obj.arclight_inactive") > 0) &&
                invTotal(inv, "obj.iron_bar") > 0 && invTotal(inv, "obj.battlestaff") > 0 -> {
                if (choice2("Emberlight", true, "Purging staff", false)) craftEmberlight() else craftPurgingStaff()
            }
            invTotal(inv, "obj.arclight") > 0 || invTotal(inv, "obj.arclight_inactive") > 0 -> craftEmberlight()
            invTotal(inv, "obj.iron_bar") > 0 && invTotal(inv, "obj.battlestaff") > 0 ->
                craftPurgingStaff()
            else ->
                mesbox(
                    "You need Arclight, or an iron bar and a battlestaff, to work the " +
                        "tormented synapse into a weapon.",
                )
        }
    }

    private suspend fun ProtectedAccess.craftEmberlight() {
        if (player.smithingLvl < 74) {
            mesbox(
                "You need a Smithing level of at least 74 to attach the tormented synapse " +
                    "to Arclight.",
            )
            if (player.hitpoints > 5) takeInstantHit(org.rsmod.game.hit.HitType.Typeless, 4)
            return
        }

        val slot = inv.objs.indexOfFirst { item -> item != null && (item.id == "obj.arclight".asRSCM() || item.id == "obj.arclight_inactive".asRSCM()) && ArclightState.ready(item) }
        if (slot < 0) { mesbox("Arclight needs 10,000 combined charges and infusion before it can become Emberlight."); return }
        val blade = checkNotNull(inv[slot])
        mesbox("You set to work fusing the synapse into Arclight's blade...")
        delay(3)
        anim("seq.human_smithing")
        soundSynth(3771)
        delay(4)

        if (player.smithingLvl >= 74 && inv[slot] === blade && hasHammer() && craft(listOf("obj.tormented_synapse" to 1, dev.openrune.rscm.RSCM.getReverseMapping(dev.openrune.rscm.RSCMType.OBJ, blade.id) to 1), "obj.emberlight", slot)) {
            creationXp("stat.smithing", 2)
            objbox("obj.emberlight", "The synapse fuses with the blade, and Emberlight is complete.")
        }
    }

    private suspend fun ProtectedAccess.craftPurgingStaff() {
        if (player.baseSmithingLvl < 55 || player.craftingLvl < 74) {
            mesbox(
                "You need a Smithing level of at least 55 and a Crafting level of at least " +
                    "74 to work the tormented synapse into a battlestaff.",
            )
            if (player.craftingLvl < 74 && player.hitpoints > 5) takeInstantHit(org.rsmod.game.hit.HitType.Typeless, 4)
            return
        }

        mesbox("You set to work fusing the synapse into the battlestaff...")
        delay(3)
        anim("seq.human_smithing")
        soundSynth(3771)
        delay(4)

        if (!hasHammer() || player.baseSmithingLvl < 55 || player.craftingLvl < 74) return
        val consumed = craft(listOf("obj.tormented_synapse" to 1, "obj.iron_bar" to 1, "obj.battlestaff" to 1), "obj.purging_staff")
        if (consumed) {
            creationXp("stat.crafting", 8)
            statAdvance("stat.smithing", 13.0)
            objbox("obj.purging_staff", "The synapse fuses with the staff, and it starts to purge.")
        }
    }

    private suspend fun ProtectedAccess.craftScorchingBow() {
        if (!canCraftSynapse()) return
        if (player.fletchingLvl < 74) {
            mesbox(
                "You need a Fletching level of at least 74 to work the tormented synapse " +
                    "into a magic longbow.",
            )
            return
        }

        mesbox("You bind the synapse to the bow, and it begins to smoulder...")
        delay(3)
        anim("seq.stringing_magic_longbow")
        soundSynth(3771)
        delay(2)

        if (player.fletchingLvl >= 74 && craft(listOf("obj.tormented_synapse" to 1, "obj.unstrung_magic_longbow" to 1), "obj.scorching_bow")) {
            creationXp("stat.fletching", 4)
            objbox("obj.scorching_bow", "The synapse fuses with the bow, and it starts to smoulder.")
        }
    }

    private fun ProtectedAccess.canCraftSynapse(): Boolean {
        if (!QuestRequirements.hasCompleted(player, "quest.whileguthixsleeps")) { mes("You must complete While Guthix Sleeps first."); return false }
        if (player.tdCraftingState and 1 == 0) { mes("Read Duradel's notes before making a synapse weapon. Ask Kuradal about Duradel to obtain them."); return false }
        return true
    }

    private fun ProtectedAccess.creationXp(stat: String, flag: Int) {
        statAdvance(stat, if (player.tdCraftingState and flag == 0) 730.0 else 73.0)
        player.tdCraftingState = player.tdCraftingState or flag
    }

    private suspend fun ProtectedAccess.mesboxChoice(text: String, yes: String, no: String): Int {
        mesbox(text)
        return choice2(yes, 1, no, 2)
    }

    private fun ProtectedAccess.craft(inputs: List<Pair<String, Int>>, output: String, selectedSlot: Int? = null): Boolean =
        player.invTransaction(inv) {
            val target = select(inv)
            for ((index, input) in inputs.withIndex()) delete(target, input.first.asRSCM(), input.second, slot = if (index == inputs.lastIndex) selectedSlot else null)
            add(target, output.asRSCM(), 1)
        }.success

    private fun ProtectedAccess.hasHammer(): Boolean =
        inv.contains("obj.hammer") ||
            inv.contains("obj.imcando_hammer") ||
            inv.contains("obj.imcando_hammer_offhand")
}
