package org.rsmod.content.other.pets

import kotlin.math.abs
import kotlin.math.sign
import org.rsmod.game.map.Direction
import org.rsmod.game.map.collision.canStep
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

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
    currentPetSize: Int = size,
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
        val extraFlag = if (currentPetTile != null && petOverlapsPlayer(currentPetTile, currentPetSize, adjacent)) {
            0
        } else {
            CollisionFlag.BLOCK_NPCS
        }
        if (
            collision.isZoneValid(adjacent) &&
                collision.canStep(origin, direction, extraFlag = extraFlag) &&
                clearPetFootprint(collision, destination, size, currentPetTile, currentPetSize)
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

internal fun clearPetFootprint(
    collision: CollisionFlagMap,
    anchor: CoordGrid,
    size: Int,
    current: CoordGrid?,
    currentSize: Int = size,
): Boolean {
    if (size < 1 || anchor.x + size > CoordGrid.MAP_WIDTH || anchor.z + size > CoordGrid.MAP_LENGTH) {
        return false
    }
    for (x in anchor.x until anchor.x + size) {
        for (z in anchor.z until anchor.z + size) {
            val tile = CoordGrid(x, z, anchor.level)
            if (!collision.isZoneValid(tile)) {
                return false
            }
            val occupiedByPet = current != null && petOverlapsPlayer(current, currentSize, tile)
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
