package org.rsmod.content.bosses.shaman

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.Mockito.*
import org.rsmod.api.bosses.runtime.*
import org.rsmod.api.combat.formulas.*
import org.rsmod.api.npc.access.*
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.hit.modifier.StandardPlayerHitModifier
import org.rsmod.api.player.protect.*
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.*
import org.rsmod.game.entity.npc.*
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitType
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.*
import org.rsmod.game.queue.*
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

@ResourceLock("ServerCacheManager")
@OptIn(org.rsmod.annotations.InternalApi::class)
internal class ShamanCombatTest {
    @Test fun `native approach producer reaches ranged handler for every ordinary variant`() {
        for (type in LizardmanShaman.TYPES) {
            val f = Fixture(type)
            assertEquals(8, f.npc.attackRange)
            f.attack("ranged"); f.tick(5)
            verify(f.info).setSequence("seq.shay_lizard_warrior_attack_ranged".asRSCM(), 0)
            val hits = f.hits(); assertEquals(1, hits.size)
            assertEquals(HitType.Ranged, hits.single().type); assertTrue(hits.single().damage in 1..21)
            assertEquals(1, mockingDetails(f.world).invocations.count { it.method.name.startsWith("projAnim") })
        }
    }

    @Test fun `ranged projectile reaches the real health and hitsplat consumer`() {
        val f = Fixture(); f.attack("ranged"); f.tick(6)
        val hit = f.hits().single()
        val context = ProtectedAccessContextFactory.empty().copy(getEventBus = { f.bus }, getNpcList = { f.npcs }, getPlayerList = { f.players }, getRandom = { DefaultGameRandom(1) })
        val access = ProtectedAccess(f.player, GameCoroutine(), context)
        with(org.rsmod.api.player.hit.processor.StandardPlayerHitProcessor) { access.process(hit) }
        assertEquals(99 - hit.damage, f.player.statMap.getCurrentLevel("stat.hitpoints").toInt())
    }

    @Test fun `protect ranged blocks the normal projectile but not acid`() {
        val f = Fixture()
        ProtectedAccessLauncher.withProtectedAccess(f.player, ProtectedAccessContextFactory.empty()) {
            vars["varbit.prayer_protectfrommissiles"] = 1
        }
        f.attack("ranged"); f.tick(6); assertEquals(0, f.hits().single().damage)
        f.clearHits(); f.attack("acid"); f.tick(3)
        assertTrue(f.hits().any { it.type == HitType.Typeless && it.damage in 25..30 })
        assertTrue(f.player.vars["varp.poison_severity"] > 0)
    }

    @Test fun `acid hits its original tile and moving out avoids damage and poison`() {
        val f = Fixture(); f.attack("acid"); f.player.coords = f.player.coords.translate(3, 0); f.tick(3)
        assertTrue(f.hits().isEmpty()); assertEquals(0, f.player.vars["varp.poison_severity"])
        val splash = mockingDetails(f.world).invocations.single { it.method.name.startsWith("spotanimMap") }
        assertEquals(CoordGrid(1451, 3696).packed, splash.arguments[1])
    }

    @Test fun `all five worn tier five pieces negate acid damage while poison remains`() {
        val f = Fixture(); f.wear(5); f.attack("acid"); f.tick(3)
        assertEquals(0, f.hits().first().damage)
        assertTrue(f.player.vars["varp.poison_severity"] > 0)
    }

    @Test fun `partial Shayzien sets reduce acid only and inventory pieces do not count`() {
        val f = Fixture()
        for (count in 0..5) { f.wear(count); assertEquals(30 * (5 - count) / 5, ShayzienProtection.acidDamage(f.player, 30)) }
        f.wear(0); assertEquals(30, ShayzienProtection.acidDamage(f.player, 30))
        f.player.worn[0] = InvObj("obj.shayzien_helm_4")
        assertEquals(30, ShayzienProtection.acidDamage(f.player, 30))
    }

    @Test fun `jump snapshots landing tile emits native landing sequence and releases movement`() {
        val f = Fixture(); val tile = f.player.coords
        f.attack("jump"); assertTrue(f.npc.movementLocked); f.tick(5)
        assertEquals(tile.translate(-1, -1), f.npc.coords)
        assertTrue(f.npc.interaction != null); assertTrue(f.npc.routeRequest != null)
        verify(f.info).setSequence("seq.shayzien_lizard_boss_land".asRSCM(), 0)
        assertTrue(f.hits().single().damage in 20..25)
        f.tick(2); assertFalse(f.npc.movementLocked)
    }

    @Test fun `jump can be dodged and blocked destination is never used`() {
        val f = Fixture(); val origin = f.npc.coords
        f.attack("jump"); f.player.coords = f.player.coords.translate(4, 0); f.tick(7)
        assertTrue(f.hits().isEmpty()); assertFalse(f.npc.movementLocked)
        val g = Fixture(); g.attack("jump")
        g.collision.add(1450, 3695, 0, CollisionFlag.LOC); g.tick(7)
        assertEquals(origin, g.npc.coords); assertTrue(g.hits().isEmpty()); assertFalse(g.npc.movementLocked)
    }

