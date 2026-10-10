package org.rsmod.content.other.npc.animations

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.Mockito.*
import org.rsmod.api.death.*
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.game.process.npc.NpcQueueProcessor
import org.rsmod.api.npc.access.*
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.npc.respawn.BossRespawnTimers
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.*
import org.rsmod.game.entity.npc.NpcInfoProtocol
import org.rsmod.game.hit.*
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@ResourceLock("ServerCacheManager")
@OptIn(org.rsmod.annotations.InternalApi::class)
class NpcAnimationCombatRoutesTest {
    @Test fun `real hit activates every dormant form once and keeps native identity and damage`() {
        for ((symbol, profile) in NpcAnimationCombatProfiles.profiles) {
            val f = Fixture(symbol)
            val identity = f.npc.uid
            val hp = f.npc.hitpoints
            assertFalse(f.npc.movementLocked)
            f.hit(1)
            assertEquals(hp - 1, f.npc.hitpoints)
            assertTrue(f.npc.movementLocked)
            assertTrue(f.npc.actionDelay > f.clock.cycle)
            f.tick()
            val order = inOrder(f.info)
            for (part in profile.parts) {
                order.verify(f.info).setSequence(part.sequence.asRSCM(), 0)
                assertEquals(part.npc.asRSCM(), f.npc.visType.id)
                repeat(ServerCacheManager.getAnim(part.sequence.asRSCM())!!.tickDuration) { f.tick() }
            }
            assertEquals(profile.active.asRSCM(), f.npc.visType.id)
            assertEquals(symbol.asRSCM(), f.npc.id)
            assertEquals(identity, f.npc.uid)
            assertFalse(f.npc.movementLocked)
            assertFalse(f.npc.ignoreCombatInteractions)
            clearInvocations(f.info)
            f.hit(1)
            repeat(12) { f.tick() }
            verify(f.info, never()).setSequence(anyInt(), anyInt())
        }
    }

    @Test fun `lethal first hit uses active body and native death queue without starting emergence`() {
        for ((symbol, profile) in NpcAnimationCombatProfiles.profiles) {
            val f = Fixture(symbol)
            f.hit(f.npc.hitpoints)
            assertFalse(f.npc.movementLocked)
            f.queues.process(f.npc)
            assertEquals(profile.active.asRSCM(), f.npc.visType.id)
            verify(f.info).setSequence(profile.death.single().asRSCM(), 0)
            val duration = ServerCacheManager.getAnim(profile.death.single().asRSCM())!!.tickDuration
            repeat(duration) { f.tick() }
            assertEquals(0, f.npcs.count())
            assertEquals(1, f.rewards)
        }
    }

    @Test fun `foreign visual change cancels transition instead of overwriting another controller`() {
        val f = Fixture("npc.wyrm_dark")
        f.hit(1)
        f.npc.transmog(ServerCacheManager.getNpc("npc.wyrm_light".asRSCM())!!, Int.MAX_VALUE)
        clearInvocations(f.info)
        repeat(12) { f.tick() }
        verifyNoInteractions(f.info)
        assertEquals("npc.wyrm_light".asRSCM(), f.npc.visType.id)
        assertFalse(f.npc.movementLocked)
        assertFalse(f.npc.ignoreCombatInteractions)
        assertEquals(-1, f.npc.actionDelay)
    }

    private class Fixture(symbol: String) {
        val clock = MapClock(100)
        val events = EventBus()
        val npcs = NpcList()
        val players = PlayerList()
        val repo = NpcRepository(clock, NpcRegistry(npcs, CollisionFlagMap(), events), npcs)
        val player = Player().apply { slotId = 1; uuid = 1; assignUid(); coords = CoordGrid(3200, 3200) }
        val npc = Npc(ServerCacheManager.getNpc(symbol.asRSCM())!!, player.coords)
        val info = mock(NpcInfoProtocol::class.java)
        var rewards = 0
        private val deaths = NpcDeath(repo, players, mock(ObjRepository::class.java),
            setOf(NpcDeathDropHook { true }), setOf(NpcDeathKillHook { rewards++ }), mock(BossRespawnTimers::class.java))
        private val context = StandardNpcAccessContext({ DefaultGameRandom(1) },
            { StandardNpcHitModifier(events) }, { StandardNpcHitProcessor(players, events, emptySet()) })
        private val access = StandardNpcAccess(npc, GameCoroutine("combat-animation-form"), context)
        val queues = NpcQueueProcessor(events, StandardNpcAccessLauncher(StandardNpcAccessContextFactory(
            DefaultGameRandom(1), StandardNpcHitModifier(events), StandardNpcHitProcessor(players, events, emptySet()))))
        init {
            players[1] = player
            npc.infoProtocol = info
            val script = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(NpcAnimationSpawnScript(NpcAnimationSpawns(clock))) { script.startup() }
            with(NpcAnimationDeathScript(deaths)) { script.startup() }
            repo.add(npc, Int.MAX_VALUE)
            npc.respawns = false
            npc.currentMapClock = clock.cycle; npc.processedMapClock = clock.cycle
        }
        fun hit(damage: Int) {
            val mark = Hitmark.fromPlayerSource(1, 1, 1, damage, 0, player.slotId)
            access.processQueuedHit(Hit(HitType.Melee, mark, player.uid.packed, null, null))
        }
        fun tick() {
            npc.pendingSequence = EntitySeq.NULL
            clock.cycle++
            npc.currentMapClock = clock.cycle; npc.processedMapClock = clock.cycle
            npc.advanceActiveCoroutine()
            events.publish(GameLifecycle.LateCycle)
        }
    }

    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
