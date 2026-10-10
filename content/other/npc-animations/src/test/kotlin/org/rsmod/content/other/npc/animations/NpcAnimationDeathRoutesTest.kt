package org.rsmod.content.other.npc.animations

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.Mockito.*
import org.rsmod.api.death.*
import org.rsmod.api.game.process.npc.NpcInteractionProcessor
import org.rsmod.api.game.process.npc.NpcMovementProcessor
import org.rsmod.api.game.process.npc.NpcQueueProcessor
import org.rsmod.api.npc.access.StandardNpcAccessContextFactory
import org.rsmod.api.npc.access.StandardNpcAccessLauncher
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.npc.interact.*
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.npc.queueDeath
import org.rsmod.api.npc.respawn.BossRespawnTimers
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.route.RayCastValidator
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.*
import org.rsmod.game.entity.npc.NpcInfoProtocol
import org.rsmod.game.interact.InteractionPlayerOp
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@ResourceLock("ServerCacheManager")
@OptIn(org.rsmod.annotations.InternalApi::class)
class NpcAnimationDeathRoutesTest {
    @Test fun `real npc retaliation interaction dispatches static handler without human attack`() {
        for (symbol in NpcAnimationStaticProfiles.targets) {
            val f = Fixture(symbol)
            val interactions = AiPlayerInteractions(f.events, f.players)
            val processor = NpcInteractionProcessor(DefaultGameRandom(1), f.events,
                mock(LocRegistry::class.java), mock(ObjRegistry::class.java),
                mock(BoundValidator::class.java), mock(RayCastValidator::class.java),
                mock(AiLocInteractions::class.java), mock(AiLocTInteractions::class.java),
                mock(AiNpcInteractions::class.java), mock(AiNpcTInteractions::class.java),
                mock(AiObjInteractions::class.java), interactions, mock(AiPlayerTInteractions::class.java),
                f.launcher, mock(NpcMovementProcessor::class.java))
            f.npc.opPlayer2(f.player, interactions)
            val requested = f.npc.interaction as InteractionPlayerOp
            processor.triggerOp(f.npc, requested)
            assertNull(f.npc.interaction)
            assertNull(f.npc.mode)
            verify(f.info, never()).setSequence(anyInt(), anyInt())
        }
    }

    @Test fun `static target death preserves native removal and reward without human animation`() {
        for (symbol in listOf("npc.castlewars_barricade_saradomin", "npc.test_combat_dummy", "npc.verzik_web_npc")) {
            val f = Fixture(symbol)
            f.kill()
            verify(f.info, never()).setSequence(anyInt(), anyInt())
            assertEquals(0, f.npcs.count())
            assertEquals(1, f.rewards)
        }
    }

    @Test fun `queued death plays every native part before removal and one reward`() {
        for (symbol in listOf("npc.tob_maiden_100", "npc.tob_xarpus_combat", "npc.jormungand", "npc.maggot_king")) {
            val f = Fixture(symbol)
            val sequences = NpcAnimationDeathProfiles.sequences.getValue(symbol)
            f.kill()
            val order = inOrder(f.info)
            for ((i, sequence) in sequences.withIndex()) {
                order.verify(f.info).setSequence(sequence.asRSCM(), 0)
                NpcAnimationDeathProfiles.forms[symbol]?.let { assertEquals(it[i].asRSCM(), f.npc.visType.id) }
                assertEquals(1, f.npcs.count())
                assertEquals(0, f.rewards)
                repeat(ServerCacheManager.getAnim(sequence.asRSCM())!!.tickDuration) { f.advance() }
            }
            assertEquals(0, f.npcs.count())
            assertEquals(1, f.rewards)
            assertEquals(symbol.asRSCM(), f.rewardType)
            f.advance()
            assertEquals(1, f.rewards)
        }
    }

    @Test fun `portals and flowers keep named death while suppressing human fallback`() {
        for ((symbol, sequence) in listOf("npc.pest_portal_1_active" to "seq.pest_portal_death_east",
            "npc.hespori_healer_active" to "seq.hespori_healer_alive_to_dead")) {
            val f = Fixture(symbol)
            f.kill()
            verify(f.info).setSequence(sequence.asRSCM(), 0)
            repeat(ServerCacheManager.getAnim(sequence.asRSCM())!!.tickDuration) { f.advance() }
            assertEquals(0, f.npcs.count())
            assertEquals(1, f.rewards)
        }
    }

    @Test fun `Maiden final dying form does not replay the first death part`() {
        val f = Fixture("npc.tob_maiden_dying_b")
        f.kill()
        verify(f.info).setSequence("seq.maiden_death_b".asRSCM(), 0)
        verify(f.info, never()).setSequence("seq.maiden_death_a".asRSCM(), 0)
        repeat(ServerCacheManager.getAnim("seq.maiden_death_b".asRSCM())!!.tickDuration) { f.advance() }
        assertEquals(0, f.npcs.count())
        assertEquals(1, f.rewards)
    }

    @Test fun `world spawn waits for both death parts before native respawn`() {
        val f = Fixture("npc.jormungand", temporary = false)
        f.kill()
        val durations = NpcAnimationDeathProfiles.sequences.getValue("npc.jormungand")
            .sumOf { ServerCacheManager.getAnim(it.asRSCM())!!.tickDuration }
        repeat(durations - 1) { f.advance(); assertFalse(f.npc.hidden) }
        assertEquals(0, f.rewards)
        f.advance()
        assertTrue(f.npc.hidden)
        assertEquals(1, f.npcs.count())
        assertEquals(1, f.rewards)
    }

    private class Fixture(symbol: String, temporary: Boolean = true) {
        val clock = MapClock(100)
        val events = EventBus()
        val npcs = NpcList()
        val players = PlayerList()
        val repo = NpcRepository(clock, NpcRegistry(npcs, CollisionFlagMap(), events), npcs)
        val player = Player().apply { slotId = 1; uuid = 1; assignUid(); coords = CoordGrid(3200, 3200) }
        val npc = Npc(ServerCacheManager.getNpc(symbol.asRSCM())!!, player.coords)
        val info = mock(NpcInfoProtocol::class.java)
        var rewards = 0
        var rewardType = -1
        private val deaths = NpcDeath(repo, players, mock(ObjRepository::class.java),
            setOf(NpcDeathDropHook { true }), setOf(NpcDeathKillHook { rewards++; rewardType = it.npc.visType.id }), mock(BossRespawnTimers::class.java))
        val launcher = StandardNpcAccessLauncher(StandardNpcAccessContextFactory(DefaultGameRandom(1),
            StandardNpcHitModifier(events), StandardNpcHitProcessor(players, events, emptySet())))
        private val queues = NpcQueueProcessor(events, launcher)
        init {
            players[1] = player
            npc.infoProtocol = info
            repo.add(npc, Int.MAX_VALUE)
            npc.respawns = !temporary
            npc.currentMapClock = clock.cycle; npc.processedMapClock = clock.cycle
            val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(NpcAnimationDeathScript(deaths)) { context.startup() }
            with(NpcAnimationStaticScript(deaths)) { context.startup() }
        }
        fun kill() { npc.recordDamage(player, npc.hitpoints); npc.hitpoints = 0; npc.queueDeath(); queues.process(npc) }
        fun advance() {
            // Native NpcPostTickProcess clears the previous tick's pending mask.
            npc.pendingSequence = EntitySeq.NULL
            clock.cycle++
            npc.currentMapClock = clock.cycle; npc.processedMapClock = clock.cycle
            npc.advanceActiveCoroutine()
        }
    }

    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
