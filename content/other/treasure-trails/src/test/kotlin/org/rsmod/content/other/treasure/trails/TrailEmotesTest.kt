package org.rsmod.content.other.treasure.trails

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import kotlin.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.Mockito.*
import org.rsmod.api.config.refs.params
import org.rsmod.api.death.*
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.respawn.BossRespawnTimers
import org.rsmod.api.player.events.interact.ContextualNpcOp
import org.rsmod.api.player.interact.ContextualInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.interfaces.emotes.PlayEmote
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.*
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@ResourceLock("ServerCacheManager")
class TrailEmotesTest {
    @Test fun `ordered medium emotes retain their stage and Uri advances once`() {
        val f = Fixture(total = 3)
        f.emote(1)
        assertEquals(0, f.state().phase)
        assertEquals(0, f.npcs.count())
        f.finishGuardian()
        f.emote(0)
        assertEquals(7, f.state().phase)
        f.talk()
        assertEquals(0, f.state().completed)
        f.emote(0) // Wrong repeated first emote does not change the ordered state.
        assertEquals(7, f.state().phase)
        f.emote(1)
        assertEquals(8, f.state().phase)
        assertEquals(1, f.npcs.count())
        val uri = f.uri()
        f.talk(uri)
        assertEquals(1, f.state().completed)
        assertEquals(0, f.npcs.count())
        f.talk(uri)
        assertEquals(1, f.state().completed)
    }

    @Test fun `master guardian defeat survives recalling Uri and never starts another fight`() {
        for (cleanup in 0..2) {
            val f = Fixture(total = 1, combat = true)
            f.finishGuardian(); f.emote(0)
            assertEquals(8, f.state().phase)
            f.emote(0)
            assertEquals(1, f.npcs.count())
            if (cleanup == 0) f.repo.del(f.uri(), Int.MAX_VALUE)
            else if (cleanup == 1) f.events.publish(SessionStateEvent.Logout(f.player))
            else TrailEmoteDeathHook(f.emotes).cleanup(f.player)
            f.player.inv[0] = f.player.inv[0]!!.copy()
            f.emote(0)
            assertEquals(8, f.state().phase)
            assertEquals(1, f.npcs.count())
            f.talk()
            assertEquals(1, f.caskets())
        }
    }

    @Test fun `final Uri reward is one casket and another player cannot claim it`() {
        val f = Fixture(total = 1)
        f.finishGuardian(); f.emote(0); f.emote(1)
        val uri = f.uri()
        assertNull(f.contextual.npc(Player(), uri, InteractionOp.Op1))
        f.talk(uri, Player()) // Even a directly dispatched protected event checks the owner.
        assertEquals(8, f.state().phase)
        assertEquals(0, f.caskets())
        f.talk(uri)
        assertEquals(1, f.caskets())
        assertEquals(0, f.npcs.count())
        f.talk(uri)
        assertEquals(1, f.caskets())
    }

    @Test fun `Uri timeout logout and player death retain progress without another guardian`() {
        for (stage in listOf(7, 8)) for (cleanup in 0..2) {
            val f = Fixture(total = 1)
            f.finishGuardian(); f.emote(0)
            if (stage == 8) f.emote(1)
            if (cleanup == 0) f.repo.del(f.uri(), Int.MAX_VALUE)
            else if (cleanup == 1) f.events.publish(SessionStateEvent.Logout(f.player))
            else TrailEmoteDeathHook(f.emotes).cleanup(f.player)
            assertEquals(0, f.npcs.count())
            f.player.inv[0] = f.player.inv[0]!!.copy() // Permanent item phase survives reload.
            assertEquals(stage, f.state().phase)
            f.emote(1)
            assertEquals(8, f.state().phase)
            assertEquals(1, f.npcs.count())
            f.talk()
            assertEquals(1, f.caskets())
        }
    }

    @Test fun `wrong location requirements and stale clue cannot claim a Uri reward`() {
        val f = Fixture(total = 1)
        f.player.coords = f.player.coords.translate(20, 20)
        f.emote(0)
        assertEquals(0, f.npcs.count())
        f.player.coords = f.location
        `when`(f.requirements.missing(f.player, f.clue)).thenReturn("Wear the requested outfit.")
        f.emote(0)
        assertEquals(0, f.npcs.count())
        `when`(f.requirements.missing(f.player, f.clue)).thenReturn(null)
        f.finishGuardian(); f.emote(0); f.emote(1)
        val uri = f.uri()
        f.player.inv[0] = f.player.inv[0]!!.copy(vars = TrailState(f.clue.row, 2).encode())
        f.talk(uri)
        assertEquals(0, f.state().completed)
        assertEquals(0, f.caskets())
        f.emotes.let { with(it) { f.context.shutdown() } }
        assertEquals(0, f.npcs.count())
        assertNull(f.contextual.npc(f.player, uri, InteractionOp.Op1))
    }

