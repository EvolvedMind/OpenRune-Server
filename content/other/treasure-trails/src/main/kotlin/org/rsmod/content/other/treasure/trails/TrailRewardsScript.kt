package org.rsmod.content.other.treasure.trails

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.*
import org.rsmod.content.interfaces.collectionlog.CollectionLog
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class TrailRewardsScript @Inject constructor(
    private val collectionLog: CollectionLog,
    private val rewards: TrailRewards,
    private val launcher: ProtectedAccessLauncher,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (tier in TrailTier.entries) onOpHeld1("obj.trail_reward_casket_${tier.key}") { event ->
            if (rewards.pending(player).objs.any { it != null }) {
                mes("Collect your previous rewards before opening another casket.")
                show()
                return@onOpHeld1
            }
            val original = player.inv[event.slot] ?: return@onOpHeld1
            val rolled = rewards.open(player, event.slot, original, tier) ?: return@onOpHeld1
            for (item in rolled) {
                val type = checkNotNull(ServerCacheManager.getItem(item.id))
                collectionLog.grant(player, if (type.certtemplate > 0) type.certlink else item.id, item.count, source = "${tier.key.replaceFirstChar(Char::uppercase)} Treasure Trail")
            }
            mes("You open your ${tier.key} reward casket.")
            show()
        }
        onIfClose("interface.trail_rewardscreen") { rewards.claim(player) }
        onPlayerLogin {
            if (rewards.pending(player).objs.any { it != null }) player.mes("You have unclaimed clue rewards. Use ::cluerewards to collect them.")
        }
        onCommand("cluerewards") {
            desc = "Collect pending Treasure Trail rewards"
            cheat { launcher.launch(player) { show() } }
        }
    }
    private fun ProtectedAccess.show() {
        if (rewards.pending(player).objs.all { it == null }) { mes("You have no unclaimed clue rewards."); return }
        // Open first: closing another reward interface must finish before projecting our loot.
        ifOpenMain("interface.trail_rewardscreen")
        invTransmit(rewards.display(player))
        mes("Close this window to collect your rewards. Any items that do not fit remain available with ::cluerewards.")
    }
}
