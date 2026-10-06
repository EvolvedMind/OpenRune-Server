package org.rsmod.content.bosses.doom

import org.rsmod.api.death.PlayerRespawnHook
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.plugin.module.PluginModule

class DoomModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcAttackValidateHook>(DoomAccessHook::class.java)
        addSetBinding<PlayerRespawnHook>(DoomRespawnHook::class.java)
    }
}
