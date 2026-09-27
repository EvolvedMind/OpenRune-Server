package org.rsmod.content.other.pets

import jakarta.inject.Inject
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onOpHeld5
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc2
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onOpNpc5
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PetFollowerScript @Inject constructor(
    private val pets: PetFollowerManager,
) : PluginScript(), PlayerPostTickHook {
    override fun ScriptContext.startup() {
        for (pet in pets.petTypes) {
            onOpHeld5(pet.item) {
                player.mes(pets.summon(player, it.inventory, it.slot, pet).message)
            }
        }

        for (item in pets.unsupportedBossItems) {
            onOpHeld5(item) { player.mes("This pet cannot follow you yet.") }
        }

        for (pet in pets.petTypes.distinctBy { it.npc.id }) {
            val npc = pet.npc.internalName
            when (pet.pickupOp) {
                1 -> onOpNpc1(npc) { player.mes(pets.pickUp(player, it.npc).message) }
                2 -> onOpNpc2(npc) { player.mes(pets.pickUp(player, it.npc).message) }
                3 -> onOpNpc3(npc) { player.mes(pets.pickUp(player, it.npc).message) }
                4 -> onOpNpc4(npc) { player.mes(pets.pickUp(player, it.npc).message) }
                5 -> onOpNpc5(npc) { player.mes(pets.pickUp(player, it.npc).message) }
            }
        }

        onIfOverlayButton("component.wornitems:call_follower") {
            player.mes(pets.call(player).message)
        }
        onPlayerLogout { pets.logout(player) }
    }

    override fun onPostTick(player: Player) {
        pets.tick(player)
    }

    private val PetActionResult.message: String
        get() = when (this) {
            PetActionResult.Summoned -> "Your pet starts following you."
            PetActionResult.PickedUp -> "You pick up your pet."
            PetActionResult.Called -> "Your pet comes to you."
            PetActionResult.NoFollower -> "You do not have a follower."
            PetActionResult.AlreadyFollowing -> "You already have a pet following you."
            PetActionResult.InventoryFull -> "You need a free inventory slot to pick up your pet."
            PetActionResult.NotYourPet -> "That is not your pet."
            PetActionResult.Unavailable -> "Your pet cannot be moved right now."
        }
}
