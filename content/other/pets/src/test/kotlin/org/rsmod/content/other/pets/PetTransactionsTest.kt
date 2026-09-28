package org.rsmod.content.other.pets

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
import org.rsmod.content.other.pets.dialogue.HeronDialogue
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.inv.InvObj
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.flag.CollisionFlag

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class PetTransactionsTest {
    @Test
    fun `blocked item morph preserves current follower cost and unlock`() {
        val f = PetTestFixture()
        val current = Pets["giant_squirrel"].base
        assertTrue(f.followers.spawn(f.player, current))
        val npc = checkNotNull(f.followers.follower(f.player))
        val acorn = checkNotNull(ServerCacheManager.getItem("obj.dark_acorn".asRSCM()))
        f.player.inv[0] = InvObj(acorn)
        blockPlacement(f)
        f.perform { assertTrue(f.morphs.tryUse(this, npc, acorn)) }
        assertEquals(acorn.id, f.player.inv[0]?.id)
        assertEquals(0, f.player.vars["varbit.hallowed_skillpet_dark_unlocked"])
        assertEquals(current.objId, f.player.followerObj)
        assertSame(npc, f.followers.follower(f.player))
    }

    @Test
    fun `phoenix recolouring consumes firelighters and changes the active form`() {
        val f = PetTestFixture()
        assertTrue(f.followers.spawn(f.player, Pets["phoenix"].base))
        val npc = checkNotNull(f.followers.follower(f.player))
        val firelighters = checkNotNull(ServerCacheManager.getItem("obj.gnomish_firelighter_blue".asRSCM()))
        f.player.inv[0] = InvObj(firelighters, 250)
        f.perform { assertTrue(f.morphs.tryUse(this, npc, firelighters)) }
        assertEquals(0, f.player.inv.count("obj.gnomish_firelighter_blue"))
        assertEquals("obj.phoenixpet_blue".asRSCM(), f.player.followerObj)
        assertEquals(1, f.player.vars["varbit.pet_phoenix_blue"])
        assertEquals(f.followers.follower(f.player)?.uid?.packed, f.uid())
    }

    @Test
    fun `held rock golem morph replaces in place in a full inventory and requires its catalyst`() {
        val f = PetTestFixture()
        val current = Pets["rock_golem"].base
        val ore = checkNotNull(ServerCacheManager.getItem("obj.tin_ore".asRSCM()))
        for (slot in 0 until f.player.inv.size) f.player.inv[slot] = InvObj("obj.coins", 1)
        f.player.inv[0] = InvObj(current.obj)
        f.perform { assertTrue(f.morphs.tryUseHeld(this, current.obj, ore)) }
        assertEquals(current.objId, f.player.inv[0]?.id)
        f.player.inv[1] = InvObj(ore)
        f.perform { assertTrue(f.morphs.tryUseHeld(this, current.obj, ore)) }
        assertEquals(1, f.player.inv.count("obj.skillpetmining_tin"))
        assertEquals(0, f.player.inv.count(current.obj))
        assertEquals(1, f.player.inv.count("obj.tin_ore"))
        assertTrue(f.player.inv.isFull())
    }

    @Test
    fun `blocked heron transformation returns its offering`() {
        val f = PetTestFixture()
        f.register(HeronDialogue(f.followers))
        assertTrue(f.followers.spawn(f.player, Pets["heron"].base))
        val npc = checkNotNull(f.followers.follower(f.player))
        f.player.inv[0] = InvObj("obj.spirit_flakes", 3000)
        blockPlacement(f)
        f.useOn(npc)
        assertSame(npc, f.followers.follower(f.player))
        assertEquals(3000, f.player.inv.count("obj.spirit_flakes"))
        assertEquals(Pets["heron"].base.objId, f.player.followerObj)
    }

    @Test
    fun `another player cannot interact with Beef`() {
        val f = PetTestFixture()
        f.register(PetEmoteScript(f.followers))
        assertTrue(f.followers.spawn(f.player, Pets["beef"].base))
        val npc = checkNotNull(f.followers.follower(f.player))
        f.operate(npc, "Interact", f.other)
        assertSame(npc, f.followers.follower(f.player))
        assertFalse(f.followers.hasFollower(f.other))
    }

    @Test
    fun `blocked reclaim with full inventory keeps insured pet available`() {
        val f = PetTestFixture()
        val pet = Pets["heron"]
        assertTrue(f.insurance.insure(f.player, pet))
        for (slot in 0 until f.player.inv.size) f.player.inv[slot] = InvObj("obj.coins", 1)
        blockPlacement(f)
        assertFalse(f.rewards.reclaim(f.player, pet))
        assertTrue(f.insurance.isInsured(f.player, pet))
        assertTrue(f.insurance.hasReclaimable(f.player))
        assertFalse(f.followers.hasFollower(f.player))
    }

    private fun blockPlacement(f: PetTestFixture) {
        for (x in 3198..3210) for (z in 3198..3210) {
            if (x != f.player.coords.x || z != f.player.coords.z) {
                f.collision.add(x, z, 0, CollisionFlag.LOC)
            }
        }
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
