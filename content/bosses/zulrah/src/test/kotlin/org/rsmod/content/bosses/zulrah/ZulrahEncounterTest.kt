package org.rsmod.content.bosses.zulrah

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossExtensionRegistry
import org.rsmod.api.bosses.runtime.EncounterRegistry
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.InstanceSpec
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.region.Region
import org.rsmod.game.region.zone.RegionZoneCopy
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag
import sun.misc.Unsafe

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class ZulrahEncounterTest {
    @Test
    fun `joining twice creates one mapped boss without a respawn`() {
        val f = Fixture()
        f.start()
        val boss = f.boss()
        f.start()
        assertEquals(1, f.npcs.count())
        assertEquals(CoordGrid(3226, 3265), boss.coords)
        assertEquals(500, boss.hitpoints)
        assertFalse(boss.respawns)
        assertSame(NpcAttackValidateResult.Deny::class, f.encounters.validate(f.player, boss)::class)
        f.advance(4)
        assertSame(NpcAttackValidateResult.BypassSingleWayPvnRestriction, f.encounters.validate(f.player, boss))
    }

    @Test
    fun `native hide reveal and form change preserve health debuffs and damage credit`() {
        val f = Fixture()
        f.start()
        val boss = f.boss()
        val firstUid = boss.uid
        boss.hitpoints = 287
        boss.defenceLvl = 260
        boss.damageContributions.record(f.player, 213)
        boss.heroPoints(f.player, 213)
        f.advance(30)
        assertTrue(boss.hidden)
        assertTrue(f.encounters.validate(f.player, boss) is NpcAttackValidateResult.Deny)
        f.advance(3)
        assertFalse(boss.hidden)
        assertEquals("npc.snakeboss_boss_melee", boss.visType.internalName)
        assertNotEquals(firstUid, boss.uid)
        assertEquals(287, boss.hitpoints)
        assertEquals(260, boss.defenceLvl)
        assertEquals(213, boss.damageContributions.damageBy(f.player))
        assertSame(f.player, boss.findHero(f.players))
        assertEquals(1, f.npcs.count())
    }

    @Test
    fun `leaving during dive cancels pending hide and does not resurrect the boss`() {
        val f = Fixture()
        f.start()
        f.advance(28)
        f.encounters.stop(f.session.id)
        f.encounters.stop(f.session.id)
        f.advance(40)
        assertEquals(0, f.npcs.count())
    }

    @Test
    fun `logout tears down the encounter without waiting for instance expiry`() {
        val f = Fixture()
        f.start()
        f.player.pendingLogout = true
        f.advance(1)
        assertEquals(0, f.npcs.count())
    }

    @Test
    fun `other players cannot damage another owners encounter`() {
        val f = Fixture()
        f.start()
        f.advance(4)
        val other = Player().apply { uuid = 2L }
        assertTrue(f.encounters.validate(other, f.boss()) is NpcAttackValidateResult.Deny)
    }

    @Test
    fun `death returns the mapped platform loot tile once and stops further phases`() {
        val f = Fixture()
        f.start()
        val boss = f.boss()
        boss.hitpoints = 0
        assertEquals(CoordGrid(3228, 3261), f.encounters.beginDeath(boss))
        assertNull(f.encounters.beginDeath(boss))
        f.advance(100)
        assertEquals("npc.snakeboss_boss_ranged", boss.visType.internalName)
        assertEquals(0, boss.hitpoints)
    }

    private class Fixture {
        val clock = MapClock(100)
        val events = EventBus()
        val player = Player().apply {
            uuid = 1L
            slotId = 1
            assignUid()
            coords = CoordGrid(3228, 3261)
            previousCoords = coords
            currentMapClock = clock.cycle
            processedMapClock = clock.cycle
        }
        val players = PlayerList().apply { this[player.slotId] = player }
        val collision = CollisionFlagMap().apply {
            for (x in 3192..3344) for (z in 3192..3344) {
                allocateIfAbsent(x, z, 0)
                add(x, z, 0, CollisionFlag.FLOOR)
            }
        }
        val npcs = NpcList()
        val npcRegistry = NpcRegistry(npcs, collision, events)
        val npcRepo = NpcRepository(clock, npcRegistry, npcs)
        val resolver = InstanceAreaResolver()
        val instances = InstanceManager(unused(RegionRepository::class.java), npcRepo, players, events, resolver, clock, collision)
        val session: InstanceSession
        val encounters: ZulrahEncounterManager

        init {
            val area = ZulrahInstance.ARENA
            val placement = (resolver.resolve(area) as InstanceAreaResolver.Result.Ready).placement
            val spec = InstanceSpec(0, 1, 2000, 100, true, area, -1)
            session = InstanceSession(InstanceId(1), mutableSetOf(), 1L, "zulrah", spec, placement, InstanceAccess.Private)
            session.addOccupant(1L)
            mapField<InstanceId, InstanceSession>(instances, "sessions")[session.id] = session
            mapField<Long, InstanceId>(instances, "playerIndex")[1L] = session.id
            val region = Region(CoordGrid(3200, 3200), CoordGrid(3328, 3328), 1, 1)
            for (x in 280..287) for (z in 376..391) {
                region.registerZone(RegionZoneCopy(ZoneKey(x, z, 0), 0, null), ZoneKey(400 + x - 280, 400 + z - 376, 0))
            }
            mapField<InstanceId, Region>(instances, "regions")[session.id] = region
            val deps = BossDeps(
                FixedRandom, WorldRepository(ZoneUpdateMap()), npcRepo, players, clock,
                WorldQueueList(), collision, EncounterRegistry(), BossExtensionRegistry(),
                unused(AccuracyFormulae::class.java), unused(MaxHitFormulae::class.java), NoopPlayerHitModifier,
            )
            encounters = ZulrahEncounterManager(deps, instances, unused(LocRepository::class.java), AiPlayerInteractions(events, players))
        }

        fun start() = encounters.start(player, session)

        fun boss(): Npc = npcs.single().also { assertNotNull(it) }

        fun advance(ticks: Int) {
            repeat(ticks) {
                clock.tick()
                player.currentMapClock = clock.cycle
                player.processedMapClock = clock.cycle
                for (npc in npcs.toList()) {
                    npc.currentMapClock = clock.cycle
                    npc.processedMapClock = clock.cycle
                    if (npc.hidden && npc.lifecycleRevealCycle > 0 && npc.lifecycleRevealCycle <= clock.cycle) {
                        npcRegistry.reveal(npc)
                        npc.lifecycleRevealCycle = 0
                    }
                }
                encounters.tick()
            }
        }
    }

    private object FixedRandom : GameRandom {
        override fun of(maxExclusive: Int): Int = 0
        override fun of(minInclusive: Int, maxInclusive: Int): Int = minInclusive
        override fun randomDouble(): Double = 0.0
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadCache() {
            ServerCacheManager.init(240).close()
        }

        @Suppress("UNCHECKED_CAST")
        private fun <K, V> mapField(target: Any, name: String): MutableMap<K, V> =
            target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target) as MutableMap<K, V>

        private fun <T> unused(type: Class<T>): T {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            return type.cast((field.get(null) as Unsafe).allocateInstance(type))
        }
    }
}
