package org.rsmod.content.other.pets.pack

import dev.openrune.pack.NpcCacheContract
import dev.openrune.pack.PluginPack

class PetPluginPack : PluginPack() {
    override fun npcCacheContracts(): List<NpcCacheContract> =
        listOf("npc.skillpet_fish", "npc.skillpet_fish_tempoross").map { npc ->
            NpcCacheContract(
                internalName = npc,
                options = mapOf(1 to "Talk-to", 3 to "Metamorphosis", 4 to "Pick-up"),
                isFollower = true,
                isInteractable = true,
            )
        }
}
