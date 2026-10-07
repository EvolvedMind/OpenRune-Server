package org.rsmod.content.skills.agility

import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.plugin.module.PluginModule

class AgilityModule : PluginModule() {
    override fun bind() { addSetBinding<PlayerDeathCleanupHook>(ClueAgilityScript::class.java) }
}
