package org.rsmod.content.other.treasure.trails

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.Mockito.mock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.death.NpcDeathRewards
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.music.MusicRepository
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.processor.NpcHitProcessor
import org.rsmod.api.npc.respawn.BossRespawnTimers
import org.rsmod.api.player.interact.ContextualInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.activities.shades.FiyrShadeAttackHook
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
@ResourceLock("ServerCacheManager")
class TrailNativeKillActionsTest {
    @Test fun `native death producer credits each mage faction with one corresponding worn god item`() {
        for ((god, item) in listOf("saradomin" to "obj.saradomin_staff", "zamorak" to "obj.zamorak_staff", "armadyl" to "obj.trail_armadyl_cloak", "bandos" to "obj.trail_bandos_cloak")) {
            val f = Fixture(TrailSkillKillHook.spiritualTask)
            f.player.worn[if (god in setOf("saradomin", "zamorak")) 3 else 1] = InvObj(item)
            val npc = Npc("npc.godwars_spiritual_${god}_mage", CoordGrid(2885, 5320, 2))
            f.kill(npc)
            assertEquals(10, f.state().phase); assertEquals(2, f.state().completed)
            f.sherlock()
        }
    }

    @Test fun `wrong god unworn affiliation low Slayer and unassigned mage task do not complete`() {
        for (failure in 0..3) {
            val f = Fixture(TrailSkillKillHook.spiritualTask)
            f.player.worn[3] = InvObj("obj.saradomin_staff")
            when (failure) { 0 -> f.player.worn[3] = InvObj("obj.zamorak_staff"); 1 -> { f.player.inv[1] = f.player.worn[3]; f.player.worn[3] = null }; 2 -> f.player.statMap.setCurrentLevel("stat.slayer", 82); else -> f.assign(0) }
            f.kill(Npc("npc.godwars_spiritual_saradomin_mage", CoordGrid(2885, 5320, 2)))
            assertEquals(if (failure == 3) 0 else 9, f.state().phase)
        }
    }

    @Test fun `Fiyr shadow activates native shade model and credited death inside catacombs completes assigned task`() {
        val f = Fixture(TrailSkillKillHook.shadeTask)
        val npc = Npc("npc.shadeshadow_level5", CoordGrid(3460, 9695, 0))
        FiyrShadeAttackHook().validate(f.player, npc)
        assertEquals("npc.shade_level5".asRSCM(), npc.visType.id)
        f.kill(npc); assertEquals(10, f.state().phase)
        val completed = f.player.inv[0]; f.kill(npc); assertSame(completed, f.player.inv[0])
        f.sherlock()
        npc.slotId = 1
        npc.setRespawnValues()
        assertEquals("npc.shadeshadow_level5".asRSCM(), npc.visType.id)
    }

    @Test fun `Fiyr kill requires dead NPC inside catacombs and excludes other shades`() {
        for (npc in listOf(Npc("npc.shade_level5", CoordGrid(3480, 3300, 0)), Npc("npc.shade_level5", CoordGrid(3460, 9695, 1)), Npc("npc.shade_level4", CoordGrid(3460, 9695, 0)))) {
            val f = Fixture(TrailSkillKillHook.shadeTask)
            f.kill(npc); assertEquals(9, f.state().phase)
        }
        val f = Fixture(TrailSkillKillHook.shadeTask)
        f.player.coords = CoordGrid(3200, 3200, 0)
        f.kill(Npc("npc.shade_level5", CoordGrid(3460, 9695, 0)))
        assertEquals(10, f.state().phase)
    }

    @Test fun `native damage contributions choose the credited player rather than another nearby clue holder`() {
        val f = Fixture(TrailSkillKillHook.shadeTask)
        val other = Fixture(TrailSkillKillHook.shadeTask)
        other.player.uuid = 2L
        f.players[2] = other.player
        val npc = Npc("npc.shade_level5", CoordGrid(3460, 9695, 0))
        npc.recordDamage(other.player, 100); npc.recordDamage(f.player, 10)
        f.spawnDeathDrops(npc)
        assertEquals(9, f.state().phase); assertEquals(10, other.state().phase)
    }

