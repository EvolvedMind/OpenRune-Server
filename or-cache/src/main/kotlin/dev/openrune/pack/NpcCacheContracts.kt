package dev.openrune.pack

import dev.openrune.OsrsCacheProvider
import dev.openrune.codec.osrs.NpcDecoder
import dev.openrune.definition.type.NpcType
import dev.openrune.filesystem.Cache
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import java.nio.file.Path

fun verifyNpcCacheContracts(
    packs: PluginPacks,
    livePath: Path,
    serverPath: Path,
    revision: Int,
) {
    val contracts = packs.active.flatMap { it.npcCacheContracts() }
    if (contracts.isEmpty()) {
        return
    }

    val liveNpcs = mutableMapOf<Int, NpcType>()
    OsrsCacheProvider.NPCDecoder(revision).load(Cache.load(livePath), liveNpcs)

    val serverNpcs = mutableMapOf<Int, NpcServerType>()
    NpcDecoder(revision).load(Cache.load(serverPath), serverNpcs)

    for (contract in contracts) {
        val id = contract.internalName.asRSCM(RSCMType.NPC)
        val live = requireNotNull(liveNpcs[id]) {
            "${contract.internalName} is missing from the LIVE client cache."
        }
        val server = requireNotNull(serverNpcs[id]) {
            "${contract.internalName} is missing from the SERVER cache."
        }
        for ((slot, expected) in contract.options) {
            require(slot in 1..5)
            val liveOption = live.actions.getOpOrNull(slot - 1)
            val serverOption = server.actions.getOpOrNull(slot - 1)
            require(liveOption == expected && serverOption == expected) {
                "${contract.internalName} option$slot should be '$expected' in both caches; " +
                    "LIVE='$liveOption', SERVER='$serverOption'."
            }
        }
        require(
            contract.isFollower == null ||
                (live.isFollower == contract.isFollower && server.isFollower == contract.isFollower),
        ) {
            "${contract.internalName} has mismatched isFollower in LIVE or SERVER cache."
        }
        require(
            contract.isInteractable == null ||
                (live.isInteractable == contract.isInteractable &&
                    server.isInteractable == contract.isInteractable),
        ) {
            "${contract.internalName} has mismatched isInteractable in LIVE or SERVER cache."
        }
    }
    println("Validated ${contracts.size} NPC definitions in LIVE and SERVER caches.")
}
