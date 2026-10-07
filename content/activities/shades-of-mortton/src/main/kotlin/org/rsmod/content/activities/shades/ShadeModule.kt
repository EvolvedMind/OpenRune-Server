package org.rsmod.content.activities.shades

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.module.PluginModule

class ShadeModule : PluginModule() {
    override fun bind() { addSetBinding<NpcAttackValidateHook>(FiyrShadeAttackHook::class.java) }
}

/** Default combat/death remains in charge; respawn resets the shadow's visual transformation. */
class FiyrShadeAttackHook : NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (npc.type.id == "npc.shadeshadow_level5".asRSCM() && npc.visType.id == npc.type.id) {
            npc.transmog(ServerCacheManager.getNpc("npc.shade_level5".asRSCM())!!, Int.MAX_VALUE)
        }
        return NpcAttackValidateResult.Pass
    }
}
