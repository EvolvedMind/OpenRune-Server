package org.rsmod.content.other.pets

import dev.openrune.ServerCacheManager
import dev.openrune.types.ItemServerType
import dev.openrune.types.NpcServerType

data class PetType(val item: ItemServerType, val npc: NpcServerType) {
    val pickupOp: Int?
        get() = (1..5).firstOrNull {
            npc.actions.getOpOrNull(it - 1)?.trim().equals("Pick-up", ignoreCase = true)
        }
}

internal class PetCatalog(
    items: Map<String, ItemServerType> = ServerCacheManager.getItems().values.associateBy {
        it.internalName.removePrefix("obj.")
    },
    npcs: Map<String, NpcServerType> = ServerCacheManager.getNpcs().values.associateBy {
        it.internalName.removePrefix("npc.")
    },
    mappings: List<Pair<String, String>> = PET_MAPPINGS,
) {
    private val availableItems = mappings.mapNotNull { (item, npc) ->
        items[item.removePrefix("obj.")]?.let { it to npcs[npc.removePrefix("npc.")] }
    }

    val types: List<PetType> = availableItems.mapNotNull { (item, npc) ->
        if (npc == null || !npc.isFollower || !npc.isInteractable) {
            null
        } else {
            PetType(item, npc).takeIf { it.pickupOp != null }
        }
    }

    private val byItemId = types.associateBy { it.item.id }.also { byId ->
        require(byId.size == types.size) { "Pet catalog contains duplicate item mappings." }
    }

    val unsupportedItems: List<ItemServerType> = availableItems.map { it.first }.filter {
        it.id !in byItemId
    }

    fun find(itemId: Int): PetType? = byItemId[itemId]
}
