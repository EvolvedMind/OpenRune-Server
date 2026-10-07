package org.rsmod.content.bosses.tormenteddemon

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bosses.runtime.*
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RouteFactory
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.*
import org.rsmod.game.entity.npc.NpcInfoProtocol
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@ResourceLock("ServerCacheManager")
@OptIn(InternalApi::class)
class TormentedDemonCombatTest {
    private lateinit var script: TormentedDemon
    private lateinit var context: ScriptContext
    private lateinit var player: Player
    private lateinit var npc: Npc
    private lateinit var clock: MapClock
    private lateinit var queues: WorldQueueList
    private lateinit var registry: EncounterRegistry
    private lateinit var modifier: StandardNpcHitModifier
    private lateinit var processor: StandardNpcHitProcessor
    private lateinit var info: NpcInfoProtocol
    private lateinit var bus: EventBus
    private lateinit var playerHitModifier: PlayerHitModifier

    @BeforeEach fun setup() {
        clock = MapClock(100)
        queues = WorldQueueList()
        bus = EventBus()
        registry = EncounterRegistry(clock)
        player = Player().apply {
            slotId = 1; uuid = 1; assignUid(); coords = CoordGrid(4075, 4424)
            worn = Inventory(ServerCacheManager.getInventory("inv.worn".asRSCM())!!, arrayOfNulls(14))
            statMap.setCurrentLevel("stat.hitpoints", 99)
        }
        val players = PlayerList().apply { this[1] = player }
        val collision = CollisionFlagMap()
        val npcs = NpcList()
        val repo = NpcRepository(clock, NpcRegistry(npcs, collision, bus), npcs)
        playerHitModifier = mock(PlayerHitModifier::class.java)
        val deps = BossDeps(mock(GameRandom::class.java) { 0 }, mock(WorldRepository::class.java), repo,
            mock(LocRepository::class.java), players, clock, queues, collision, registry,
            BossExtensionRegistry(), mock(AccuracyFormulae::class.java), mock(MaxHitFormulae::class.java), playerHitModifier)
        script = TormentedDemon(deps, mock(RouteFactory::class.java), mock(AttackTypes::class.java))
        context = ScriptContext(bus, CheatCommandMap(), EngineQueueCache())
        with(script) { context.startup() }
        npc = Npc(ServerCacheManager.getNpc("npc.tormented_demon_1".asRSCM())!!, CoordGrid(4075, 4427))
        info = mock(NpcInfoProtocol::class.java)
        npc.infoProtocol = info
        repo.add(npc, Int.MAX_VALUE)
        bus.publish(NpcStateEvents.Spawn(npc))
        modifier = StandardNpcHitModifier(bus)
        processor = StandardNpcHitProcessor(players, bus, emptySet())
    }

    private fun hit(type: HitType, damage: Int): Hit = npc.queueHit(player, 1, type, damage, modifier)
    private fun land(hit: Hit) {
        val access = StandardNpcAccess(npc, GameCoroutine("td-impact"), StandardNpcAccessContext({ DefaultGameRandom(1) }, { modifier }, { processor }))
        with(processor) { access.process(hit) }
    }
    private fun tick() {
        clock.cycle++
        val iterator = queues.iterator()
        while (iterator.hasNext()) {
            val queue = iterator.next()
            if (--queue.remainingCycles <= 0) { iterator.remove(); queue.action() }
        }
        iterator.cleanUp()
    }

    @Test fun `shield reduces ordinary damage and opening hit creates exactly one unshielded hit`() {
        val first = hit(HitType.Ranged, 100)
        assertEquals(80, first.damage)
        assertEquals(0, npc.vars["varn.td_shield_up"])
        assertEquals(100, hit(HitType.Ranged, 100).damage)
        assertEquals(1, npc.vars["varn.td_shield_up"])
        assertEquals(80, hit(HitType.Ranged, 100).damage)
    }

    @Test fun `overhead blocks ordinary attacks without counting rolled damage`() {
        repeat(3) { land(hit(HitType.Melee, 200)) }
        assertEquals(600, npc.hitpoints)
        assertTrue(npc.damageContributions.isEmpty)
        assertEquals(1, npc.vars["varn.td_overhead_style"])
    }

    @Test fun `prayer changes only after 150 actual health loss`() {
        val first = hit(HitType.Ranged, 100)
        val second = hit(HitType.Ranged, 70)
        assertEquals(1, npc.vars["varn.td_overhead_style"])
        land(first)
        assertEquals(1, npc.vars["varn.td_overhead_style"])
        land(second)
        assertEquals(450, npc.hitpoints)
        assertEquals(2, npc.vars["varn.td_overhead_style"])
        assertEquals(106, registry.of(npc).busyUntil)
        assertEquals(0, hit(HitType.Ranged, 100).damage)
    }

