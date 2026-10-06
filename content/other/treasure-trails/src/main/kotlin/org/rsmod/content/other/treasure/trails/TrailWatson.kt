package org.rsmod.content.other.treasure.trails

import jakarta.inject.Inject
import org.rsmod.api.invtx.*
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class TrailWatson @Inject constructor(private val progress: TrailProgress) : PluginScript() {
    override fun ScriptContext.startup() {
        for (npc in listOf("npc.trail_watson", "npc.trail_watson_pre_talk", "npc.trail_watson_post_talk")) onOpNpc1(npc) {
            vars["varbit.trail_watson_spoken"] = 1
            when (choice3("Give clues and collect a master clue", 1, "Check stored clues", 2, "Never mind", 3)) {
                1 -> {
                    val deposited = deposit(player)
                    if (claim(player)) mes("Watson gives you a master clue scroll.")
                    else mes("Watson accepted $deposited clue(s). Stored: ${stored(player)}. A master clue needs all four tiers, free inventory space and no existing master clue.")
                }
                2 -> mes("Watson has stored: ${stored(player)}.")
            }
        }
    }
    private fun stored(player: Player) = tiers.filter { player.vars[flag(it)] != 0 }.joinToString { it.key }.ifEmpty { "none" }
    fun deposit(player: Player): Int {
        val deposits = tiers.filter { player.vars[flag(it)] == 0 }.mapNotNull { tier ->
            player.inv.objs.withIndex().firstOrNull { (_, item) ->
                item != null && (progress.state(item)?.let { progress.catalog.clues.getValue(it.row).tier }
                    ?: progress.catalog.itemRows[item.id]?.let { progress.catalog.clues.getValue(it).tier }) == tier
            }?.let { tier to it }
        }
        if (deposits.isEmpty()) return 0
        val result = player.invTransaction(player.inv) {
            val inv = select(player.inv)
            for ((_, entry) in deposits) delete(inv, entry.value!!.id, 1, entry.index)
        }
        if (!result.success) return 0
        for ((tier, _) in deposits) VarPlayerIntMapSetter.set(player, flag(tier), 1)
        return deposits.size
    }
    fun claim(player: Player): Boolean {
        if (tiers.any { player.vars[flag(it)] == 0 }) return false
        val master = InvObj("obj.trail_clue_master")
        if (progress.ownsClue(player, TrailTier.MASTER)) return false
        val result = player.invTransaction(player.inv) { add(select(player.inv), master.id, 1) }
        if (!result.success) return false
        for (tier in tiers) VarPlayerIntMapSetter.set(player, flag(tier), 0)
        return true
    }
    companion object {
        val tiers = listOf(TrailTier.EASY, TrailTier.MEDIUM, TrailTier.HARD, TrailTier.ELITE)
        fun flag(tier: TrailTier) = "varbit.trail_watson_${tier.key}"
    }
}
