package org.rsmod.content.other.pets

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

class PetPlacementTest {
    private val playerTile = CoordGrid(4, 4)
    private val collision = CollisionFlagMap(arrayOfNulls(2)).apply {
        allocateIfAbsent(playerTile.x, playerTile.z, playerTile.level)
    }

    @Test
    fun `pet appears on previous player tile when that step is open`() {
        val direction = trailingDirection(CoordGrid(3, 4), playerTile)

        assertEquals(Direction.West, direction)
        assertEquals(CoordGrid(3, 4), trailingPlacement(collision, playerTile, direction!!))
    }

    @Test
    fun `walls and objects override the preferred trailing tile`() {
        collision.add(4, 3, 0, CollisionFlag.WALL_NORTH)

        val besideWall = trailingPlacement(collision, playerTile, Direction.South)

        assertNotEquals(CoordGrid(4, 3), besideWall)
        assertNotEquals(playerTile, besideWall)

        collision.remove(4, 3, 0, CollisionFlag.WALL_NORTH)
        collision.add(4, 3, 0, CollisionFlag.LOC)

        assertNotEquals(CoordGrid(4, 3), trailingPlacement(collision, playerTile, Direction.South))
    }

    @Test
    fun `placement refuses every blocked adjacent tile`() {
        for (direction in Direction.entries) {
            collision.add(4 + direction.xOff, 4 + direction.zOff, 0, CollisionFlag.LOC)
        }

        assertNull(trailingPlacement(collision, playerTile, Direction.South))
    }

    @Test
    fun `placement skips an occupied trailing tile`() {
        collision.add(4, 3, 0, CollisionFlag.BLOCK_NPCS)

        assertNotEquals(CoordGrid(4, 3), trailingPlacement(collision, playerTile, Direction.South))
        assertEquals(
            CoordGrid(4, 3),
            trailingPlacement(
                collision,
                playerTile,
                Direction.South,
                currentPetTile = CoordGrid(4, 3),
            ),
        )
    }

    @Test
    fun `heading is ignored after a teleport or level change`() {
        assertNull(trailingDirection(CoordGrid(1, 4), playerTile))
        assertNull(trailingDirection(CoordGrid(4, 3, 1), playerTile))
    }

    @Test
    fun `placement stays within an allocated zone`() {
        val edge = CoordGrid(7, 4)

        assertNotEquals(CoordGrid(8, 4), trailingPlacement(collision, edge, Direction.East))
    }
}
