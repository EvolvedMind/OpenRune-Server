package org.rsmod.content.other.pets

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.NpcServerType

data class PetType(val item: ItemServerType, val npc: NpcServerType) {
    val pickupOp: Int?
        get() = (1..5).firstOrNull { npc.actions.getOpOrNull(it - 1).isPickup() }
}

internal class PetCatalog {
    private val bossItems = BOSS_PETS.mapNotNull { item ->
        ServerCacheManager.getItem(item.asRSCM(RSCMType.OBJ))
    }

    val types: List<PetType> = buildList {
        for ((item, npc) in EXPLICIT_PETS) {
            val itemType = requireNotNull(ServerCacheManager.getItem(item.asRSCM(RSCMType.OBJ))) {
                "Pet item is missing from the server cache: $item"
            }
            val npcType = requireNotNull(ServerCacheManager.getNpc(npc.asRSCM(RSCMType.NPC))) {
                "Pet NPC is missing from the server cache: $npc"
            }
            add(PetType(itemType, npcType))
        }

        val followerNpcs = ServerCacheManager.getNpcs().values
        for (itemType in bossItems) {
            val npcType = uniqueFollowerFor(itemType, followerNpcs) ?: continue
            add(PetType(itemType, npcType))
        }
    }

    private val byItemId = types.associateBy { it.item.id }
    val unsupportedBossItems: List<ItemServerType> = bossItems.filter { it.id !in byItemId }

    fun find(itemId: Int): PetType? = byItemId[itemId]

    private companion object {
        val EXPLICIT_PETS = listOf(
            "obj.skillpetfish" to "npc.skillpet_fish",
            "obj.skillpetfish_tempoross" to "npc.skillpet_fish_tempoross",
        )

        val BOSS_PETS = listOf(
            "obj.amoxliatlpet",
            "obj.araxxorpet",
            "obj.armadylpet",
            "obj.bandospet",
            "obj.callisto_pet",
            "obj.chaoselepet",
            "obj.chompybird_pet",
            "obj.corepet",
            "obj.cowbosspet",
            "obj.dompet",
            "obj.dukesucelluspet",
            "obj.gryphonbosspet",
            "obj.hell_pet",
            "obj.hueypet",
            "obj.hydrapet",
            "obj.jad_pet",
            "obj.kbdpet",
            "obj.kqpet_walking",
            "obj.krakenpet",
            "obj.leviathanpet",
            "obj.molepet",
            "obj.muspahpet",
            "obj.nexpet",
            "obj.nightmarepet",
            "obj.primepet",
            "obj.rexpet",
            "obj.rtbrandapet",
            "obj.sarachnispet",
            "obj.saradominpet",
            "obj.scorpia_pet",
            "obj.scurriuspet",
            "obj.skillpetfarming",
            "obj.skotizopet",
            "obj.smokepet",
            "obj.snakepet",
            "obj.supremepet",
            "obj.vardorvispet",
            "obj.venenatis_pet",
            "obj.vetion_pet",
            "obj.vorkathpet",
            "obj.whispererpet",
            "obj.yamapet",
            "obj.zalcanopet",
            "obj.zamorakpet",
        )
    }
}

internal fun uniqueFollowerFor(item: ItemServerType, npcs: Collection<NpcServerType>): NpcServerType? {
    val matches = npcs.asSequence().filter { npc ->
        npc.isFollower && npc.name.trim().equals(item.name.trim(), ignoreCase = true) &&
            (1..5).any { npc.actions.getOpOrNull(it - 1).isPickup() }
    }.take(2).toList()
    return matches.singleOrNull()
}

private fun String?.isPickup(): Boolean = this?.trim().equals("Pick-up", ignoreCase = true)
