package org.rsmod.content.other.pets

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.util.BlockWalk
import kotlin.coroutines.*
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.interact.HeldInteractions
import org.rsmod.api.player.interact.HeldUInteractions
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.worn.HeldEquipOp
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.HeldOp
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@ResourceLock("ServerCacheManager")
@OptIn(InternalApi::class)
class CompanionPetTest {
    @Test fun `native Drop and Release summon the correct actors and native Pick-up returns the item`() {
        for ((entry, op) in summons) {
            val f = Fixture()
            f.player.inv[0] = InvObj(entry.obj, 1)
            f.held(op)
            val actor = checkNotNull(f.followers.follower(f.player))
            assertEquals(entry.form!!.npcId, actor.type.id)
            assertEquals(BlockWalk.None, actor.type.blockWalk)
            assertEquals(0, f.player.inv.count(entry.obj))
            assertEquals(entry.objId, f.player.followerObj)
            f.pickup(actor)
            assertEquals(1, f.player.inv.count(entry.obj))
            assertFalse(f.followers.hasFollower(f.player))
        }
    }

    @Test fun `existing follower rejects a second summon without consuming the companion`() {
        for ((entry, op) in summons) {
            val f = Fixture()
            f.followers.spawn(f.player, Pets.all.first().base)
            val old = f.followers.follower(f.player)
            f.player.inv[0] = InvObj(entry.obj, 1)
            f.held(op)
            assertSame(old, f.followers.follower(f.player))
            assertEquals(1, f.player.inv.count(entry.obj))
        }
    }

    @Test fun `foreign pickup and full inventory leave the owned companion intact`() {
        for ((entry, op) in summons) {
            val f = Fixture()
            f.player.inv[0] = InvObj(entry.obj, 1)
            f.held(op)
            val actor = checkNotNull(f.followers.follower(f.player))
            val stranger = f.player(2)
            f.pickup(actor, stranger)
            assertEquals(0, stranger.inv.count(entry.obj))
            assertSame(actor, f.followers.follower(f.player))
            repeat(28) { f.player.inv[it] = InvObj("obj.spade", 1) }
            f.pickup(actor)
            assertSame(actor, f.followers.follower(f.player))
            f.player.inv[0] = null
            f.pickup(actor)
            assertEquals(1, f.player.inv.count(entry.obj))
            assertFalse(f.followers.hasFollower(f.player))
        }
    }

    @Test fun `native companions survive relog and call without duplication`() {
        for ((entry, op) in summons) {
            val f = Fixture()
            f.player.inv[0] = InvObj(entry.obj, 1)
            f.held(op)
            f.followers.onLogout(f.player)
            assertNull(f.followers.follower(f.player))
            f.followers.onPostTick(f.player)
            val restored = checkNotNull(f.followers.follower(f.player))
            f.followers.call(f.player)
            assertSame(restored, f.followers.follower(f.player))
            f.pickup(restored)
            assertEquals(1, f.player.inv.count(entry.obj))
        }
    }

    @Test fun `spooky chair returns after twenty ticks and waits safely for inventory room`() {
        for (full in listOf(false, true)) {
            val f = Fixture()
            f.player.inv[0] = InvObj(CompanionPets.chair.obj, 1)
            f.held(HeldOp.Op2)
            val chair = f.followers.follower(f.player)
            assertNotNull(chair)
            if (full) repeat(28) { f.player.inv[it] = InvObj("obj.spade", 1) }
            f.tick(19)
            assertSame(chair, f.followers.follower(f.player))
            f.tick(1)
            if (full) {
                assertSame(chair, f.followers.follower(f.player))
                f.player.inv[0] = null
                f.tick(1)
            }
            assertEquals(1, f.player.inv.count(CompanionPets.chair.obj))
            assertFalse(f.followers.hasFollower(f.player))
            f.tick(3)
            assertEquals(1, f.player.inv.count(CompanionPets.chair.obj))
        }
    }

    @Test fun `full inventory chair logout retains a restorable saved follower`() {
        val f = Fixture()
        f.player.inv[0] = InvObj(CompanionPets.chair.obj, 1)
        f.held(HeldOp.Op1)
        repeat(28) { f.player.inv[it] = InvObj("obj.spade", 1) }
        with(f.script) { f.scripts.shutdown() }
        f.followers.onLogout(f.player)
        assertEquals(CompanionPets.chair.objId, f.player.followerObj)
        f.followers.onPostTick(f.player)
        f.tick(21)
        assertNotNull(f.followers.follower(f.player))
        f.player.inv[0] = null
        f.tick(1)
        assertEquals(1, f.player.inv.count(CompanionPets.chair.obj))
        assertFalse(f.followers.hasFollower(f.player))
    }

