package org.rsmod.content.other.pets

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.npc.owner.clearSpawnOwner
import org.rsmod.api.npc.owner.isSpawnOwnedBy
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.route.StepFactory
import org.rsmod.content.other.pets.cats.Cats
import org.rsmod.content.other.pets.dogs.Dogs
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.map.Direction
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

@Singleton
class PetFollowers
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val npcList: NpcList,
    private val mapClock: MapClock,
    private val collision: CollisionFlagMap,
    private val steps: StepFactory,
) {
    fun hasFollower(player: Player): Boolean {
        migrateLegacyFollower(player)
        return player.followerObj != 0
    }

    internal fun migrateLegacyFollower(player: Player) {
        val legacy = player.activePetItemId
        if (legacy == 0 || Pets.formForObj(legacy) == null) {
            return
        }
        if (player.followerObj == 0) {
            player.followerObj = legacy
        }
        if (player.followerObj == legacy) {
            player.activePetItemId = 0
        }
    }

    fun followerForm(player: Player): PetForm? =
        Pets.formForObj(player.followerObj)
            ?: Cats.formForObj(player.followerObj)
            ?: Dogs.formForObj(player.followerObj)

    fun follower(player: Player): Npc? {
        val packed = player.followerNpc
        if (packed == NO_FOLLOWER) {
            return null
        }
        val npc = NpcUid(packed).resolve(npcList)
        if (npc == null || !npc.isSpawnOwnedBy(player)) {
            player.followerNpc = NO_FOLLOWER
            return null
        }
        return npc
    }

    fun isFollowerOf(npc: Npc, player: Player): Boolean = follower(player) === npc

    /** Guards an op against another player's follower, sending the refusal message when it is. */
    fun requireOwned(access: ProtectedAccess, npc: Npc): Boolean {
        if (isFollowerOf(npc, access.player)) {
            return true
        }
        access.mes("That's not your pet.")
        return false
    }

    fun spawn(player: Player, form: PetForm): Boolean {
        val previous = follower(player)
        val type = requireNotNull(ServerCacheManager.getNpc(form.npc.asRSCM(RSCMType.NPC))) {
            "NPC type '${form.npc}' not found in cache"
        }
        val coords =
            previous?.coords?.takeIf {
                !petOverlapsPlayer(it, type.size, player.coords) &&
                    clearPetFootprint(collision, it, type.size, it, previous?.size ?: type.size)
            }
                ?: besideTile(player, type.size, previous?.coords, previous?.size ?: type.size)
                ?: return false
        val facedNpc = previous?.facingTarget(npcList)
        val wasFacingPet = previous != null && player.isFacingNpc(previous)
        despawn(player)
        val npc = Npc(type, coords)
        npc.mode = NpcMode.None
        if (npc.moveRestrict == MoveRestrict.PassThru) {
            npc.moveRestrict = MoveRestrict.Normal
        }
        npc.defaultMoveSpeed = if (form.runs) MoveSpeed.Run else MoveSpeed.Walk
        npcRepo.add(npc, Int.MAX_VALUE)
        npc.respawns = false
        npc.assignSpawnOwner(player, mapClock.cycle)
        // Metamorphosis replaces the npc, so the new one takes over the old one's tile and facing
        // rather than reappearing beside the owner pointing somewhere else.
        if (facedNpc != null) {
            npc.faceNpc(facedNpc)
        } else {
            npc.facePlayer(player)
        }
        // Replacing the npc drops whatever the owner was facing, so hand the face target over to
        // the new one; otherwise metamorphosing mid-interaction snaps the owner back to a default
        // angle.
        if (wasFacingPet) {
            player.faceNpc(npc)
        }
        player.followerNpc = npc.uid.packed
        player.followerObj = form.objId
        return true
    }

    private fun Player.isFacingNpc(npc: Npc): Boolean = faceEntity.isNpc && faceEntity.npcSlot == npc.slotId

    fun dismiss(player: Player): PetForm? {
        val form = followerForm(player)
        despawn(player)
        player.followerObj = 0
        return form
    }

    fun call(player: Player): Boolean {
        if (!hasFollower(player)) {
            return false
        }
        val npc = follower(player)
        if (npc == null) {
            followerForm(player)?.let { spawn(player, it) }
            return true
        }
        val destination = besideTile(player, npc.size, npc.coords)
        if (destination == null) {
            if (petOverlapsPlayer(npc.coords, npc.size, player.coords)) {
                despawn(player)
            }
            return true
        }
        npc.teleport(collision, destination)
        npc.facePlayer(player)
        return true
    }

    fun onLogout(player: Player) {
        despawn(player)
    }

    fun onPostTick(player: Player) {
        if (player.loggingOut) {
            return
        }
        migrateLegacyFollower(player)
        if (player.followerObj == 0) {
            return
        }
        trailingDirection(player.previousCoords, player.coords)?.let {
            player.followerDirection = it.ordinal + 1
        }
        val npc = follower(player)
        if (npc != null) {
            maintain(player, npc)
            return
        }
        val form = followerForm(player)
        if (form == null) {
            player.followerObj = 0
            return
        }
        spawn(player, form)
    }

    /** Parks the pet so a script can walk it somewhere; cleared once that route ends. */
    fun markBusy(player: Player) {
        player.followerBusy = true
    }

    fun clearBusy(player: Player) {
        player.followerBusy = false
    }

    /**
     * Pets are driven here rather than through [NpcMode.PlayerFollow]. That mode paths a pet the way
     * a monster approaches its target, which lets it cut corners, shuffle in place once adjacent,
     * and teleport onto the owner's own tile. The mode is pinned to [NpcMode.None] every cycle
     * because anything that resets it would otherwise fall back to the npc type's default of
     * wandering home to its spawn tile.
     */
    private fun maintain(player: Player, npc: Npc) {
        npc.mode = NpcMode.None
        if (
            petOverlapsPlayer(npc.coords, npc.size, player.coords) ||
                (npc.pendingStepCount > 1 && petOverlapsPlayer(npc.lastProcessedCoord, npc.size, player.coords))
        ) {
            val destination = besideTile(player, npc.size, npc.coords)
            if (destination == null) {
                despawn(player)
            } else {
                npc.teleport(collision, destination)
            }
            return
        }
        if (player.followerBusy) {
            // Leave the face target alone: the script that parked the pet owns what it looks at.
            releaseWhenRouteEnds(player, npc)
            return
        }
        npc.facePlayer(player)
        follow(player, npc)
        if (follower(player) === npc) {
            unstick(player, npc)
        }
    }

    private fun releaseWhenRouteEnds(player: Player, npc: Npc) {
        if (npc.routeDestination.isEmpty() && !npc.hasMovedThisCycle) {
            player.followerBusy = false
        }
    }

    /**
     * Routes to the last trailing direction, using a nearby clear footprint when terrain blocks it.
     * An overlapping follower is removed until an adjacent footprint becomes available.
     */
    private fun follow(player: Player, npc: Npc) {
        npc.abortRoute()
        val sameLevel = npc.coords.level == player.coords.level
        val distance = if (sameLevel) npc.coords.chebyshevDistance(player.coords) else Int.MAX_VALUE
        if (distance > TELEPORT_DISTANCE) {
            warpBeside(player, npc)
            return
        }
        val destination = besideTile(player, npc.size, npc.coords)
        if (destination == null) {
            return
        }
        if (destination == npc.coords) {
            return
        }
        val waypoints = safeSteps(player, npc, destination)
        if (waypoints.isNotEmpty()) {
            npc.walk(waypoints)
        }
    }

    private fun safeSteps(player: Player, npc: Npc, destination: CoordGrid): List<CoordGrid> {
        val waypoints = mutableListOf<CoordGrid>()
        var current = npc.coords
        repeat(npc.defaultMoveSpeed.steps.coerceIn(1, 2)) {
            if (current == destination) {
                return waypoints
            }
            val step = steps.validated(
                source = current,
                dest = destination,
                size = npc.size,
                extraFlag = CollisionFlag.BLOCK_NPCS or CollisionFlag.BLOCK_PLAYERS,
            )
            if (
                step == CoordGrid.NULL ||
                    petOverlapsPlayer(step, npc.size, player.coords) ||
                    !clearPetFootprint(collision, step, npc.size, npc.coords)
            ) {
                return waypoints
            }
            waypoints += step
            current = step
        }
        return waypoints
    }

    private fun warpBeside(player: Player, npc: Npc) {
        val destination = besideTile(player, npc.size, npc.coords) ?: return
        npc.abortRoute()
        npc.teleport(collision, destination)
        npc.facePlayer(player)
        player.followerStuck = 0
    }

    private fun besideTile(
        player: Player,
        size: Int,
        current: CoordGrid? = null,
        currentSize: Int = size,
    ): CoordGrid? {
        val direction = trailingDirection(player.previousCoords, player.coords)
            ?: Direction.entries.getOrNull(player.followerDirection - 1)
            ?: Direction.South
        return trailingPlacement(collision, player.coords, direction, size, current, currentSize)
    }

    private fun unstick(player: Player, npc: Npc) {
        val destination = besideTile(player, npc.size, npc.coords)
        if (destination == npc.coords || npc.hasMovedThisCycle) {
            player.followerStuck = 0
            return
        }
        val stuck = player.followerStuck + 1
        if (stuck < STUCK_TELEPORT_CYCLES) {
            player.followerStuck = stuck
            return
        }
        player.followerStuck = 0
        if (destination == null) {
            return
        }
        npc.teleport(collision, destination)
        npc.facePlayer(player)
    }

    private fun despawn(player: Player) {
        player.followerStuck = 0
        player.followerBusy = false
        val npc = follower(player) ?: return
        player.followerNpc = NO_FOLLOWER
        npc.clearSpawnOwner()
        npcRepo.del(npc, Int.MAX_VALUE)
    }

    private companion object {
        const val STUCK_TELEPORT_CYCLES = 5
        const val TELEPORT_DISTANCE = 12
        const val NO_FOLLOWER = 0
    }
}

/**
 * Official varp the client reads to render follower-only right-click options. Holds the npc uid,
 * which packs as `(type shl 16) or slot` — exactly what the client expects.
 */
private var Player.followerNpc: Int by intVarp("varp.follower_npc")
private var Player.followerStuck: Int by intVarBit("varbit.pet_follower_stuck")
private var Player.followerBusy: Boolean by boolVarBit("varbit.pet_follower_busy")
private var Player.followerDirection: Int by intVarBit("varbit.pet_follower_direction")
