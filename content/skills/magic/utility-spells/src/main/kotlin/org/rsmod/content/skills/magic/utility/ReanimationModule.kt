package org.rsmod.content.skills.magic.utility

import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.plugin.module.PluginModule

class ReanimationModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcAttackValidateHook>(AbyssalReanimationScript::class.java)
        addSetBinding<NpcDeathKillHook>(AbyssalReanimationScript::class.java)
        addSetBinding<PlayerDeathCleanupHook>(AbyssalReanimationScript::class.java)
        addSetBinding<PlayerPostTickHook>(AbyssalReanimationScript::class.java)
    }
}
