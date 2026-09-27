package org.rsmod.content.other.pets

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.abs
import kotlin.math.sign
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.npc.owner.isSpawnOwnedBy
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.npc.isSuccess
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.PathingEntity.Companion.INVALID_SLOT
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.inv.Inventory
import org.rsmod.game.map.Direction
import org.rsmod.game.map.collision.canStep
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.game.movement.RouteRequestCoord
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

internal var Player.activePetItemId by intVarp("varp.active_pet")

enum class PetActionResult {
    Summoned,
    PickedUp,
    Called,
    NoFollower,
    AlreadyFollowing,
    InventoryFull,
    NotYourPet,
    Unavailable,
}

@Singleton
class PetFollowerManager @Inject constructor(
    private val registry: NpcRegistry,
    private val npcs: NpcList,
    private val collision: CollisionFlagMap,
) {
    private val catalog = PetCatalog()
    private val followers = mutableMapOf<PlayerUid, Npc>()
    private val trailingDirections = mutableMapOf<PlayerUid, Direction>()

    init {
        val varp = ServerCacheManager.getVarp("varp.active_pet".asRSCM(RSCMType.VARP))
        require(
            varp != null &&
                varp.scope == VarpLifetime.Perm &&
                varp.transmit == VarpTransmitLevel.Never,
        ) {
            "Pet varp.active_pet is missing or misconfigured in the SERVER cache. Run :or-cache:buildCache."
        }
    }

    val petTypes: List<PetType>
        get() = catalog.types

    val unsupportedBossItems: List<ItemServerType>
        get() = catalog.unsupportedBossItems

    fun summon(player: Player, inventory: Inventory, slot: Int, pet: PetType): PetActionResult {
        if (player.activePetItemId != 0 || followers[player.uid] != null) {
            return PetActionResult.AlreadyFollowing
        }
        if (inventory !== player.inv || inventory[slot]?.id != pet.item.id) {
            return PetActionResult.Unavailable
        }
        if (!collision.isZoneValid(player.coords)) {
            return PetActionResult.Unavailable
        }

        val npc = spawn(player, pet) ?: return PetActionResult.Unavailable
        if (player.invDel(inventory, pet.item.id, 1, slot = slot).failure) {
            remove(npc)
            return PetActionResult.Unavailable
        }
        player.activePetItemId = pet.item.id
        followers[player.uid] = npc
        return PetActionResult.Summoned
    }

    fun pickUp(player: Player, npc: Npc): PetActionResult {
        if (followers[player.uid] !== npc || !npc.isSpawnOwnedBy(player) || !registered(npc)) {
            return PetActionResult.NotYourPet
        }
        if (player.inv.isFull()) {
            return PetActionResult.InventoryFull
        }

        val pet = catalog.find(player.activePetItemId) ?: return PetActionResult.Unavailable
        if (!remove(npc)) {
            return PetActionResult.Unavailable
        }
        followers.remove(player.uid)
        if (player.invAdd(player.inv, pet.item.id, 1).failure) {
            spawn(player, pet)?.let { followers[player.uid] = it }
            return PetActionResult.Unavailable
        }
        player.activePetItemId = 0
        return PetActionResult.PickedUp
    }

    fun call(player: Player): PetActionResult {
        val pet = followers[player.uid]
        if (player.activePetItemId == 0) {
            return PetActionResult.NoFollower
        }
        if (!collision.isZoneValid(player.coords)) {
            return PetActionResult.Unavailable
        }
        if (pet == null || !registered(pet)) {
            followers.remove(player.uid)
            return if (restore(player)) PetActionResult.Called else PetActionResult.Unavailable
        }
        pet.teleport(collision, placement(player, pet.size, pet.coords))
        pet.facePlayer(player)
        pet.mode = NpcMode.None
        return PetActionResult.Called
    }

    fun transform(player: Player, npc: Npc, nextItem: String): Boolean {
        if (!owns(player, npc)) {
            return false
        }
        val replacement = catalog.find(nextItem.asRSCM(RSCMType.OBJ)) ?: return false
        val previous = catalog.find(player.activePetItemId) ?: return false
        if (!remove(npc)) {
            return false
        }
        followers.remove(player.uid)
        val newNpc = spawn(player, replacement)
        if (newNpc == null) {
            spawn(player, previous)?.let { followers[player.uid] = it }
            return false
        }
        player.activePetItemId = replacement.item.id
        followers[player.uid] = newNpc
        return true
    }

    fun tick(player: Player) {
        if (!player.canProcess) {
            return
        }
        val current = followers[player.uid]
        if (player.activePetItemId == 0) {
            if (current != null) {
                remove(current)
                followers.remove(player.uid)
            }
            trailingDirections.remove(player.uid)
            return
        }
        updateTrailingDirection(player)
        if (current == null || !registered(current)) {
            followers.remove(player.uid)
            restore(player)
            return
        }
        current.mode = NpcMode.None
        val trailingTile =
            trailingPlacement(
                collision,
                player.coords,
                trailingDirections[player.uid] ?: Direction.South,
                current.size,
                current.coords,
            )
        when {
            trailingTile == null -> current.abortRoute()
            current.level != player.level ||
                current.coords.chebyshevDistance(player.coords) > MAX_FOLLOW_DISTANCE ->
                current.teleport(collision, trailingTile)
            current.coords == player.coords -> current.teleport(collision, trailingTile)
            current.coords == trailingTile -> current.abortRoute()
            else -> current.routeRequest = RouteRequestCoord(trailingTile)
        }
        current.facePlayer(player)
    }

    fun logout(player: Player) {
        followers.remove(player.uid)?.let(::remove)
        trailingDirections.remove(player.uid)
    }

    fun owns(player: Player, npc: Npc): Boolean =
        followers[player.uid] === npc && npc.isSpawnOwnedBy(player) && registered(npc)

    fun isActivePet(npc: Npc): Boolean = followers.values.any { it === npc }

    private fun restore(player: Player): Boolean {
        val pet = catalog.find(player.activePetItemId) ?: return false
        if (!collision.isZoneValid(player.coords)) {
            return false
        }
        val npc = spawn(player, pet) ?: return false
        followers[player.uid] = npc
        return true
    }

    private fun spawn(player: Player, pet: PetType): Npc? {
        val npc = Npc(pet.npc, placement(player, pet.npc.size))
        npc.respawns = false
        npc.setHunt(0)
        npc.spawnOwner = player.uid
        if (npc.moveRestrict == MoveRestrict.PassThru) {
            npc.moveRestrict = MoveRestrict.Normal
        }
        npc.mode = NpcMode.None
        if (!registry.add(npc).isSuccess()) {
            return null
        }
        npc.facePlayer(player)
        return npc
    }

    private fun registered(npc: Npc): Boolean =
        npc.slotId != INVALID_SLOT && npcs[npc.slotId] === npc

    private fun placement(player: Player, size: Int, currentPetTile: CoordGrid? = null): CoordGrid {
        updateTrailingDirection(player)
        return trailingPlacement(
            collision,
            player.coords,
            trailingDirections[player.uid] ?: Direction.South,
            size,
            currentPetTile,
        ) ?: player.coords
    }

    private fun updateTrailingDirection(player: Player) {
        trailingDirection(player.previousCoords, player.coords)?.let {
            trailingDirections[player.uid] = it
        }
    }

    private fun remove(npc: Npc): Boolean =
        !registered(npc) || registry.del(npc).isSuccess()

    private companion object {
        const val MAX_FOLLOW_DISTANCE = 15
    }
}

internal fun trailingDirection(previous: CoordGrid, current: CoordGrid): Direction? {
    if (previous.level != current.level || previous.chebyshevDistance(current) !in 1..2) {
        return null
    }
    val behindX = (previous.x - current.x).sign
    val behindZ = (previous.z - current.z).sign
    return Direction.entries.first { it.xOff == behindX && it.zOff == behindZ }
}

internal fun trailingPlacement(
    collision: CollisionFlagMap,
    origin: CoordGrid,
    preferred: Direction,
    size: Int = 1,
    currentPetTile: CoordGrid? = null,
): CoordGrid? =
    Direction.entries.sortedBy { direction ->
        val difference = abs(direction.angle - preferred.angle)
        minOf(difference, 2048 - difference)
    }.firstNotNullOfOrNull { direction ->
        val destination = origin.translate(direction.xOff, direction.zOff)
        if (
            collision.isZoneValid(destination) &&
                collision.canStep(
                    origin,
                    direction,
                    size,
                    extraFlag = if (destination == currentPetTile) 0 else CollisionFlag.BLOCK_NPCS,
                )
        ) {
            destination
        } else {
            null
        }
    }
