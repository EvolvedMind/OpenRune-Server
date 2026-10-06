package org.rsmod.content.bosses.doom

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.instances.InstanceManager
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/** The base map is a template; only an initialized private encounter may be attacked. */
internal class DoomAccessHook @Inject constructor(private val instances: InstanceManager) : NpcAttackValidateHook {
    private val bosses by lazy { setOf(DoomNpcs.BOSS, DoomNpcs.SHIELDED, DoomNpcs.BURROWED).map { it.asRSCM() }.toSet() }
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (npc.type.id !in bosses) return NpcAttackValidateResult.Pass
        val session = instances.sessionForPlayer(player)
        val owner = instances.instanceForNpc(npc)?.let(instances::sessionForId)
        return if (session != null && session.key == "doom_of_mokhaiotl" && owner === session)
            NpcAttackValidateResult.Pass
        else NpcAttackValidateResult.Deny("Enter the Doom arena through the gap to start your run.")
    }
}
