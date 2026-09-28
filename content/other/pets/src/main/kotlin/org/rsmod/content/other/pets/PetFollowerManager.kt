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
private var Player.followerNpcUid by intVarp("varp.follower_npc")

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
        val followerNpc = ServerCacheManager.getVarp("varp.follower_npc".asRSCM(RSCMType.VARP))
        require(followerNpc != null && followerNpc.transmit != VarpTransmitLevel.Never) {
            "The client follower varp.follower_npc is missing or not transmitted. Run :or-cache:buildCache."
        }
    }

    val petTypes: List<PetType>
        get() = catalog.types

    val unsupportedPetItems: List<ItemServerType>
        get() = catalog.unsupportedItems

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
        track(player, npc)
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
        untrack(player)
        if (player.invAdd(player.inv, pet.item.id, 1).failure) {
            spawn(player, pet)?.let { track(player, it) }
            return PetActionResult.Unavailable
        }
        player.activePetItemId = 0
        return PetActionResult.PickedUp
    }

    fun call(player: Player): PetActionResult {
        val pet = followers[player.uid]
        if (player.activePetItemId == 0) {
            untrack(player)?.let(::remove)
            return PetActionResult.NoFollower
        }
        if (!collision.isZoneValid(player.coords)) {
            return PetActionResult.Unavailable
        }
        if (pet == null || !registered(pet)) {
            untrack(player)
            return if (restore(player)) PetActionResult.Called else PetActionResult.Unavailable
        }
        val destination = placement(player, pet.size, pet.coords)
            ?: return PetActionResult.Unavailable
        pet.teleport(collision, destination)
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
        untrack(player)
        val newNpc = spawn(player, replacement)
        if (newNpc == null) {
            spawn(player, previous)?.let { track(player, it) }
            return false
        }
        player.activePetItemId = replacement.item.id
        track(player, newNpc)
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
            }
            untrack(player)
            trailingDirections.remove(player.uid)
            return
        }
        updateTrailingDirection(player)
        if (current == null || !registered(current)) {
            untrack(player)
            restore(player)
            return
        }
        if (player.followerNpcUid != current.uid.packed) {
            player.followerNpcUid = current.uid.packed
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
            trailingTile == null -> {
                current.abortRoute()
                if (petOverlapsPlayer(current.coords, current.size, player.coords) && remove(current)) {
                    untrack(player)
                    return
                }
            }
            current.level != player.level ||
                current.coords.chebyshevDistance(player.coords) > MAX_FOLLOW_DISTANCE ->
                current.teleport(collision, trailingTile)
            petOverlapsPlayer(current.coords, current.size, player.coords) -> current.teleport(collision, trailingTile)
            current.coords == trailingTile -> current.abortRoute()
            else -> current.routeRequest = RouteRequestCoord(trailingTile)
        }
        current.facePlayer(player)
    }

    fun logout(player: Player) {
        untrack(player)?.let(::remove)
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
        track(player, npc)
        return true
    }

    private fun track(player: Player, npc: Npc) {
        followers[player.uid] = npc
        player.followerNpcUid = npc.uid.packed
    }

    private fun untrack(player: Player): Npc? {
        val npc = followers.remove(player.uid)
        if (player.followerNpcUid != 0) {
            player.followerNpcUid = 0
        }
        return npc
    }

    private fun spawn(player: Player, pet: PetType): Npc? {
        val destination = placement(player, pet.npc.size) ?: return null
        val npc = Npc(pet.npc, destination)
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

    private fun placement(player: Player, size: Int, currentPetTile: CoordGrid? = null): CoordGrid? {
        updateTrailingDirection(player)
        return trailingPlacement(
            collision,
            player.coords,
            trailingDirections[player.uid] ?: Direction.South,
            size,
            currentPetTile,
        )
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
        val xOffset = if (direction.xOff < 0) -size else direction.xOff
        val zOffset = if (direction.zOff < 0) -size else direction.zOff
        val x = origin.x + xOffset
        val z = origin.z + zOffset
        if (size < 1 || x < 0 || z < 0 || x + size > CoordGrid.MAP_WIDTH || z + size > CoordGrid.MAP_LENGTH) {
            return@firstNotNullOfOrNull null
        }
        val destination = CoordGrid(x, z, origin.level)
        val adjacent = origin.translate(direction.xOff, direction.zOff)
        val extraFlag = if (currentPetTile != null && petOverlapsPlayer(currentPetTile, size, adjacent)) {
            0
        } else {
            CollisionFlag.BLOCK_NPCS
        }
        if (
            collision.isZoneValid(adjacent) &&
                collision.canStep(origin, direction, extraFlag = extraFlag) &&
                clearPetFootprint(collision, destination, size, currentPetTile)
        ) {
            destination
        } else {
            null
        }
    }

internal fun petOverlapsPlayer(anchor: CoordGrid, size: Int, player: CoordGrid): Boolean =
    anchor.level == player.level &&
        player.x in anchor.x until anchor.x + size &&
        player.z in anchor.z until anchor.z + size

private fun clearPetFootprint(
    collision: CollisionFlagMap,
    anchor: CoordGrid,
    size: Int,
    current: CoordGrid?,
): Boolean {
    for (x in anchor.x until anchor.x + size) {
        for (z in anchor.z until anchor.z + size) {
            val tile = CoordGrid(x, z, anchor.level)
            if (!collision.isZoneValid(tile)) {
                return false
            }
            val occupiedByPet = current != null && petOverlapsPlayer(current, size, tile)
            val mask = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC or CollisionFlag.GROUND_DECOR or
                CollisionFlag.BLOCK_PLAYERS or (if (occupiedByPet) 0 else CollisionFlag.BLOCK_NPCS)
            if (collision[x, z, anchor.level] and mask != 0) {
                return false
            }
            if (x + 1 < anchor.x + size && !collision.canStep(tile, Direction.East)) {
                return false
            }
            if (z + 1 < anchor.z + size && !collision.canStep(tile, Direction.North)) {
                return false
            }
        }
    }
    return true
}