    @Test fun `defenceless phase starts after 30 ticks and swaps the body model`() {
        hit(HitType.Ranged, 0)
        repeat(29) { tick() }
        verify(info, never()).setTransmog(anyInt())
        tick()
        assertEquals(1, npc.vars["varn.guaranteed_hit"])
        verify(info).setTransmog("npc.tormented_demon_defenceless_1".asRSCM())
        verify(info, never()).setBodyModel(anyInt())
        assertEquals("npc.tormented_demon_1".asRSCM(), npc.type.id)
        assertEquals(600, npc.hitpoints)
        repeat(10) { tick() }
        verify(info, times(1)).setTransmog("npc.tormented_demon_defenceless_1".asRSCM())
    }

    @Test fun `leaving heals and restores the model and shield without delayed model reappearance`() {
        land(hit(HitType.Ranged, 100))
        repeat(30) { tick() }
        player.coords = CoordGrid(3222, 3218)
        tick()
        assertEquals(600, npc.hitpoints)
        assertEquals(0, npc.vars["varn.guaranteed_hit"])
        assertEquals(1, npc.vars["varn.td_shield_up"])
        verify(info).resetTransmog("npc.tormented_demon_1".asRSCM())
        repeat(60) { tick() }
        verify(info, times(1)).setTransmog("npc.tormented_demon_defenceless_1".asRSCM())
    }

    @Test fun `old impacts cannot alter a reset fight`() {
        val queued = hit(HitType.Ranged, 200)
        script.resetFight(npc)
        land(queued)
        assertEquals(1, npc.vars["varn.td_overhead_style"])
        assertEquals(600, npc.hitpoints)
    }

    @Test fun `logout cleans up active phase`() {
        hit(HitType.Magic, 0)
        repeat(30) { tick() }
        player.pendingLogout = true
        tick()
        assertEquals(0, npc.vars["varn.guaranteed_hit"])
        verify(info).resetTransmog("npc.tormented_demon_1".asRSCM())
    }

    @Test fun `killing the defenceless actor restores its native identity and cannot cancel the lethal hit`() {
        hit(HitType.Ranged, 0)
        repeat(30) { tick() }
        land(hit(HitType.Magic, 1000))
        assertEquals(0, npc.hitpoints)
        assertTrue(npc.transmog == null)
        verify(info).resetTransmog("npc.tormented_demon_1".asRSCM())
        assertTrue("queue.death" in npc.queueList)
    }

    @Test fun `shutdown restores the visual and stops pending phase changes`() {
        hit(HitType.Ranged, 0)
        repeat(30) { tick() }
        with(script) { context.shutdown() }
        assertTrue(npc.transmog == null)
        repeat(60) { tick() }
        verify(info, times(1)).setTransmog(anyInt())
    }

    @Test fun `fire bomb snapshots the tile resets the model and queues one 40 to 45 damage hit`() {
        hit(HitType.Magic, 0)
        repeat(30) { tick() }
        script.fireBomb(npc, player)
        assertTrue(npc.transmog == null)
        assertEquals(0, npc.vars["varn.td_shield_up"])
        repeat(3) { tick() }
        assertEquals(0, player.queueList.count("queue.hit"))
        tick()
        assertEquals(1, player.queueList.count("queue.hit"))
        val queue = player.queueList.iterator()!!
        val hits = mutableListOf<Hit>()
        while (queue.hasNext()) (queue.next().args as? Hit)?.let(hits::add)
        assertEquals(HitType.Typeless, hits.single().type)
        assertTrue(hits.single().damage in 40..45)
    }

    @Test fun `dodging a bomb avoids the incoming hit`() {
        hit(HitType.Magic, 0)
        script.fireBomb(npc, player)
        repeat(2) { tick() }
        player.coords = player.coords.translate(3, 0)
        repeat(2) { tick() }
        assertEquals(0, player.queueList.count("queue.hit"))
        verifyNoInteractions(playerHitModifier)
    }

    @Test fun `reset cancels a pending bomb without damaging the returning player`() {
        hit(HitType.Magic, 0)
        script.fireBomb(npc, player)
        script.resetFight(npc)
        repeat(4) { tick() }
        assertEquals(0, player.queueList.count("queue.hit"))
        verifyNoInteractions(playerHitModifier)
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() }
    }
}