    @Test fun `native Feed swaps food for its empty box in a full inventory and preserves the fish`() {
        for (entry in CompanionPets.fish + CompanionPets.mayor) {
            val f = Fixture()
            repeat(28) { f.player.inv[it] = InvObj("obj.spade", 1) }
            val pet = InvObj(entry.obj, 1)
            f.player.inv[0] = pet
            f.player.inv[1] = InvObj("obj.fish_food", 1)
            f.held(if (entry == CompanionPets.mayor) HeldOp.Op2 else HeldOp.Op3)
            assertEquals(pet, f.player.inv[0])
            assertEquals(0, f.player.inv.count("obj.fish_food"))
            assertEquals(1, f.player.inv.count("obj.empty_fishfood_box"))
            assertEquals(26, f.player.inv.count("obj.spade"))
        }
    }

    @Test fun `native Use food on fish works in both directions and missing food consumes nothing`() {
        for (entry in CompanionPets.fish + CompanionPets.mayor) {
            for (reverse in listOf(false, true)) {
                val f = Fixture()
                val pet = InvObj(entry.obj, 1)
                f.player.inv[0] = pet
                f.held(if (entry == CompanionPets.mayor) HeldOp.Op2 else HeldOp.Op3)
                assertEquals(pet, f.player.inv[0])
                assertEquals(0, f.player.inv.count("obj.empty_fishfood_box"))
                f.player.inv[1] = InvObj("obj.fish_food", 1)
                f.use(if (reverse) 0 else 1, if (reverse) 1 else 0)
                assertEquals(pet, f.player.inv[0])
                assertEquals(0, f.player.inv.count("obj.fish_food"))
                assertEquals(1, f.player.inv.count("obj.empty_fishfood_box"))
            }
        }
    }

    private class Fixture {
        val events = EventBus()
        val clock = MapClock(100)
        val collision = CollisionFlagMap().apply {
            for (x in 3192..3216 step 8) for (z in 3192..3216 step 8) allocateIfAbsent(x, z, 0)
        }
        val npcs = NpcList()
        val followers = PetFollowers(NpcRepository(clock, NpcRegistry(npcs, collision, events), npcs), npcs, clock, collision)
        val menu = PetMenu(mock(PetRewards::class.java))
        private val held = HeldInteractions::class.java.declaredConstructors.single().let {
            it.isAccessible = true
            it.newInstance(events, mock(it.parameterTypes[1]), mock(it.parameterTypes[2]), HeldEquipOp(events)) as HeldInteractions
        }
        val script = CompanionPetScript(followers, menu, held)
        val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
        val player = player(1)
        init { with(script) { scripts.startup() } }
        fun player(slot: Int) = Player().apply {
            slotId = slot; uuid = slot.toLong(); assignUid()
            coords = CoordGrid(3204, 3204); previousCoords = coords
            currentMapClock = 100; processedMapClock = 100
            inv = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())), arrayOfNulls(28))
        }
        fun held(op: HeldOp) = run(player) { held.interact(this, inv, 0, op) }
        fun use(first: Int, second: Int) = run(player) {
            HeldUInteractions(events).interact(this, inv,
                checkNotNull(ServerCacheManager.getItem(checkNotNull(inv[first]).id)), first,
                checkNotNull(ServerCacheManager.getItem(checkNotNull(inv[second]).id)), second)
        }
        fun pickup(npc: Npc, owner: Player = player) = run(owner) {
            val op = checkNotNull(petOpIndex(CompanionPets.all.first { it.form?.npcId == npc.type.id }.npc!!, "Pick-up"))
            val event = checkNotNull(NpcInteractions(events).opTrigger(owner, npc, if (op == 3) InteractionOp.Op3 else InteractionOp.Op1))
            assertTrue(events.publish(this, event))
        }
        fun tick(count: Int) { repeat(count) { player.currentMapClock++; script.onPostTick(player) } }
        fun run(owner: Player, action: suspend ProtectedAccess.() -> Unit) {
            val coroutine = GameCoroutine("native companion interaction")
            owner.activeCoroutine = coroutine
            val context = ProtectedAccessContextFactory.empty().copy(getEventBus = { events }, getCollision = { collision })
            var outcome: Result<Unit>? = null
            val block: suspend () -> Unit = { action(ProtectedAccess(owner, coroutine, context)) }
            block.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { outcome = result }
            })
            checkNotNull(outcome).getOrThrow()
            owner.activeCoroutine = null
        }
    }

    companion object {
        private val summons get() = listOf(CompanionPets.broav to HeldOp.Op4, CompanionPets.toyCat to HeldOp.Op5, CompanionPets.chair to HeldOp.Op1)
        private val restored = mutableListOf<() -> Unit>()

        @JvmStatic @BeforeAll fun cache() {
            ServerCacheManager.init(240).close()
            for ((owner, name) in listOf(
                "org.rsmod.api.invtx.InvTransactionsScriptKt" to "cachedInventoryTransactions",
                "org.rsmod.api.invtx.VirtualInvTransactionsKt" to "cachedPlayerItemStorage")) {
                val field = Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
                val old = field.get(null)
                restored += { field.set(null, old) }
            }
            val storage = InvVirtualStorageHolder.instance
            restored += { InvVirtualStorageHolder.instance = storage }
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup() }
        }

        @JvmStatic @AfterAll fun restore() { restored.asReversed().forEach { it() }; restored.clear() }
    }
}