    @Test fun `three finite spawns follow their target explode and cannot respawn`() {
        val f = Fixture(); f.attack("summon"); assertEquals(4, f.npcs.count())
        val minions = f.npcs.filter { it !== f.npc }.toList(); assertEquals(3, minions.size)
        f.tick(1); assertTrue(minions.all { it.routeDestination.isNotEmpty() })
        f.tick(7); assertEquals(1, f.npcs.count()); assertEquals(3, f.hits().size)
        assertTrue(f.hits().all { it.type == HitType.Typeless && it.damage in 8..10 })
        assertTrue(minions.all { !it.isSlotAssigned && it.lifecycleRespawnCycle < 0 })
    }

    @Test fun `logout departure death respawn and unload cancel every pending special`() {
        for (ability in listOf("acid", "jump", "summon")) for (end in 0..4) {
            val f = Fixture(); f.attack(ability)
            when (end) {
                0 -> f.players.remove(1)
                1 -> f.player.coords = CoordGrid(3222, 3218)
                2 -> f.npc.hitpoints = 0
                3 -> f.bus.publish(NpcStateEvents.Respawn(f.npc))
                4 -> with(f.script) { f.context.shutdown() }
            }
            f.tick(15); assertTrue(f.hits().isEmpty(), "$ability/$end")
            assertFalse(f.npc.movementLocked); assertEquals(1, f.npcs.count()); assertTrue(f.queues.isEmpty)
        }
    }

    @Test fun `melee continues to use native attack block and death definitions`() {
        val f = Fixture(); f.player.coords = f.npc.coords.translate(-1, 0)
        f.attack("melee"); verify(f.info).setSequence("seq.shay_lizard_warrior_attack_melee".asRSCM(), 0)
        assertEquals(HitType.Melee, f.hits().single().type)
        assertEquals("seq.shay_lizard_warrior_defend".asRSCM(), f.npc.type.param(org.rsmod.api.config.refs.BaseParams.defend_anim).id)
        assertEquals("seq.shay_lizard_warrior_death".asRSCM(), f.npc.type.param(org.rsmod.api.config.refs.BaseParams.death_anim).id)
    }

    private class Fixture(type: String = LizardmanShaman.TYPES.first()) {
        val clock = MapClock(100); val bus = EventBus(); val collision = CollisionFlagMap(); val npcs = NpcList(); val players = PlayerList()
        val queues = WorldQueueList(); val world = mock(WorldRepository::class.java)
        val player = Player().apply {
            slotId = 1; uuid = 1; assignUid(); coords = CoordGrid(1451, 3696)
            statMap.setBaseLevel("stat.hitpoints", 99); statMap.setCurrentLevel("stat.hitpoints", 99)
            worn = Inventory(ServerCacheManager.getInventory("inv.worn".asRSCM())!!, arrayOfNulls(14))
        }
        val repo = NpcRepository(clock, NpcRegistry(npcs, collision, bus), npcs)
        val deps = BossDeps(DefaultGameRandom(1), world, repo, mock(LocRepository::class.java), players, clock, queues, collision,
            EncounterRegistry(clock), BossExtensionRegistry(), mock(AccuracyFormulae::class.java) { true }, mock(MaxHitFormulae::class.java), StandardPlayerHitModifier(bus))
        val script = LizardmanShaman(deps, AiPlayerInteractions(bus, players)); val context = ScriptContext(bus, CheatCommandMap(), EngineQueueCache())
        val npc = Npc(ServerCacheManager.getNpc(type.asRSCM())!!, CoordGrid(1457, 3696)); val info = mock(NpcInfoProtocol::class.java)
        init {
            for (x in 1440..1470) for (z in 3685..3710) collision.set(x, z, 0, 0)
            players[1] = player; with(script) { context.startup() }; npc.infoProtocol = info; repo.add(npc, Int.MAX_VALUE)
        }
        fun attack(name: String) {
            deps.encounter(npc).forceNext(name)
            val producer = AiPlayerInteractions(bus, players)
            producer.interactAp(npc, player, InteractionOp.Op2)
            val trigger = checkNotNull(producer.apTrigger(npc, player, InteractionOp.Op2))
            val access = StandardNpcAccess(npc, GameCoroutine(), StandardNpcAccessContext({ DefaultGameRandom(1) }, { StandardNpcHitModifier(bus) }, { StandardNpcHitProcessor(players, bus, emptySet()) }))
            npc.launch { bus.publish(access, trigger) }
        }
        fun tick(count: Int) { repeat(count) {
            clock.cycle++
            val it = queues.iterator(); while (it.hasNext()) { val q = it.next(); if (--q.remainingCycles <= 0) { it.remove(); q.action() } }; it.cleanUp()
        } }
        fun hits(): List<Hit> {
            val hits = mutableListOf<Hit>(); val it = player.queueList.iterator() ?: return hits
            while (it.hasNext()) { (it.next().args as? Hit)?.let(hits::add) }; it.cleanUp(); return hits
        }
        fun clearHits() { player.queueList.removeAll("queue.hit") }
        fun wear(count: Int) {
            player.worn.objs.fill(null)
            listOf(0 to "helm", 4 to "body", 7 to "legs", 9 to "gloves", 10 to "boots").take(count).forEach { (slot, piece) ->
                player.worn[slot] = InvObj("obj.shayzien_${piece}_5")
            }
        }
    }
    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
