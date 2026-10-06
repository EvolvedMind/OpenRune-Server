package org.rsmod.content.other.treasure.trails

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import kotlin.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.Mockito.*
import org.rsmod.api.config.refs.params
import org.rsmod.api.death.*
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.music.MusicRepository
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.respawn.BossRespawnTimers
import org.rsmod.api.player.events.PlayerDigEvent
import org.rsmod.api.player.interact.ContextualInteractions
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.*
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@ResourceLock("ServerCacheManager")
class TrailGuardsTest {
    @Test fun `native deletion before kill credit lets the next dig advance without another guardian`() {
        val f = Fixture(total = 3)
        f.dig(); val guardian = f.npcs.single()
        assertEquals(4, f.state().phase)
        f.dig(); assertEquals(1, f.npcs.count())
        f.kill(guardian)
        assertEquals(5, f.state().phase)
        f.player.inv[0] = f.player.inv[0]!!.copy() // Saved/copied item retains the completion marker.
        f.dig()
        assertEquals(1, f.state().completed)
        assertEquals(0, f.state().phase)
        assertEquals(0, f.npcs.count())
        assertEquals(0, f.caskets())
    }
    @Test fun `final coordinate guardian gives exactly one reward casket after the next dig`() {
        val f = Fixture(total = 1)
        f.dig(); f.kill(f.npcs.single()); f.dig(); f.dig()
        assertEquals(1, f.caskets())
        assertEquals(0, f.npcs.count())
        assertNull(f.progress.state(f.player.inv[0]!!))
    }
    @Test fun `all guardians must receive owner kill credit even when deletion events happen first`() {
        val f = Fixture(total = 1, multi = true)
        f.dig(); val guardians = f.npcs.toList()
        assertTrue(guardians.size > 1)
        guardians.forEach { f.removeDead(it) }
        guardians.dropLast(1).forEach { f.credit(it) }
        assertEquals(4, f.state().phase)
        f.credit(guardians.first()) // Duplicate dispatch cannot count the same guardian twice.
        assertEquals(4, f.state().phase)
        f.credit(guardians.last())
        assertEquals(5, f.state().phase)
        f.dig()
        assertEquals(1, f.caskets())
    }
    @Test fun `wrong owner and stale clue state never complete a guardian encounter`() {
        val f = Fixture(total = 1)
        f.dig(); val guardian = f.npcs.single()
        TrailGuardKillHook(f.guards).onKill(NpcDeathKillContext(Player(), guardian, 0))
        assertEquals(4, f.state().phase)
        f.player.inv[0] = f.player.inv[0]!!.copy(vars = TrailState(f.clue.row, 2).encode())
        f.kill(guardian)
        assertEquals(0, f.state().phase)
        assertEquals(0, f.caskets())
    }
    @Test fun `timeout logout and death cleanup do not create a completed clue`() {
        for (cleanup in 0..2) {
            val f = Fixture(total = 1)
            f.dig(); val guardian = f.npcs.single()
            if (cleanup == 0) {
                f.repo.del(guardian, Int.MAX_VALUE) // Alive removal is not a credited defeat.
                f.events.publish(GameLifecycle.LateCycle)
            } else if (cleanup == 1) f.events.publish(SessionStateEvent.Logout(f.player))
            else TrailGuardDeathHook(f.guards).cleanup(f.player)
            assertEquals(4, f.state().phase)
            assertEquals(0, f.npcs.count())
            f.dig()
            assertEquals(1, f.npcs.count())
        }
    }
    @Test fun `uncredited dead removals expire before they can unlock a later clue`() {
        val f = Fixture(total = 1)
        f.dig(); val guardian = f.npcs.single()
        f.removeDead(guardian)
        f.events.publish(GameLifecycle.LateCycle)
        f.credit(guardian)
        assertEquals(4, f.state().phase)
        f.dig(); assertEquals(1, f.npcs.count())
    }

    private class Fixture(total: Int, multi: Boolean = false) {
        val events = EventBus()
        val random = DefaultGameRandom(42)
        val progress = TrailProgress(TrailCatalog(), random)
        val clue = progress.catalog.clues.values.first { clue ->
            clue.kind == "coordinate" && (clue.tier in setOf(TrailTier.BEGINNER, TrailTier.MASTER) || progress.catalog.rowItems.containsKey(clue.row)) &&
                clue.fields.ints("combat_encounter").isNotEmpty() &&
                clue.fields.ints("combat_encounter").all { row ->
                    val size = progress.catalog.fields(row).ints("npcs").size
                    if (multi) size > 1 else size == 1
                }
        }
        val player = Player().apply {
            uuid = 1; observerUUID = 1; slotId = 1
            inv = Inventory(ServerCacheManager.getInventory("inv.inv".asRSCM())!!, arrayOfNulls(28))
        }
        val players = PlayerList().apply { this[1] = player }
        val npcs = NpcList()
        val clock = MapClock(100)
        val repo = NpcRepository(clock, NpcRegistry(npcs, CollisionFlagMap(), events), npcs)
        val guards = TrailGuards(progress, repo, mock(AiPlayerInteractions::class.java), random)
        val death = NpcDeath(repo, players, mock(ObjRepository::class.java), setOf(NpcDeathDropHook { true }),
            setOf(TrailGuardKillHook(guards)), BossRespawnTimers(clock))
        init {
            val ctx = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { ctx.startup() }
            with(guards) { ctx.startup() }
            with(TrailInteractionScript(progress, TrailTargets(progress), mock(TrailRequirements::class.java),
                mock(TrailPuzzleScript::class.java), guards, ContextualInteractions(), mock(MusicRepository::class.java))) { ctx.startup() }
            val target = clue.targets.map(progress.catalog::fields).first { it.table == "cluehelper_target_coord" }
            player.coords = CoordGrid(target.int("coord"))
            player.inv[0] = InvObj(ServerCacheManager.getItem(progress.catalog.item(clue))!!, 1, TrailState(clue.row, total).encode())
        }
        fun caskets() = player.inv.objs.filterNotNull().filter { it.id == clue.tier.casket }.sumOf { it.count }
        fun state() = checkNotNull(progress.state(player.inv[0]!!))
        fun dig() = events.publish(PlayerDigEvent(player))
        fun kill(npc: Npc) {
            npc.recordDamage(player, 1); npc.hitpoints = 0
            run { death.deathWithDrops(access(npc)) }
        }
        fun removeDead(npc: Npc) {
            npc.recordDamage(player, 1); npc.hitpoints = 0
            run { death.deathNoDrops(access(npc)) }
        }
        fun credit(npc: Npc) = death.spawnDrops(access(npc))
        private fun access(npc: Npc): StandardNpcAccess = mock(StandardNpcAccess::class.java) { call ->
            when (call.method.name) {
                "getNpc" -> npc
                "getCoords" -> npc.coords
                "param" -> npc.param(params.death_anim)
                else -> RETURNS_DEFAULTS.answer(call)
            }
        }
        private fun run(block: suspend () -> Unit) {
            var result: Result<Unit>? = null
            block.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(value: Result<Unit>) { result = value }
            })
            checkNotNull(result).getOrThrow()
        }
    }
    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
