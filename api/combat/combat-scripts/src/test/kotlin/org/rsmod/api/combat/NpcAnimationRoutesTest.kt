package org.rsmod.api.combat

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.Mockito.*
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.combat.manager.NpcMaxHitRegistry
import org.rsmod.api.combat.manager.PlayerAttackManager
import org.rsmod.api.death.death
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.npc.respawn.BossRespawnTimers
import org.rsmod.api.player.hit.modifier.StandardPlayerHitModifier
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.interact.NpcTInteractions
import org.rsmod.api.player.interact.PlayerInteractions
import org.rsmod.api.player.interact.PlayerTInteractions
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.*
import org.rsmod.game.entity.npc.NpcInfoProtocol
import org.rsmod.game.inv.Inventory
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

@ResourceLock("ServerCacheManager")
@OptIn(org.rsmod.annotations.InternalApi::class)
class NpcAnimationRoutesTest {
    @Test fun `Tlati jaguar variants emit their native attack through generic combat`() {
        for (symbol in listOf("npc.varlamore_jaguar", "npc.varlamore_jaguar_cub", "npc.varlamore_dark_jaguar")) {
            val f = Fixture(symbol)
            f.attack()
            verify(f.info).setSequence("seq.npc_lynx_combat_melee".asRSCM(), 0)
            assertTrue(f.npc.actionDelay > f.clock.cycle)
            assertTrue(f.player.queueList.isNotEmpty)
            clearInvocations(f.info)
            f.attack() // Normal attack cooldown remains enforced.
            verifyNoInteractions(f.info)
        }
    }

    @Test fun `armed Bandosian guardian attacks and blocks with ork sequences`() {
        val f = Fixture("npc.elite_npc_2")
        f.attack()
        verify(f.info).setSequence("seq.ork_update_double_grip_attack".asRSCM(), 0)
        f.npc.resetAnim()
        clearInvocations(f.info)
        f.incomingHit()
        verify(f.info).setSequence("seq.ork_update_defend".asRSCM(), 0)
        assertTrue(f.npc.queueList.isNotEmpty)
    }

    @Test fun `jaguar has no invented human block when hit`() {
        val f = Fixture("npc.varlamore_jaguar")
        f.incomingHit()
        verifyNoInteractions(f.info)
    }

    @Test fun `native weapons and ring variants reach attack and incoming hit routes`() {
        for ((symbol, attack, block) in listOf(
            Triple("npc.hos_town_guard_03", "seq.human_staff_pummel", "seq.human_staff_block"),
            Triple("npc.icewarrior", "seq.human_sword_slash", "seq.human_sword_def"),
            Triple("npc.shipyardworker2", "seq.human_axe_chop", "seq.human_axe_block"),
            Triple("npc.tzhaar_xil3", "seq.thzaar_ring_attack", "seq.thzaar_parry"),
            Triple("npc.elite_npc_1", "seq.godwars_armadyl_cannon_attack", "seq.godwars_armadyl_defend"),
        )) {
            val f = Fixture(symbol)
            f.attack()
            verify(f.info).setSequence(attack.asRSCM(), 0)
            f.npc.resetAnim()
            clearInvocations(f.info)
            f.incomingHit()
            verify(f.info).setSequence(block.asRSCM(), 0)
        }
    }

    @Test fun `world death retains the existing respawn lifecycle`() {
        val f = Fixture("npc.varlamore_jaguar")
        var complete = false
        val action: suspend () -> Unit = { f.access.death(f.repo, f.players, mock(BossRespawnTimers::class.java)) }
        action.startCoroutine(object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) { result.getOrThrow(); complete = true }
        })
        repeat(ServerCacheManager.getAnim("seq.npc_lynx_combat_death".asRSCM())!!.tickDuration) {
            f.clock.cycle++
            f.npc.currentMapClock = f.clock.cycle
            f.npc.processedMapClock = f.clock.cycle
            f.coroutine.advance()
        }
        assertTrue(complete)
        assertEquals(1, f.npcs.count())
        assertTrue(f.npc.hidden)
    }

    @Test fun `death emits the native sequence and waits before removing NPC`() {
        for ((symbol, sequence) in listOf("npc.varlamore_jaguar" to "seq.npc_lynx_combat_death", "npc.elite_npc_2" to "seq.ork_update_death")) {
            val f = Fixture(symbol)
            f.npc.respawns = false // Clue guardians and other temporary spawns are removed on death.
            var complete = false
            val action: suspend () -> Unit = { f.access.death(f.repo, f.players, mock(BossRespawnTimers::class.java)) }
            action.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { result.getOrThrow(); complete = true }
            })
            verify(f.info).setSequence(sequence.asRSCM(), 0)
            assertFalse(complete)
            assertEquals(1, f.npcs.count())
            assertTrue(f.coroutine.isSuspended)
            repeat(ServerCacheManager.getAnim(sequence.asRSCM())!!.tickDuration) {
                f.clock.cycle++
                f.npc.currentMapClock = f.clock.cycle
                f.npc.processedMapClock = f.clock.cycle
                f.coroutine.advance()
            }
            assertTrue(complete)
            assertEquals(0, f.npcs.count())
        }
    }

    private class Fixture(symbol: String) {
        val clock = MapClock(100)
        val events = EventBus()
        val npcs = NpcList()
        val players = PlayerList()
        val repo = NpcRepository(clock, NpcRegistry(npcs, CollisionFlagMap(), events), npcs)
        val player = Player().apply {
            slotId = 1; uuid = 1; assignUid(); coords = CoordGrid(3200, 3200)
            currentMapClock = clock.cycle; processedMapClock = clock.cycle
            statMap.setBaseLevel("stat.hitpoints", 99); statMap.setCurrentLevel("stat.hitpoints", 99)
            worn = Inventory(ServerCacheManager.getInventory("inv.worn".asRSCM())!!, arrayOfNulls(14))
        }
        val npc = Npc(ServerCacheManager.getNpc(symbol.asRSCM())!!, player.coords.translateZ(1))
        val info = mock(NpcInfoProtocol::class.java)
        val coroutine = GameCoroutine("native-npc-animation")
        val access = StandardNpcAccess(npc, coroutine, StandardNpcAccessContext(
            { DefaultGameRandom(42) }, { StandardNpcHitModifier(events) }, { StandardNpcHitProcessor(players, events, emptySet()) }))
        private val combat = NvPCombat(mock(AccuracyFormulae::class.java), mock(MaxHitFormulae::class.java),
            mock(WorldRepository::class.java), StandardPlayerHitModifier(events))
        private val attacks = PlayerAttackManager(DefaultGameRandom(42), events, mock(WorldRepository::class.java),
            mock(AccuracyFormulae::class.java), mock(MaxHitFormulae::class.java), StandardNpcHitModifier(events),
            StandardPlayerHitModifier(events), mock(NpcInteractions::class.java), mock(NpcTInteractions::class.java),
            mock(PlayerInteractions::class.java), mock(PlayerTInteractions::class.java), emptySet(), mock(NpcMaxHitRegistry::class.java))
        init {
            players[1] = player
            npc.infoProtocol = info
            repo.add(npc, Int.MAX_VALUE)
            npc.currentMapClock = clock.cycle; npc.processedMapClock = clock.cycle
            npc.vars["varn.lastattack"] = clock.cycle
        }
        fun attack() { combat.attack(access, player, CombatAttack.NpcMelee(MeleeAttackType.Crush)) }
        fun incomingHit() { attacks.queueMeleeHit(player, npc, 1) }
    }

    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
