package org.rsmod.content.other.pets

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.game.process.npc.NpcMovementProcessor
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.route.StepFactory
import org.rsmod.content.other.pets.cats.Cats
import org.rsmod.content.other.pets.dialogue.HeronDialogue
import org.rsmod.content.other.pets.dogs.Dogs
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.inv.InvObj
import org.rsmod.game.map.Direction
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.flag.CollisionFlag

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class PetFollowersTest {
    @Test
    fun `all 289 forms drop and pick up through native handlers without losing their item`() {
        val f = PetTestFixture()
        f.registerFollowers()
        val forms = Pets.all.flatMap { it.forms } + Cats.all.map { it.form } + Dogs.all.map { it.form }
        for (form in forms) {
            f.player.inv[0] = InvObj(form.obj)
            f.drop()
            val npc = checkNotNull(f.followers.follower(f.player)) { form.obj }
            assertEquals(form.objId, f.player.followerObj, form.obj)
            assertEquals(form.npcId, npc.id, form.obj)
            assertNull(f.player.inv[0], form.obj)
            assertEquals(npc.uid.packed, f.uid(), form.obj)
            assertSame(npc, NpcUid(f.uid()).resolve(f.npcs), form.obj)
            assertFalse(f.player.coords.x in npc.coords.x until npc.coords.x + npc.size &&
                f.player.coords.z in npc.coords.z until npc.coords.z + npc.size, form.obj)
            f.operate(npc, "Pick-up")
            assertEquals(form.objId, f.player.inv[0]?.id, form.obj)
            assertEquals(0, f.player.followerObj, form.obj)
            assertEquals(0, f.uid(), form.obj)
            assertTrue(f.npcs.none(), form.obj)
        }
    }

    @Test
    fun `blocked drop and full inventory pickup leave the item or follower intact`() {
        val f = PetTestFixture()
        f.registerFollowers()
        val form = Pets["beaver"].base
        f.player.inv[0] = InvObj(form.obj)
        for (direction in Direction.entries) {
            f.collision.add(3204 + direction.xOff, 3204 + direction.zOff, 0, CollisionFlag.LOC)
        }
        f.drop()
        assertEquals(form.objId, f.player.inv[0]?.id)
        assertFalse(f.followers.hasFollower(f.player))
        for (direction in Direction.entries) {
            f.collision.remove(3204 + direction.xOff, 3204 + direction.zOff, 0, CollisionFlag.LOC)
        }
        f.drop()
        val npc = checkNotNull(f.followers.follower(f.player))
        for (slot in 0 until f.player.inv.size) f.player.inv[slot] = InvObj("obj.coins", 1)
        f.operate(npc, "Pick-up")
        assertSame(npc, f.followers.follower(f.player))
        assertEquals(form.objId, f.player.followerObj)
        f.operate(npc, "Pick-up", f.other)
        assertSame(npc, f.followers.follower(f.player))
        assertTrue(f.other.inv.isEmpty())
    }

    @Test
    fun `logout clears the uid and restores the saved follower with a new uid`() {
        val f = PetTestFixture()
        val form = Dogs.all.first().form
        assertTrue(f.followers.spawn(f.player, form))
        val previous = checkNotNull(f.followers.follower(f.player))
        val previousUid = f.uid()
        f.followers.onLogout(f.player)
        assertEquals(0, f.uid())
        assertEquals(form.objId, f.player.followerObj)
        assertNull(NpcUid(previousUid).resolve(f.npcs))
        f.followers.onPostTick(f.player)
        val restored = checkNotNull(f.followers.follower(f.player))
        assertNotSame(previous, restored)
        assertNotEquals(previousUid, f.uid())
        assertSame(restored, NpcUid(f.uid()).resolve(f.npcs))
    }

    @Test
    fun `existing PR8 followers migrate to the official persistent follower object`() {
        val f = PetTestFixture()
        val form = Pets["heron"].base
        VarPlayerIntMapSetter.set(f.player, "varp.active_pet", form.objId)
        f.followers.onPostTick(f.player)
        assertEquals(form.objId, f.player.followerObj)
        assertEquals(0, f.player.vars["varp.active_pet"])
        assertEquals(form.npcId, f.followers.follower(f.player)?.id)
    }

    @Test
    fun `another player's heron cannot consume offerings or become the attacker's pet`() {
        val f = PetTestFixture()
        f.register(HeronDialogue(f.followers))
        for ((form, offering) in listOf(
            Pets["heron"].base to "obj.spirit_flakes",
            Pets["heron"].forms.last() to "obj.raw_shark",
        )) {
            assertTrue(f.followers.spawn(f.player, form))
            val npc = checkNotNull(f.followers.follower(f.player))
            f.other.inv[0] = InvObj(offering, 3000)
            val before = f.other.inv[0]
            f.useOn(npc, f.other)
            assertEquals(before, f.other.inv[0])
            assertEquals(0, f.other.followerObj)
            assertSame(npc, f.followers.follower(f.player))
        }
    }

    @Test
    fun `late cycle follows registered players and native movement never steps onto the owner`() {
        val f = PetTestFixture()
        f.registerFollowers()
        f.player.previousCoords = f.player.coords.translate(0, -1)
        assertTrue(f.followers.spawn(f.player, Pets["heron"].base))
        val npc = checkNotNull(f.followers.follower(f.player))
        npc.teleport(f.collision, f.player.coords.translate(0, 1))
        f.player.previousCoords = f.player.coords
        f.collision.add(f.player.coords.x, f.player.coords.z, 0, CollisionFlag.BLOCK_PLAYERS)
        val movement = NpcMovementProcessor(f.collision, StepFactory(f.collision), f.events)
        repeat(10) {
            f.player.currentMapClock = f.clock.cycle
            f.player.processedMapClock = f.clock.cycle
            npc.currentMapClock = f.clock.cycle
            npc.processedMapClock = f.clock.cycle
            npc.previousCoords = npc.coords
            movement.process(npc)
            assertFalse(petOverlapsPlayer(npc.coords, npc.size, f.player.coords))
            f.clock.tick()
            assertTrue(f.events.publish(GameLifecycle.LateCycle))
            assertFalse(petOverlapsPlayer(npc.coords, npc.size, f.player.coords))
            npc.pendingStepCount = 0
            npc.pendingTeleport = false
            npc.pendingTelejump = false
        }
        assertEquals(CoordGrid(3204, 3203), npc.coords)
    }

    companion object {
        @JvmStatic @BeforeAll fun cacheAndTransactions() {
            ServerCacheManager.init(240).close()
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
        }
    }
}
