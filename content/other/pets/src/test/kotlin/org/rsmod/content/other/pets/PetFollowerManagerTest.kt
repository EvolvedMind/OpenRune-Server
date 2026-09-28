package org.rsmod.content.other.pets

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.npc.isSuccess
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class PetFollowerManagerTest {
    @Test
    fun `every available canonical pet has a usable follower and can be summoned and picked up`() {
        val f = Fixture()
        assertTrue(f.manager.unsupportedPetItems.isEmpty(), "Known pets must have an interactable follower with Pick-up.")
        for (symbol in listOf(
            "obj.skillpetwc",
            "obj.skillpetmining",
            "obj.skillpetagility",
            "obj.skillpethunter_grey",
            "obj.skillpetthieving",
            "obj.skillpetrunecrafting_fire",
            "obj.skillpetfarming",
            "obj.kbdpet",
            "obj.hell_pet",
            "obj.bloodhound_pet",
        )) {
            assertTrue(f.manager.petTypes.any { it.item.id == symbol.asRSCM(RSCMType.OBJ) }, "Missing $symbol")
        }
        for (pet in f.manager.petTypes) {
            f.player.inv[0] = InvObj(pet.item)
            assertEquals(PetActionResult.Summoned, f.manager.summon(f.player, f.player.inv, 0, pet), pet.item.name)
            val follower = f.singleFollower()
            f.assertFollowerUid(follower)
            assertTrue(!petOverlapsPlayer(follower.coords, follower.size, f.player.coords), pet.item.name)
            assertEquals(pet.npc.id, follower.type.id)
            assertEquals(PetActionResult.PickedUp, f.manager.pickUp(f.player, follower), pet.item.name)
            assertEquals(pet.item.id, f.player.inv[0]?.id)
            assertEquals(0, f.followerUid())
            assertEquals(0, f.player.activePetItemId)
        }
    }

    @Test
    fun `summon transform and pickup keep follower uid and inventory in sync`() {
        val f = Fixture()
        val fish = f.fish
        val tempoross = f.tempoross
        f.player.inv[0] = InvObj(fish.item)

        assertEquals(PetActionResult.Summoned, f.manager.summon(f.player, f.player.inv, 0, fish))
        val first = f.singleFollower()
        val firstUid = f.assertFollowerUid(first)
        assertEquals(fish.item.id, f.player.activePetItemId)
        assertNull(f.player.inv[0])

        assertTrue(f.manager.transform(f.player, first, "obj.skillpetfish_tempoross"))
        val transformed = f.singleFollower()
        val transformedUid = f.assertFollowerUid(transformed)
        assertNotEquals(firstUid, transformedUid)
        assertNull(NpcUid(firstUid).resolve(f.npcs))
        assertEquals(tempoross.item.id, f.player.activePetItemId)

        assertEquals(PetActionResult.PickedUp, f.manager.pickUp(f.player, transformed))
        assertEquals(0, f.followerUid())
        assertEquals(0, f.player.activePetItemId)
        assertEquals(tempoross.item.id, f.player.inv[0]?.id)
        assertTrue(f.npcs.none())
    }

    @Test
    fun `logout clears follower uid and call restores it from the saved item`() {
        val f = Fixture()
        f.player.inv[0] = InvObj(f.fish.item)
        assertEquals(PetActionResult.Summoned, f.manager.summon(f.player, f.player.inv, 0, f.fish))
        val previous = f.singleFollower()
        val oldUid = f.assertFollowerUid(previous)

        f.manager.logout(f.player)
        assertEquals(0, f.followerUid())
        assertEquals(f.fish.item.id, f.player.activePetItemId)
        assertNull(NpcUid(oldUid).resolve(f.npcs))
        assertTrue(f.npcs.none())

        assertEquals(PetActionResult.Called, f.manager.call(f.player))
        val restored = f.singleFollower()
        assertNotSame(previous, restored)
        f.assertFollowerUid(restored)
        assertEquals(f.fish.item.id, f.player.activePetItemId)

        assertTrue(f.registry.del(restored).isSuccess())
        f.manager.tick(f.player)
        val recovered = f.singleFollower()
        assertNotSame(restored, recovered)
        f.assertFollowerUid(recovered)
    }

    private class Fixture {
        val player = Player().apply {
            coords = CoordGrid(3204, 3204)
            previousCoords = coords
            slotId = 1
            uuid = 1L
            assignUid()
            inv = Inventory.create("inv.inv")
        }
        val collision = CollisionFlagMap().apply {
            allocateIfAbsent(player.coords.x, player.coords.z, player.level)
        }
        val npcs = NpcList()
        val registry = NpcRegistry(npcs, collision, EventBus())
        val manager = PetFollowerManager(registry, npcs, collision)
        val fish = manager.petTypes.first {
            it.item.id == "obj.skillpetfish".asRSCM(RSCMType.OBJ)
        }
        val tempoross = manager.petTypes.first {
            it.item.id == "obj.skillpetfish_tempoross".asRSCM(RSCMType.OBJ)
        }

        fun singleFollower(): Npc = npcs.single()

        fun followerUid(): Int = player.vars["varp.follower_npc"]

        fun assertFollowerUid(npc: Npc): Int = followerUid().also { uid ->
            assertNotEquals(0, uid)
            assertEquals(npc.uid.packed, uid)
            assertSame(npc, NpcUid(uid).resolve(npcs))
        }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupCacheAndInventoryTransactions() {
            ServerCacheManager.init(240).close()
            val script = InvTransactionsScript(PlayerItemStorage(emptySet()))
            val context = ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache())
            with(script) { context.startup() }
        }
    }
}
