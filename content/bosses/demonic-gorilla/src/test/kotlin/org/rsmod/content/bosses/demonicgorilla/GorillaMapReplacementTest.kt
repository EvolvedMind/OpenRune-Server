package org.rsmod.content.bosses.demonicgorilla

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.Mockito.mock
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.registry.loc.*
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.loc.*
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@ResourceLock("ServerCacheManager")
@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
internal class GorillaMapReplacementTest {
    @Test fun `startup removes underlying native arch and shutdown restores it`() {
        val f = Fixture()
        f.start()
        assertTrue(f.repo.findAll(GorillaAccessScript.OLD_ENTRANCE).none())
        assertTrue(f.repo.findLoc(GorillaAccessScript.ROPE_COORD, GorillaAccessScript.ROPE))
        with(f.script) { f.context.shutdown() }
        assertEquals(f.arch, f.repo.findAll(f.arch.coords).single())
    }

    @Test fun `same native arch at another location remains untouched`() {
        val f = Fixture()
        val other = f.arch.copy(coords = f.arch.coords.translateX(1))
        f.static(other)
        f.start()
        assertEquals(other, f.repo.findAll(other.coords).single())
        assertTrue(f.repo.findAll(f.arch.coords).none())
    }

    @Test fun `shutdown does not overwrite a subsequent editor placement`() {
        val f = Fixture()
        f.start()
        val replacement = f.arch.copy(entity = LocEntity("loc.dangersign".asRSCM(), 10, 0))
        assertTrue(f.repo.add(replacement, Int.MAX_VALUE))
        with(f.script) { f.context.shutdown() }
        assertEquals(replacement, f.repo.findAll(f.arch.coords).single())
    }

    private class Fixture {
        val collision = sharedCollision
        val zones = LocZoneStorage()
        val bus = EventBus()
        val repo = LocRepository(MapClock(), LocRegistry(zones, LocRegistryNormal(ZoneUpdateMap(), collision, zones), mock(LocRegistryRegion::class.java)), mock(RegionRegistry::class.java))
        val script = GorillaAccessScript(repo, mock(ProtectedAccessLauncher::class.java))
        val context = ScriptContext(bus, CheatCommandMap(), EngineQueueCache())
        val arch = LocInfo(2, GorillaAccessScript.OLD_ENTRANCE, LocEntity("loc.mm2_cave_boss_waterfall_small".asRSCM(), 10, 0))
        init {
            for (coord in listOf(arch.coords, arch.coords.translateX(1), GorillaAccessScript.ENTRANCE, GorillaAccessScript.SIGN_COORD, GorillaAccessScript.ROPE_COORD)) collision.allocateIfAbsent(coord.x, coord.z, coord.level)
            static(arch)
            with(script) { context.startup() }
        }
        fun static(loc: LocInfo) {
            zones.mapLocs[ZoneKey.from(loc.coords), LocZoneKey(ZoneGrid.from(loc.coords), loc.layer)] = loc.entity
        }
        fun start() { bus.publish(GameLifecycle.Startup) }
    }
    companion object {
        private val sharedCollision = CollisionFlagMap()

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() }
    }
}
