package org.rsmod.content.other.pets

import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.plugin.module.PluginModule

class PetsModule : PluginModule() {
    override fun bind() {
        addSetBinding<PlayerPostTickHook>(PetFollowerScript::class.java)
        addSetBinding<NpcAttackValidateHook>(PetAttackHook::class.java)
    }
}
