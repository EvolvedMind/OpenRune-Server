package org.rsmod.content.skills.magic.utility

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.player.events.skilling.SkillingActionCompleteEvent
import org.rsmod.api.player.events.skilling.SkillingActionContext
import org.rsmod.api.player.events.skilling.SkillingProductSource
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.player.ui.IfOverlayButtonT
import org.rsmod.api.script.onEvent
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.events.EventBus
import org.rsmod.game.inv.isType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DragonstoneEnchantmentScript @Inject constructor(
    private val bus: EventBus,
    private val launcher: ProtectedAccessLauncher,
    private val spells: MagicSpellRegistry,
    private val runes: MagicRuneManager,
) : PluginScript() {
    override fun ScriptContext.startup() {
        val spell = spells.getUtilitySpell(checkNotNull(ServerCacheManager.getItem("obj.68_enchant_amulet_lvl5".asRSCM())))
        val key = EventBus.composeLongKey(spell.component.packed, "component.inventory:items".asRSCM())
        onEvent<IfOverlayButtonT>(key) {
            val target = targetObj ?: return@onEvent
            val output = recipes[target.id] ?: return@onEvent
            if (player.isDelayed || player.isAccessProtected || player.actionDelay > player.currentMapClock) return@onEvent
            bus.let { player.clearPendingAction(it) }
            launcher.launch(player) {
                val original = inv[targetSlot] ?: return@launch
                if (!original.isType(target) || original.count != 1 || original.vars != 0) return@launch
                if (!produceSpell(player, runes, spell, target.id, targetSlot, output.asRSCM())) return@launch
                actionDelay = mapClock + 3
                val neck = target.id == "obj.strung_dragonstone_amulet".asRSCM() || target.id == "obj.dragonstone_necklace".asRSCM()
                anim(if (neck) "seq.human_enchantamuletlvl3" else "seq.human_cast_enchantring")
                spotanim(if (neck) "spotanim.enchant_amulet2_lvl5" else "spotanim.enchant_ring", height = 92)
                statAdvance("stat.magic", spell.castXp)
                publish(SkillingActionCompleteEvent(player, SkillingActionContext.Product("stat.magic", output, 1, spell.castXp, SkillingProductSource.JewelleryEnchantment)))
            }
        }
    }
    companion object {
        val recipes by lazy {
            mapOf(
                "obj.dragonstone_ring".asRSCM() to "obj.ring_of_wealth",
                "obj.dragonstone_necklace".asRSCM() to "obj.jewl_necklace_of_skills",
                "obj.jewl_dragonstone_bracelet".asRSCM() to "obj.jewl_bracelet_of_combat",
                "obj.strung_dragonstone_amulet".asRSCM() to "obj.amulet_of_glory",
            )
        }
    }
}
