package org.rsmod.content.other.treasure.trails

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.output.mes

internal class TrailTargetKillHook @Inject constructor(
    private val progress: TrailProgress,
    private val targets: TrailTargets,
) : NpcDeathKillHook {
    override fun onKill(context: NpcDeathKillContext) {
        val player = context.hero
        val npc = context.npc
        for (active in targets.active(player)) for (row in active.clue.targets) {
            val fields = progress.catalog.fields(row)
            if (npc.type.id !in fields.ints("npcs") && npc.visType.id !in fields.ints("npcs")) continue
            when (fields.table) {
                "cluehelper_target_key" -> {
                    val description = fields.string("description").lowercase()
                    val anywhere = description.contains("kill a man") || description.contains("any chicken") || description.contains("any hill giant") || description.contains("male barbarian")
                    if (!anywhere && !targets.near(npc.coords, fields.int("key_coord"), 64)) continue
                    val key = fields.int("key")
                    if (player.inv.objs.filterNotNull().any { it.id == key }) continue
                    if (player.invAdd(player.inv, key, 1).success) player.mes("You find ${ServerCacheManager.getItem(key)?.name ?: "a key"} for your clue.")
                    else player.mes("You need an inventory space to take the clue key. Defeat another target after making room.")
                }
                "cluehelper_target_kill" -> {
                    if (!targets.near(npc.coords, fields.int("coord"), 64)) continue
                    if (progress.advance(player, player.inv, active.slot, active.item)) player.mes("Your kill reveals the next part of your Treasure Trail.")
                }
            }
        }
    }
}
