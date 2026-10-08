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
import org.rsmod.api.combat.manager.PlayerAttackManager
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.stat.statAdvance
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
    private lateinit var attackManager: PlayerAttackManager
    private lateinit var styles: AttackStyles
    private lateinit var repo: NpcRepository
    private lateinit var controller: TormentedDemonController
    private lateinit var npcs: NpcList
    private lateinit var maxHits: MaxHitFormulae
    private lateinit var types: AttackTypes
    private lateinit var playerHitModifier: PlayerHitModifier

    @BeforeEach fun setup() {
        clock = MapClock(100)
        queues = WorldQueueList()
        bus = EventBus()
        registry = EncounterRegistry(clock)
        player = Player().apply {
            slotId = 1; uuid = 1; assignUid(); coords = CoordGrid(4075, 4424)
            worn = Inventory(ServerCacheManager.getInventory("inv.worn".asRSCM())!!, arrayOfNulls(14))
            statMap.setBaseLevel("stat.hitpoints", 99.toByte())
            statMap.setCurrentLevel("stat.hitpoints", 99)
        }
        val players = PlayerList().apply { this[1] = player }
        val collision = CollisionFlagMap()
        npcs = NpcList()
        repo = NpcRepository(clock, NpcRegistry(npcs, collision, bus), npcs)
        playerHitModifier = mock(PlayerHitModifier::class.java)
        val deps = BossDeps(mock(GameRandom::class.java) { 0 }, mock(WorldRepository::class.java), repo,
            mock(LocRepository::class.java), players, clock, queues, collision, registry,
            BossExtensionRegistry(), mock(AccuracyFormulae::class.java), mock(MaxHitFormulae::class.java), playerHitModifier)
        styles = mock(AttackStyles::class.java) { org.rsmod.api.combat.commons.styles.AttackStyle.AccurateRanged }
        types = mock(AttackTypes::class.java)
        maxHits = mock(MaxHitFormulae::class.java) { 0..1 }
        attackManager = PlayerAttackManager(mock(GameRandom::class.java), bus, mock(WorldRepository::class.java),
            mock(AccuracyFormulae::class.java), maxHits, StandardNpcHitModifier(bus), playerHitModifier,
            mock(org.rsmod.api.player.interact.NpcInteractions::class.java), mock(org.rsmod.api.player.interact.NpcTInteractions::class.java),
            mock(org.rsmod.api.player.interact.PlayerInteractions::class.java), mock(org.rsmod.api.player.interact.PlayerTInteractions::class.java),
            emptySet(), mock(org.rsmod.api.combat.manager.NpcMaxHitRegistry::class.java))
        PlayerAttackManager::class.java.getDeclaredField("attackStyles").apply { isAccessible = true }.set(attackManager, styles)
        controller = TormentedDemonController()
        script = TormentedDemon(deps, mock(RouteFactory::class.java), types, styles, attackManager, controller)
        context = ScriptContext(bus, CheatCommandMap(), EngineQueueCache())
        with(script) { context.startup() }
        npc = Npc(ServerCacheManager.getNpc("npc.tormented_demon_1".asRSCM())!!, CoordGrid(4075, 4427))
        info = mock(NpcInfoProtocol::class.java)
        npc.infoProtocol = info
        repo.add(npc, Int.MAX_VALUE)
        bus.publish(NpcStateEvents.Spawn(npc))
        npc.combatXpMultiplier = 1000
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
        if (player.coords.x == 4075 && !player.pendingLogout) script.combatTick(npc, player)
        val iterator = queues.iterator()
        while (iterator.hasNext()) {
            val queue = iterator.next()
            if (--queue.remainingCycles <= 0) { iterator.remove(); queue.action() }
        }
        iterator.cleanUp()
    }

    @Test fun `blocked hits grant no damage XP for all three protected styles`() {
        for ((type, code) in listOf(HitType.Melee to 1, HitType.Ranged to 2, HitType.Magic to 3)) {
            script.resetFight(npc)
            // Change prayer through actual health loss, not a fabricated Modify/Impact event.
            if (code != 1) { land(hit(type, 100)); land(hit(type, 70)) }
            val before = listOf("stat.attack", "stat.strength", "stat.defence", "stat.ranged", "stat.magic", "stat.hitpoints").map { player.statMap.getFineXP(it) }
            val queued = hit(type, 100)
            when (type) {
                HitType.Melee -> attackManager.giveCombatXp(player, npc, org.rsmod.api.combat.commons.CombatAttack.Melee(null, null, org.rsmod.api.combat.commons.styles.MeleeAttackStyle.Accurate, org.rsmod.api.combat.commons.CombatStance.Stance1), 100)
                HitType.Ranged -> attackManager.giveCombatXp(player, npc, org.rsmod.api.combat.commons.CombatAttack.Ranged(org.rsmod.game.inv.InvObj("obj.magic_shortbow", 1), null, org.rsmod.api.combat.commons.styles.RangedAttackStyle.Accurate), 100)
                else -> attackManager.giveCombatXp(player, npc, org.rsmod.api.combat.commons.CombatAttack.Staff(org.rsmod.game.inv.InvObj("obj.battlestaff", 1), null), 100)
            }
            assertEquals(before, listOf("stat.attack", "stat.strength", "stat.defence", "stat.ranged", "stat.magic", "stat.hitpoints").map { player.statMap.getFineXP(it) }, "no launch XP: $type")
            land(queued)
            assertEquals(0, queued.damage)
            assertEquals(before, listOf("stat.attack", "stat.strength", "stat.defence", "stat.ranged", "stat.magic", "stat.hitpoints").map { player.statMap.getFineXP(it) }, "no impact XP: $type")
        }
    }

    @Test fun `damage XP follows shield-reduced actual damage and is awarded once`() {
        val before = listOf("stat.attack", "stat.strength", "stat.defence", "stat.ranged", "stat.magic", "stat.hitpoints").map { player.statMap.getFineXP(it) }
        val queued = hit(HitType.Ranged, 100)
        assertEquals(before, listOf("stat.attack", "stat.strength", "stat.defence", "stat.ranged", "stat.magic", "stat.hitpoints").map { player.statMap.getFineXP(it) })
        land(queued)
        val after = listOf("stat.attack", "stat.strength", "stat.defence", "stat.ranged", "stat.magic", "stat.hitpoints").map { player.statMap.getFineXP(it) }
        val expected = Player()
        expected.statAdvance("stat.ranged", 320.0)
        expected.statAdvance("stat.hitpoints", 106.4)
        assertEquals(expected.statMap.getFineXP("stat.ranged"), after[3] - before[3])
        assertEquals(expected.statMap.getFineXP("stat.hitpoints"), after[5] - before[5])
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

    @Test fun `two and three demons coordinate distinct styles and stagger attack slots`() {
        player.coords = CoordGrid(4087, 4375)
        npc.coords = player.coords.translate(0, 3)
        script.combatTick(npc, player)
        val second = addDemon(player.coords.translate(1, 3))
        script.combatTick(second, player)
        assertEquals("style_melee", registry.of(npc).currentPhaseName)
        assertEquals("style_ranged", registry.of(second).currentPhaseName)
        assertEquals(3, registry.of(second).nextAttackTick!! - registry.of(npc).nextAttackTick!!)
        val third = addDemon(player.coords.translate(2, 3))
        script.combatTick(third, player)
        assertEquals("single_bombs", registry.of(third).currentPhaseName)
        assertEquals(listOf(100, 102, 104), listOf(npc, second, third).map { registry.of(it).nextAttackTick })
        assertEquals(3, controller.members(player).size)
    }

    @Test fun `group bombs occur simultaneously after sixty ticks and third throws a single bomb`() {
        player.coords = CoordGrid(4087, 4375); npc.coords = player.coords.translate(0, 3)
        val second = addDemon(player.coords.translate(1, 3)); val third = addDemon(player.coords.translate(2, 3))
        val demons = listOf(npc, second, third)
        demons.forEach { script.combatTick(it, player) }
        repeat(60) { clock.cycle++; demons.forEach { script.combatTick(it, player) }; advanceQueues() }
        demons.forEach { verify(it.infoProtocol!!).setSequence("seq.luc2_undead_demon_explosion_fire".asRSCM(), 0) }
        assertEquals("single_bombs", registry.of(third).currentPhaseName)
        repeat(4) { clock.cycle++; demons.forEach { script.combatTick(it, player) }; advanceQueues() }
        assertEquals(3, player.queueList.count("queue.hit"))
    }

    @Test fun `bombs in flight still land when the demon dies`() {
        script.combatTick(npc, player)
        script.fireBomb(npc, player)
        land(hit(HitType.Magic, 1000))
        assertEquals(0, npc.hitpoints)
        repeat(4) { clock.cycle++; advanceQueues() }
        assertEquals(1, player.queueList.count("queue.hit"))
    }

    @Test fun `another player and a fourth demon are denied without changing the current owner`() {
        player.coords = CoordGrid(4087, 4375); npc.coords = player.coords.translate(0, 3)
        val second = addDemon(player.coords.translate(1, 3)); val third = addDemon(player.coords.translate(2, 3)); val fourth = addDemon(player.coords.translate(3, 3))
        listOf(npc, second, third).forEach { assertTrue(controller.claim(player, it)) }
        assertFalse(controller.claim(player, fourth))
        val other = Player().apply { slotId = 2; uuid = 2; assignUid(); coords = player.coords; statMap.setCurrentLevel("stat.hitpoints", 99) }
        assertFalse(controller.claim(other, npc))
        assertSame(player, controller.owner(npc))
        controller.release(third)
        assertTrue(controller.claim(player, fourth))
    }

    @Test fun `unreachable target clears combat ownership and restores health`() {
        land(hit(HitType.Ranged, 100))
        repeat(13) { clock.cycle++; advanceQueues() }
        assertEquals(600, npc.hitpoints)
        Assertions.assertNull(controller.owner(npc))
    }

    @Test fun `native player impact applies a landed bomb after demon death and cancels it after reset`() {
        for (reset in listOf(true, false)) {
            script.resetFight(npc); player.statMap.setCurrentLevel("stat.hitpoints", 99)
            player.queueList.clear()
            script.combatTick(npc, player); script.fireBomb(npc, player)
            repeat(4) { clock.cycle++; advanceQueues() }
            val iterator = player.queueList.iterator()!!
            val hits = mutableListOf<Hit>()
            while (iterator.hasNext()) (iterator.next().args as? Hit)?.let(hits::add)
            if (reset) script.resetFight(npc) else land(hit(HitType.Magic, 1000))
            val context = org.rsmod.api.player.protect.ProtectedAccessContextFactory.empty().copy(getEventBus = { bus }, getRandom = { DefaultGameRandom(1) }, getPlayerList = { PlayerList().apply { this[1] = player } }, getNpcList = { npcs })
            assertTrue(org.rsmod.api.player.protect.ProtectedAccessLauncher.withProtectedAccess(player, context) {
                with(org.rsmod.api.player.hit.processor.StandardPlayerHitProcessor) { hits.forEach { process(it) } }
            })
            assertEquals(if (reset) 99 else 59, player.statMap.getCurrentLevel("stat.hitpoints").toInt())
        }
    }

    @Test fun `native player hit modifier reduces the opening off-prayer ranged attack only with shield down`() {
        land(hit(HitType.Magic, 0))
        org.rsmod.api.player.vars.VarPlayerIntMapSetter.set(player, "varbit.prayer_protectfrommagic", 1)
        val actual = org.rsmod.api.player.hit.modifier.StandardPlayerHitModifier(bus)
        val hit = player.queueHit(npc, 1, HitType.Ranged, 31, actual)
        assertEquals(7, hit.damage)
    }

    @Test fun `slow crush bonus against matching prayer is fixed at eleven and shortens cooldown`() {
        player.worn[3] = org.rsmod.game.inv.InvObj("obj.barrows_dharok_weapon")
        `when`(types.resolve(any(dev.openrune.types.ItemServerType::class.java), anyInt())).thenReturn(org.rsmod.api.combat.commons.types.AttackType.Crush)
        `when`(styles.resolve(any(dev.openrune.types.ItemServerType::class.java), anyInt())).thenReturn(org.rsmod.api.combat.commons.styles.AttackStyle.AccurateMelee)
        land(hit(HitType.Melee, 100))
        val queued = hit(HitType.Melee, 100)
        assertEquals(11, queued.damage)
        land(queued)
        assertEquals(589, npc.hitpoints)
        assertEquals(clock.cycle + 4, player.actionDelay)
    }

    @Test fun `manual spell bonus uses equipped slow bow speed while ordinary NPC keeps spell speed`() {
        land(hit(HitType.Ranged, 0))
        player.worn[3] = org.rsmod.game.inv.InvObj("obj.darkbow")
        val spell = ServerCacheManager.getItem("obj.01_wind_strike".asRSCM())!!
        for ((style, rate) in listOf(org.rsmod.api.combat.commons.styles.AttackStyle.AccurateRanged to 9, org.rsmod.api.combat.commons.styles.AttackStyle.RapidRanged to 8)) {
            `when`(styles.resolve(any(dev.openrune.types.ItemServerType::class.java), anyInt())).thenReturn(style)
            attackManager.calculateSpellMaxHit(player, npc, spell, null, 8, 5, false)
            assertEquals(rate, mockingDetails(maxHits).invocations.last().arguments[5])
        }
        val ordinary = Npc(ServerCacheManager.getNpcs().values.first { it.name == "Goblin" })
        attackManager.calculateSpellMaxHit(player, ordinary, spell, null, 8, 5, false)
        assertEquals(5, mockingDetails(maxHits).invocations.last().arguments[5])
    }

    @Test fun `unguarded player hit copies retain distinct damage amounts`() {
        val hit = player.queueHit(npc, 1, HitType.Typeless, 40, playerHitModifier)
        val copy = hit.copy(hitmark = hit.hitmark.copy(damage = 5))
        val context = org.rsmod.api.player.protect.ProtectedAccessContextFactory.empty().copy(getEventBus = { bus }, getRandom = { DefaultGameRandom(1) }, getPlayerList = { PlayerList().apply { this[1] = player } }, getNpcList = { npcs })
        assertTrue(org.rsmod.api.player.protect.ProtectedAccessLauncher.withProtectedAccess(player, context) {
            with(org.rsmod.api.player.hit.processor.StandardPlayerHitProcessor) { process(hit); process(copy) }
        })
        assertEquals(54, player.statMap.getCurrentLevel("stat.hitpoints").toInt())
    }
    private fun addDemon(coords: CoordGrid): Npc = Npc(ServerCacheManager.getNpc("npc.tormented_demon_2".asRSCM())!!, coords).also {
        it.infoProtocol = mock(NpcInfoProtocol::class.java); repo.add(it, Int.MAX_VALUE); bus.publish(NpcStateEvents.Spawn(it))
    }
    private fun advanceQueues() {
        val iterator = queues.iterator()
        while (iterator.hasNext()) { val queue = iterator.next(); if (--queue.remainingCycles <= 0) { iterator.remove(); queue.action() } }
        iterator.cleanUp()
    }
    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() }
    }
}