    @Test fun `saved completed kill state retains completion until Sherlock even after gear removal`() {
        val f = Fixture(TrailSkillKillHook.spiritualTask)
        f.player.worn[3] = InvObj("obj.saradomin_staff")
        f.kill(Npc("npc.godwars_spiritual_saradomin_mage", CoordGrid(2885, 5320, 2)))
        f.player.inv[0] = f.player.inv[0]!!.copy(); f.player.worn[3] = null
        assertEquals(10, f.state().phase); f.sherlock()
    }
    private class Fixture(row: Int) {
        val random = DefaultGameRandom(42); val bus = EventBus()
        val scripts = ScriptContext(bus, CheatCommandMap(), EngineQueueCache())
        val progress = TrailProgress(TrailCatalog(), random)
        val player = Player().apply {
            uuid = 1L
            inv = Inventory(ServerCacheManager.getInventory("inv.inv".asRSCM())!!, arrayOfNulls(28))
            worn = Inventory(ServerCacheManager.getInventory("inv.worn".asRSCM())!!, arrayOfNulls(14))
            statMap.setCurrentLevel("stat.slayer", 99)
            statMap.setCurrentLevel("stat.firemaking", 99)
        }
        val players = PlayerList().apply { set(1, player) }
        val hook = TrailSkillKillHook(progress, TrailTargets(progress), TrailRequirements(progress.catalog), mock(AreaChecker::class.java))
        val death = NpcDeath(mock(NpcRepository::class.java), players, mock(ObjRepository::class.java), emptySet(), setOf(hook), mock(BossRespawnTimers::class.java))
        init {
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { scripts.startup() }
            val clue = progress.catalog.clues.getValue(row)
            player.inv[0] = InvObj(ServerCacheManager.getItem(progress.catalog.item(clue))!!, 1, TrailState(row, 3, 2, 9).encode())
        }
        fun state() = progress.state(player.inv[0]!!)!!
        fun assign(phase: Int) { assertTrue(progress.phase(player, 0, player.inv[0]!!, phase)) }
        fun kill(npc: Npc) { npc.recordDamage(player, 1); spawnDeathDrops(npc) }
        fun spawnDeathDrops(npc: Npc) {
            npc.hitpoints = 0
            val access = StandardNpcAccess(npc, GameCoroutine("native-death-test"), StandardNpcAccessContext({ random }, { mock(NpcHitModifier::class.java) }, { mock(NpcHitProcessor::class.java) }))
            death.spawnDrops(access, rewards = NpcDeathRewards(includeRemains = false))
        }
        fun sherlock() {
            val clue = progress.catalog.clues.getValue(state().row)
            val contextual = ContextualInteractions()
            val script = TrailInteractionScript(progress, TrailTargets(progress), TrailRequirements(progress.catalog), mock(TrailPuzzleScript::class.java), mock(TrailGuards::class.java), contextual, mock(MusicRepository::class.java))
            with(script) { scripts.startup() }
            val fields = progress.catalog.fields(clue.targets.single())
            val npc = Npc(ServerCacheManager.getNpc(fields.int("npc"))!!, CoordGrid(fields.int("coord")))
            val event = contextual.npc(player, npc, InteractionOp.Op1)!!
            val coroutine = GameCoroutine("native-kill-sherlock")
            player.activeCoroutine = coroutine
            val access = ProtectedAccess(player, coroutine, ProtectedAccessContextFactory.empty().copy(getEventBus = { bus }, getRandom = { random }))
            var result: Result<Boolean>? = null
            suspend { bus.publish(access, event) }.startCoroutine(object : Continuation<Boolean> { override val context = EmptyCoroutineContext; override fun resumeWith(outcome: Result<Boolean>) { result = outcome } })
            assertTrue(result!!.getOrThrow()); assertEquals(1, player.inv.objs.filterNotNull().filter { it.id == clue.tier.casket }.sumOf { it.count })
            with(script) { scripts.shutdown() }
        }
    }
    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
