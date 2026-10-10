package org.rsmod.content.other.npc.animations

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.Mockito.*
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.npc.NpcInfoProtocol
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@ResourceLock("ServerCacheManager")
@OptIn(org.rsmod.annotations.InternalApi::class)
class NpcAnimationSpawnRoutesTest {
    @Test fun `world create advances all emergence forms to their native active form`() {
        for ((symbol, profile) in NpcAnimationSpawnProfiles.profiles) {
            val f = Fixture(symbol)
            val identity = f.npc.uid
            val hp = f.npc.hitpoints
            val coords = f.npc.coords
            assertTrue(f.npc.movementLocked)
            assertTrue(f.npc.ignoreCombatInteractions)
            assertTrue(f.npc.actionDelay > f.clock.cycle)
            f.tick()
            val order = inOrder(f.info)
            for (part in profile.parts) {
                order.verify(f.info).setSequence(part.sequence.asRSCM(), 0)
                assertEquals(part.npc.asRSCM(), f.npc.visType.id)
                assertTrue(f.npc.actionDelay > f.clock.cycle)
                repeat(ServerCacheManager.getAnim(part.sequence.asRSCM())!!.tickDuration) { f.tick() }
            }
            assertEquals(profile.active.asRSCM(), f.npc.visType.id)
            assertEquals(symbol.asRSCM(), f.npc.id)
            assertEquals(identity, f.npc.uid)
            assertEquals(hp, f.npc.hitpoints)
            assertEquals(coords, f.npc.coords)
            assertFalse(f.npc.movementLocked)
            assertFalse(f.npc.ignoreCombatInteractions)
            assertTrue(f.npc.actionDelay > f.clock.cycle)
            f.tick()
            assertTrue(f.npc.actionDelay <= f.clock.cycle)
        }
    }

    @Test fun `deleting an emerging actor cancels remaining visual work`() {
        val f = Fixture("npc.ttrek2_zombie_diff_1_ver_1_1")
        f.tick()
        f.repo.del(f.npc, Int.MAX_VALUE)
        clearInvocations(f.info)
        repeat(30) { f.tick() }
        verifyNoInteractions(f.info)
        assertFalse(f.npc.movementLocked)
        assertFalse(f.npc.ignoreCombatInteractions)
    }

    @Test fun `death during emergence stops stages and restores combat control`() {
        val f = Fixture("npc.ttrek2_zombie_diff_1_ver_1_1")
        f.tick()
        f.npc.hitpoints = 0
        clearInvocations(f.info)
        repeat(30) { f.tick() }
        verifyNoInteractions(f.info)
        assertFalse(f.npc.movementLocked)
        assertFalse(f.npc.ignoreCombatInteractions)
    }

    @Test fun `accepted Zulrah is untouched by ordinary animation lifecycle`() {
        val f = Fixture("npc.snakeboss_boss_ranged")
        clearInvocations(f.info)
        repeat(30) { f.tick() }
        verifyNoInteractions(f.info)
        assertNull(f.npc.transmog)
    }

    private class Fixture(symbol: String) {
        val clock = MapClock(100)
        val events = EventBus()
        val npcs = NpcList()
        val repo = NpcRepository(clock, NpcRegistry(npcs, CollisionFlagMap(), events), npcs)
        val npc = Npc(ServerCacheManager.getNpc(symbol.asRSCM())!!, CoordGrid(3200, 3200))
        val info = mock(NpcInfoProtocol::class.java)
        init {
            val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(NpcAnimationSpawnScript(NpcAnimationSpawns(clock))) { context.startup() }
            npc.infoProtocol = info
            repo.add(npc, Int.MAX_VALUE)
        }
        fun tick() {
            npc.pendingSequence = EntitySeq.NULL
            clock.cycle++
            npc.currentMapClock = clock.cycle; npc.processedMapClock = clock.cycle
            events.publish(GameLifecycle.LateCycle)
        }
    }

    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
