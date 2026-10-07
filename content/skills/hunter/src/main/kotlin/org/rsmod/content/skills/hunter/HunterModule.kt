package org.rsmod.content.skills.hunter

import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.plugin.module.PluginModule

class HunterModule : PluginModule() {
    override fun bind() {
        addSetBinding<PlayerDeathCleanupHook>(ClueTrapHunting::class.java)
        addSetBinding<PlayerPostTickHook>(ClueTrapHunting::class.java)
    }
}