    private class Fixture(total: Int, combat: Boolean = false) {
        val events = EventBus()
        val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
        val random = DefaultGameRandom(42)
        val progress = TrailProgress(TrailCatalog(), random)
        val clue = progress.catalog.clues.values.first { clue ->
            clue.kind == "emote" && clue.tier == (if (combat) TrailTier.MASTER else TrailTier.MEDIUM) &&
                clue.fields.ints("emote").let { it.size == (if (combat) 1 else 2) && it.distinct().size == it.size && it.all { id -> id in 0..21 } } &&
                clue.fields.ints("combat_encounter").isNotEmpty() == combat
        }
        val location = CoordGrid(clue.targets.map(progress.catalog::fields).first { it.table == "cluehelper_target_coord" }.int("coord"))
        val player = Player().apply {
            uuid = 1; observerUUID = 1; slotId = 1; coords = location
            inv = Inventory(ServerCacheManager.getInventory("inv.inv".asRSCM())!!, arrayOfNulls(28))
        }
        val players = PlayerList().apply { this[1] = player }
        val npcs = NpcList()
        val clock = MapClock(100)
        val repo = NpcRepository(clock, NpcRegistry(npcs, CollisionFlagMap(), events), npcs)
        val guards = TrailGuards(progress, repo, mock(AiPlayerInteractions::class.java), random)
        val requirements = mock(TrailRequirements::class.java)
        val contextual = ContextualInteractions()
        val emotes = TrailEmotesScript(progress, TrailTargets(progress), requirements, guards, repo, contextual)
        val death = NpcDeath(repo, players, mock(ObjRepository::class.java), setOf(NpcDeathDropHook { true }),
            setOf(TrailGuardKillHook(guards)), BossRespawnTimers(clock))
        init {
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { context.startup() }
            with(guards) { context.startup() }
            with(emotes) { context.startup() }
            player.inv[0] = InvObj(ServerCacheManager.getItem(progress.catalog.item(clue))!!, 1, TrailState(clue.row, total).encode())
        }
        fun state() = checkNotNull(progress.state(player.inv[0]!!))
        fun caskets() = player.inv.objs.filterNotNull().filter { it.id == clue.tier.casket }.sumOf { it.count }
        fun uri() = npcs.single { it.type.id == "npc.trail_${clue.tier.key}_uri".asRSCM() }
        fun emote(index: Int) {
            val name = listOf("yes", "no", "bow", "angry", "think", "wave", "shrug", "cheer", "beckon", "laugh", "jump_with_joy", "yawn", "dance", "dance_scottish", "dance_spin", "dance_headbang", "cry", "blow_kiss", "panic", "ya_boo_sucks", "clap", "fremmenik_salute")[clue.fields.ints("emote")[index]]
            events.publish(PlayEmote(player, ServerCacheManager.getAnim("seq.emote_$name".asRSCM())!!))
        }
        fun finishGuardian() {
            if (clue.fields.ints("combat_encounter").isEmpty()) return
            emote(0)
            assertEquals(4, state().phase)
            for (npc in npcs.toList()) {
                npc.recordDamage(player, 1); npc.hitpoints = 0
                val access = mock(StandardNpcAccess::class.java) { call ->
                    when (call.method.name) {
                        "getNpc" -> npc
                        "getCoords" -> npc.coords
                        "param" -> npc.param(params.death_anim)
                        else -> RETURNS_DEFAULTS.answer(call)
                    }
                }
                run { death.deathWithDrops(access) }
            }
            assertEquals(5, state().phase)
        }
        fun talk(npc: Npc = uri(), actor: Player = player) {
            val event = ContextualNpcOp(npc, 0x555249L)
            val access = mock(ProtectedAccess::class.java) { call ->
                if (call.method.name == "getPlayer") actor else RETURNS_DEFAULTS.answer(call)
            }
            run { events.publish(access, event) }
        }
        private fun run(block: suspend () -> Unit) {
            var outcome: Result<Unit>? = null
            block.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { outcome = result }
            })
            checkNotNull(outcome).getOrThrow()
        }
    }

    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
