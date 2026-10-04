package org.rsmod.content.other.treasure.trails

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.IdentityHashMap
import org.rsmod.api.death.*
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.output.mes
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.*
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.plugin.module.PluginModule
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class TrailModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcAttackValidateHook>(TrailGuardAttackHook::class.java)
        addSetBinding<NpcDeathKillHook>(TrailGuardKillHook::class.java)
        addSetBinding<NpcDeathKillHook>(TrailTargetKillHook::class.java)
        addSetBinding<PlayerDeathCleanupHook>(TrailGuardDeathHook::class.java)
    }
}
internal class TrailGuardAttackHook @Inject constructor(private val guards: TrailGuards) : NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        val owner = guards.owner(npc) ?: return NpcAttackValidateResult.Pass
        return if (owner === player) NpcAttackValidateResult.BypassSingleWayPvnRestriction else NpcAttackValidateResult.Deny("This creature is protecting someone else's clue.")
    }
}
internal class TrailGuardKillHook @Inject constructor(private val guards: TrailGuards) : NpcDeathKillHook {
    override fun onKill(context: NpcDeathKillContext) { guards.killed(context.hero, context.npc) }
}
internal class TrailGuardDeathHook @Inject constructor(private val guards: TrailGuards) : PlayerDeathCleanupHook {
    override fun cleanup(player: Player) { guards.clear(player) }
}

@Singleton
internal class TrailGuards @Inject constructor(
    private val progress: TrailProgress,
    private val repo: NpcRepository,
    private val interactions: AiPlayerInteractions,
    private val random: GameRandom,
) : PluginScript() {
    private data class Guard(val player: Player, val row: Int, val total: Int, val completed: Int)
    private val guards = IdentityHashMap<Npc, Guard>()
    override fun ScriptContext.startup() {
        onPlayerLogout { clear(player) }
        onEvent<NpcStateEvents.Delete> { guards.remove(npc) }
    }
    fun owner(npc: Npc): Player? = guards[npc]?.player
    fun requireFight(player: Player, active: ActiveTrail): Boolean {
        val encounters = active.clue.fields.ints("combat_encounter")
        if (encounters.isEmpty() || active.state.phase == 5) return false
        if (guards.values.any { it.player === player && it.row == active.state.row && it.completed == active.state.completed }) return true
        val encounter = progress.catalog.fields(encounters[random.of(encounters.size)])
        if (!progress.phase(player, active.slot, active.item, 4)) return true
        val owner = Guard(player, active.state.row, active.state.total, active.state.completed)
        encounter.ints("npcs").forEachIndexed { index, id ->
            val npc = Npc(checkNotNull(ServerCacheManager.getNpc(id)), player.coords.translate(index % 2 + 1, index / 2))
            guards[npc] = owner
            repo.add(npc, 1000)
            npc.apPlayer2(player, interactions)
        }
        player.mes("A guardian appears to protect the treasure!")
        return true
    }
    fun killed(player: Player, npc: Npc) {
        val owner = guards.remove(npc) ?: return
        if (owner.player !== player || guards.values.any { it == owner }) return
        val slot = player.inv.objs.indexOfFirst { item ->
            item != null && progress.state(item)?.let { it.row == owner.row && it.total == owner.total && it.completed == owner.completed && it.phase == 4 } == true
        }
        if (slot >= 0 && progress.phase(player, slot, player.inv[slot]!!, 5)) player.mes("The guardian has been defeated. You can now continue your clue.")
    }
    fun clear(player: Player) {
        val npcs = guards.entries.filter { it.value.player === player }.map { it.key }
        for (npc in npcs) { guards.remove(npc); repo.del(npc, Int.MAX_VALUE) }
    }
    override fun ScriptContext.shutdown() { guards.values.map { it.player }.distinct().forEach(::clear) }
}
