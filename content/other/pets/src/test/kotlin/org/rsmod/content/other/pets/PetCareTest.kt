package org.rsmod.content.other.pets

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.other.pets.cats.CatCare
import org.rsmod.content.other.pets.cats.CatColour
import org.rsmod.content.other.pets.cats.CatStage
import org.rsmod.content.other.pets.cats.Cats
import org.rsmod.content.other.pets.cats.catAttention
import org.rsmod.content.other.pets.cats.catGrowthEvents
import org.rsmod.content.other.pets.cats.catGrowthTicks
import org.rsmod.content.other.pets.cats.catHunger
import org.rsmod.content.other.pets.cats.catOriginalColour
import org.rsmod.content.other.pets.dogs.DogCare
import org.rsmod.content.other.pets.dogs.Dogs
import org.rsmod.content.other.pets.dogs.dogFedTicks
import org.rsmod.content.other.pets.dogs.dogGrowthEvents
import org.rsmod.content.other.pets.dogs.dogGrowthTicks
import org.rsmod.routefinder.flag.CollisionFlag

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class PetCareTest {
    @Test
    fun `every kitten colour grows into its matching adult without starving the new cat`() {
        val f = PetTestFixture()
        val care = CatCare(f.followers)
        for (kitten in Cats.all.filter { it.isKitten }) {
            care.resetKitten(f.player)
            assertTrue(f.followers.spawn(f.player, kitten.form), kitten.obj)
            f.player.catGrowthEvents = CatCare.KITTEN_EVENTS - 1
            f.player.catGrowthTicks = CatCare.GROWTH_TICKS - 1
            f.player.catHunger = 1
            f.player.catAttention = 1

            care.tick(f.player)

            assertSame(Cats.of(CatStage.Cat, kitten.colour), care.following(f.player), kitten.obj)
            assertNotNull(f.followers.follower(f.player))
            assertEquals(0, f.player.catGrowthEvents)
            assertEquals(0, f.player.catGrowthTicks)
            f.followers.dismiss(f.player)
        }
    }

    @Test
    fun `kitten feeding and attention restore care meters`() {
        val f = PetTestFixture()
        val care = CatCare(f.followers)
        val kitten = Cats.all.first { it.isKitten }
        care.resetKitten(f.player)
        assertTrue(f.followers.spawn(f.player, kitten.form))
        f.player.catHunger = CatCare.HUNGER_VERY
        f.player.catAttention = CatCare.ATTENTION_LONELY

        care.feed(f.player)
        care.stroke(f.player)
        care.stroke(f.player)
        care.tick(f.player)

        assertEquals(CatCare.HUNGER_FULL - 1, f.player.catHunger)
        assertEquals(CatCare.ATTENTION_FRESH - 1, f.player.catAttention)
        assertSame(kitten, care.following(f.player))
        care.play(f.player)
        assertEquals(CatCare.ATTENTION_PLAYED, f.player.catAttention)
    }

    @Test
    fun `blocked kitten growth preserves the follower and retries when placement becomes available`() {
        val f = PetTestFixture()
        val care = CatCare(f.followers)
        val kitten = Cats.all.first { it.isKitten }
        care.resetKitten(f.player)
        assertTrue(f.followers.spawn(f.player, kitten.form))
        val previous = requireNotNull(f.followers.follower(f.player))
        f.player.catGrowthEvents = CatCare.KITTEN_EVENTS - 1
        f.player.catGrowthTicks = CatCare.GROWTH_TICKS - 1
        blockPlacement(f)

        care.tick(f.player)

        assertSame(previous, f.followers.follower(f.player))
        assertSame(kitten, care.following(f.player))
        assertEquals(CatCare.KITTEN_EVENTS - 1, f.player.catGrowthEvents)
        assertEquals(CatCare.GROWTH_TICKS - 1, f.player.catGrowthTicks)
        assertEquals(CatCare.HUNGER_FULL - 1, f.player.catHunger)
        assertEquals(CatCare.ATTENTION_FRESH - 1, f.player.catAttention)

        unblockPlacement(f)
        care.tick(f.player)

        assertSame(Cats.of(CatStage.Cat, kitten.colour), care.following(f.player))
        assertEquals(0, f.player.catGrowthEvents)
        assertEquals(0, f.player.catGrowthTicks)
    }

    @Test
    fun `blocked adult cat growth preserves age and retries the overgrown form`() {
        val f = PetTestFixture()
        val care = CatCare(f.followers)
        val cat = Cats.of(CatStage.Cat, CatColour.of(2))
        assertTrue(f.followers.spawn(f.player, cat.form))
        val previous = requireNotNull(f.followers.follower(f.player))
        f.player.catGrowthEvents = CatCare.CAT_EVENTS - 1
        f.player.catGrowthTicks = CatCare.GROWTH_TICKS - 1
        blockPlacement(f)

        care.tick(f.player)

        assertSame(previous, f.followers.follower(f.player))
        assertSame(cat, care.following(f.player))
        assertEquals(CatCare.CAT_EVENTS - 1, f.player.catGrowthEvents)
        assertEquals(CatCare.GROWTH_TICKS - 1, f.player.catGrowthTicks)

        unblockPlacement(f)
        care.tick(f.player)

        assertSame(Cats.of(CatStage.Overgrown, cat.colour), care.following(f.player))
        assertEquals(0, f.player.catGrowthEvents)
        assertEquals(0, f.player.catGrowthTicks)
    }

    @Test
    fun `hellcat transformation preserves original colour through blocked transformations`() {
        val f = PetTestFixture()
        val care = CatCare(f.followers)
        val cat = Cats.of(CatStage.Cat, CatColour.of(2))
        assertTrue(f.followers.spawn(f.player, cat.form))
        val previous = requireNotNull(f.followers.follower(f.player))
        f.player.catOriginalColour = 5
        blockPlacement(f)

        assertNull(care.toHell(f.player))
        assertSame(previous, f.followers.follower(f.player))
        assertSame(cat, care.following(f.player))
        assertEquals(5, f.player.catOriginalColour)

        unblockPlacement(f)
        assertNotNull(care.toHell(f.player))
        assertSame(Cats.of(CatStage.Cat, CatColour.Hell), care.following(f.player))
        assertEquals(cat.colour.id, f.player.catOriginalColour)
        val hellcat = requireNotNull(f.followers.follower(f.player))
        blockPlacement(f)

        assertNull(care.fromHell(f.player))
        assertSame(hellcat, f.followers.follower(f.player))
        assertSame(Cats.of(CatStage.Cat, CatColour.Hell), care.following(f.player))

        unblockPlacement(f)
        assertNotNull(care.fromHell(f.player))
        assertSame(cat, care.following(f.player))
    }

    @Test
    fun `feeding resumes hungry puppy growth into the matching breed and colour`() {
        val f = PetTestFixture()
        val care = DogCare(f.followers)
        for (puppy in Dogs.all.filter { it.puppy }) {
            care.resetPuppy(f.player)
            assertTrue(f.followers.spawn(f.player, puppy.form), puppy.obj)
            f.player.dogGrowthEvents = DogCare.PUPPY_EVENTS - 1
            f.player.dogGrowthTicks = DogCare.GROWTH_TICKS - 1
            f.player.dogFedTicks = DogCare.HUNGER_STOP

            care.tick(f.player)

            assertTrue(care.isGrowthPaused(f.player))
            assertSame(puppy, care.following(f.player))
            assertEquals(DogCare.GROWTH_TICKS - 1, f.player.dogGrowthTicks)
            care.feed(f.player)
            assertFalse(care.isGrowthPaused(f.player))
            care.tick(f.player)

            assertSame(Dogs.adult(puppy), care.following(f.player), puppy.obj)
            assertEquals(0, f.player.dogGrowthEvents)
            assertEquals(0, f.player.dogGrowthTicks)
            f.followers.dismiss(f.player)
        }
    }

    @Test
    fun `blocked puppy growth retains age and the same puppy until an adult can be placed`() {
        val f = PetTestFixture()
        val care = DogCare(f.followers)
        val puppy = Dogs.all.first { it.puppy }
        care.resetPuppy(f.player)
        assertTrue(f.followers.spawn(f.player, puppy.form))
        val previous = requireNotNull(f.followers.follower(f.player))
        f.player.dogGrowthEvents = DogCare.PUPPY_EVENTS - 1
        f.player.dogGrowthTicks = DogCare.GROWTH_TICKS - 1
        blockPlacement(f)

        care.tick(f.player)

        assertSame(previous, f.followers.follower(f.player))
        assertSame(puppy, care.following(f.player))
        assertEquals(DogCare.PUPPY_EVENTS - 1, f.player.dogGrowthEvents)
        assertEquals(DogCare.GROWTH_TICKS - 1, f.player.dogGrowthTicks)
        assertEquals(1, f.player.dogFedTicks)

        unblockPlacement(f)
        care.tick(f.player)

        assertSame(Dogs.adult(puppy), care.following(f.player))
        assertEquals(0, f.player.dogGrowthEvents)
        assertEquals(0, f.player.dogGrowthTicks)
    }

    private fun blockPlacement(f: PetTestFixture) {
        f.player.coords = requireNotNull(f.followers.follower(f.player)).coords
        f.player.previousCoords = f.player.coords
        for (x in -8..8) {
            for (z in -8..8) {
                f.collision.add(f.player.coords.x + x, f.player.coords.z + z, f.player.level, CollisionFlag.LOC)
            }
        }
    }

    private fun unblockPlacement(f: PetTestFixture) {
        for (x in -8..8) {
            for (z in -8..8) {
                f.collision.remove(f.player.coords.x + x, f.player.coords.z + z, f.player.level, CollisionFlag.LOC)
            }
        }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadCache() {
            ServerCacheManager.init(240).close()
        }
    }
}
