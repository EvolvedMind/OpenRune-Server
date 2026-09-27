package org.rsmod.content.other.pets

import jakarta.inject.Inject
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

class PetAttackHook @Inject constructor(private val pets: PetFollowerManager) : NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult =
        if (pets.isActivePet(npc)) {
            NpcAttackValidateResult.Deny("You can't attack a pet.")
        } else {
            NpcAttackValidateResult.Pass
        }
}
