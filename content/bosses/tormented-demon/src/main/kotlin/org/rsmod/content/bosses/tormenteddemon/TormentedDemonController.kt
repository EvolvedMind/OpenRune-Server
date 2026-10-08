package org.rsmod.content.bosses.tormenteddemon

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.player.isValidTarget
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.module.PluginModule

/** Shared world demons have one combat owner; pseudo-multi never permits pile-ons by other players. */
@Singleton
class TormentedDemonController @Inject constructor() {
    private val owners = linkedMapOf<NpcUid, Pair<Npc, Player>>()
    fun owner(npc: Npc): Player? = owners[npc.uid]?.second?.takeIf { it.isValidTarget() && npc.isWithinDistance(it, 30) }
    fun members(player: Player): List<Npc> = owners.values.filter { (npc, target) ->
        target === player && npc.isSlotAssigned && npc.hitpoints > 0 && target.isValidTarget() && npc.isWithinDistance(target, 30)
    }.map { it.first }
    fun denial(player: Player, npc: Npc): String? {
        if (owner(npc)?.let { it !== player } == true) return "Someone else is already fighting this demon."
        if (members(player).any { it === npc }) return null
        if (members(player).size >= TormentedTemple.limit(player.coords)) return "You can only fight ${TormentedTemple.limit(player.coords)} tormented demons in this area."
        return null
    }
    fun claim(player: Player, npc: Npc): Boolean {
        if (denial(player, npc) != null) return false
        owners[npc.uid] = npc to player
        return true
    }
    fun release(npc: Npc) { owners.remove(npc.uid) }
    fun clear() { owners.clear() }
}

internal object TormentedTemple {
    // Boundaries follow the native chamber/corridor layout. Plinth and NW rooms are singles;
    // central/eastern rooms are doubles; the two SW chambers are triples.
    fun contains(c: CoordGrid): Boolean = c.level == 0 && c.x in 4032..4159 && c.z in 4352..4479
    fun limit(c: CoordGrid): Int = when {
        !contains(c) -> 1
        c.z < 4416 && c.x < 4100 -> 3
        c.x < 4056 -> 1
        c.x >= 4100 && c.z < 4416 -> 1
        else -> 2
    }
}

internal class TormentedDemonModule : PluginModule() {
    override fun bind() { addSetBinding<NpcAttackValidateHook>(TormentedDemonAttackHook::class.java)
        addSetBinding<org.rsmod.api.death.NpcDeathDropHook>(TormentedSmouldering::class.java)
        addSetBinding<org.rsmod.api.player.hook.PlayerPostTickHook>(TormentedSmouldering::class.java)
        addSetBinding<org.rsmod.api.player.hook.PlayerObjTakeValidateHook>(SmoulderingTakeHook::class.java) }
}
internal class TormentedDemonAttackHook @Inject constructor(private val controller: TormentedDemonController) : NpcAttackValidateHook {
    private val demonIds by lazy { setOf("npc.tormented_demon_1".asRSCM(), "npc.tormented_demon_2".asRSCM()) }
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (npc.id !in demonIds) return NpcAttackValidateResult.Pass
        if (!QuestRequirements.hasCompleted(player, "quest.whileguthixsleeps")) return NpcAttackValidateResult.Deny("You must complete While Guthix Sleeps first.")
        controller.denial(player, npc)?.let { return NpcAttackValidateResult.Deny(it) }
        return NpcAttackValidateResult.BypassSingleWayPvnRestriction
    }
}
