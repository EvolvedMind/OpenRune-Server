package org.rsmod.content.other.pets

import dev.openrune.ServerCacheManager
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.Assertions.assertTrue
import org.rsmod.annotations.InternalApi
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.events.interact.NpcUDefaultEvents
import org.rsmod.api.player.events.interact.OpEvent
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.route.StepFactory
import org.rsmod.content.other.pets.cats.CatCare
import org.rsmod.content.other.pets.cats.CatChase
import org.rsmod.content.other.pets.cats.CatDialogue
import org.rsmod.content.other.pets.cats.CatScript
import org.rsmod.content.other.pets.dogs.DogCare
import org.rsmod.content.other.pets.dogs.DogScript
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import sun.misc.Unsafe

@OptIn(InternalApi::class)
internal class PetTestFixture {
    val player = player(1)
    val other = player(2)
    val clock = MapClock(100)
    val events = EventBus()
    val collision = CollisionFlagMap().apply {
        for (x in 3192..3216 step 8) for (z in 3192..3216 step 8) {
            allocateIfAbsent(x, z, 0)
        }
    }
    val npcs = NpcList()
    val players = PlayerList().apply {
        this[player.slotId] = player
        this[other.slotId] = other
    }
    val registry = NpcRegistry(npcs, collision, events)
    val repository = NpcRepository(clock, registry, npcs)
    val followers = PetFollowers(repository, npcs, clock, collision, StepFactory(collision))
    val random = DefaultGameRandom(1)
    val insurance = PetInsurance()
    val rewards = PetRewards(followers, insurance, random)
    val morphs = PetMorphs(followers)
    val catCare = CatCare(followers)
    val dogCare = DogCare(followers)
    private val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
    private val accessContext = ProtectedAccessContextFactory.empty().copy(
        getRandom = { random },
        getEventBus = { events },
        getNpcList = { npcs },
        getPlayerList = { players },
        getCollision = { collision },
        getAlignment = { TextAlignment() },
    )

    fun register(script: PluginScript) {
        with(script) { scripts.startup() }
    }

    fun registerFollowers() {
        register(PetScript(followers, rewards, unusedCommandLauncher(), players))
        val chase = CatChase(repository, random, catCare, followers)
        val objects = ObjRepository(clock, ObjRegistry(ZoneUpdateMap()))
        val death = NpcDeath(repository, players, objects, emptySet(), emptySet())
        register(CatScript(followers, catCare, chase, CatDialogue(catCare), death, players))
        register(DogScript(followers, dogCare))
    }

    fun drop(actor: Player = player, slot: Int = 0) {
        val obj = checkNotNull(actor.inv[slot])
        val type = checkNotNull(ServerCacheManager.getItem(obj.id))
        dispatch(HeldObjEvents.Op5(slot, obj, type, actor.inv), actor)
    }

    fun operate(npc: Npc, name: String, actor: Player = player) {
        val event = when (petOpIndex(npc.type.internalName, name)) {
            1 -> NpcEvents.Op1(npc)
            2 -> NpcEvents.Op2(npc)
            3 -> NpcEvents.Op3(npc)
            4 -> NpcEvents.Op4(npc)
            5 -> NpcEvents.Op5(npc)
            else -> error("${npc.type.internalName} has no $name option")
        }
        dispatch(event, actor)
    }

    fun useOn(npc: Npc, actor: Player = player, slot: Int = 0) {
        val obj = checkNotNull(actor.inv[slot])
        val type = checkNotNull(ServerCacheManager.getItem(obj.id))
        dispatch(NpcUDefaultEvents.OpType(npc, slot, type, npc.type), actor)
    }

    fun dispatch(event: OpEvent, actor: Player = player) {
        perform(actor) { assertTrue(events.publish(this, event), "Missing handler: $event") }
    }

    fun perform(actor: Player = player, block: suspend ProtectedAccess.() -> Unit) {
        val coroutine = GameCoroutine()
        actor.activeCoroutine = coroutine
        val access = ProtectedAccess(actor, coroutine, accessContext)
        var outcome: Result<Unit>? = null
        val action: suspend () -> Unit = { access.block() }
        action.startCoroutine(object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) { outcome = result }
        })
        repeat(10) {
            if (outcome != null) return@repeat
            check(coroutine.isAwaiting(ResumePauseButtonInput::class)) { "Unexpected suspension" }
            val parent = listOf("objectbox", "messagebox", "chat_left", "chat_right").firstOrNull {
                actor.ui.containsModal("interface.$it")
            } ?: error("Unexpected dialogue menu")
            val component = if (parent == "objectbox") "universe" else "continue"
            coroutine.resumeWith(ResumePauseButtonInput("component.$parent:$component", -1))
        }
        actor.activeCoroutine = null
        checkNotNull(outcome) { "Interaction did not complete" }.getOrThrow()
    }

    fun uid(): Int = player.vars["varp.follower_npc"]

    private fun player(slot: Int) = Player().apply {
        coords = CoordGrid(3204, 3204)
        previousCoords = coords
        slotId = slot
        uuid = slot.toLong()
        assignUid()
        currentMapClock = 100
        processedMapClock = 100
        inv = Inventory.create("inv.inv")
    }

    private fun unusedCommandLauncher(): ProtectedAccessLauncher {
        val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        return (field.get(null) as Unsafe).allocateInstance(ProtectedAccessLauncher::class.java) as ProtectedAccessLauncher
    }
}
