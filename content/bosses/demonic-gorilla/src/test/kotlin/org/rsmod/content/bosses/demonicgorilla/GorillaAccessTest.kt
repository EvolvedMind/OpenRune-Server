package org.rsmod.content.bosses.demonicgorilla

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import kotlin.coroutines.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.Mockito.*
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.protect.*
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.loc.*
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
internal class GorillaAccessTest {
    @Test fun `native hole Climb-down and rope Climb-up travel after climbing delay`() {
        val f = Fixture()
        f.begin(GorillaAccessScript.HOLE, GorillaAccessScript.ENTRANCE)
        assertEquals(GorillaAccessScript.LOBBY, f.player.coords)
        f.finish(); assertEquals(GorillaAccessScript.CAVERN, f.player.coords)
        f.begin(GorillaAccessScript.ROPE, GorillaAccessScript.ROPE_COORD)
        assertEquals(GorillaAccessScript.CAVERN, f.player.coords)
        f.finish(); assertEquals(GorillaAccessScript.LOBBY, f.player.coords)
    }

    @Test fun `rope after relog returns to fixed outside entrance and clears legacy return state`() {
        val f = Fixture(); f.player.coords = GorillaAccessScript.CAVERN
        VarPlayerIntMapSetter.set(f.player, "varp.gorilla_entry_return", CoordGrid(2076, 5646).packed)
        f.begin(GorillaAccessScript.ROPE, GorillaAccessScript.ROPE_COORD); f.finish()
        assertEquals(GorillaAccessScript.LOBBY, f.player.coords)
        assertEquals(0, f.player.vars["varp.gorilla_entry_return"])
    }

    @Test fun `native existing exit remains usable`() {
        val f = Fixture(); f.player.coords = GorillaAccessScript.CAVERN
        f.begin("loc.mm2_cave_boss_exit", CoordGrid(2129, 5647)); f.finish()
        assertEquals(GorillaAccessScript.LOBBY, f.player.coords)
    }

    @Test fun `cancel or logout during climbing never lands at destination`() {
        for (cancel in listOf(true, false)) {
            val f = Fixture(); val start = f.player.coords
            f.begin(GorillaAccessScript.HOLE, GorillaAccessScript.ENTRANCE)
            if (cancel) f.coroutine.cancel() else f.player.pendingLogout = true
            f.finish(expectFailure = cancel)
            assertEquals(start, f.player.coords)
        }
    }

    @Test fun `danger sign reads without teleporting`() {
        val f = Fixture(); f.begin(GorillaAccessScript.SIGN, GorillaAccessScript.SIGN_COORD); f.finish()
        assertEquals(GorillaAccessScript.LOBBY, f.player.coords)
    }

    @Test fun `startup and shutdown own exactly the requested hole sign and rope`() {
        val f = Fixture(); f.bus.publish(GameLifecycle.Startup)
        val owned = listOf(
            LocInfo(2, GorillaAccessScript.ENTRANCE, LocEntity(GorillaAccessScript.HOLE.asRSCM(), 10, 0)),
            LocInfo(2, GorillaAccessScript.SIGN_COORD, LocEntity(GorillaAccessScript.SIGN.asRSCM(), 10, 2)),
            LocInfo(2, GorillaAccessScript.ROPE_COORD, LocEntity(GorillaAccessScript.ROPE.asRSCM(), 10, 0)),
        )
        for (loc in owned) verify(f.repo).add(loc, Int.MAX_VALUE, null)
        with(f.script) { f.scripts.shutdown() }
        for (loc in owned) verify(f.repo).del(loc, Int.MAX_VALUE)
        verifyNoMoreInteractions(f.repo)
    }

    private class Fixture {
        val bus = EventBus(); val collision = sharedCollision
        val player = Player().apply { coords = GorillaAccessScript.LOBBY; currentMapClock = 100; processedMapClock = 100 }
        val repo = mock(LocRepository::class.java) { invocation -> if (invocation.method.returnType == Boolean::class.javaPrimitiveType) true else org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation) }
        val script = GorillaAccessScript(repo, mock(ProtectedAccessLauncher::class.java))
        val scripts = ScriptContext(bus, CheatCommandMap(), EngineQueueCache())
        val locs = LocInteractions(mock(BoundValidator::class.java), bus)
        val context = ProtectedAccessContextFactory.empty().copy(getEventBus = { bus }, getCollision = { collision })
        lateinit var coroutine: GameCoroutine
        private var outcome: Result<Unit>? = null
        init {
            for (c in listOf(GorillaAccessScript.LOBBY, GorillaAccessScript.CAVERN)) collision.allocateIfAbsent(c.x, c.z, c.level)
            with(script) { scripts.startup() }
        }
        fun begin(symbol: String, c: CoordGrid) {
            val type = ServerCacheManager.getObject(symbol.asRSCM())!!
            val loc = BoundLocInfo(LocInfo(2, c, LocEntity(type.id, 10, 0)), type)
            outcome = null; coroutine = GameCoroutine(); player.activeCoroutine = coroutine
            val block: suspend () -> Unit = {
                val access = ProtectedAccess(player, coroutine, context)
                assertTrue(bus.publish(access, locs.opTrigger(player, loc, InteractionOp.Op1) ?: error("Missing native object operation")))
            }
            block.startCoroutine(object : Continuation<Unit> { override val context = EmptyCoroutineContext; override fun resumeWith(r: Result<Unit>) { outcome = r } })
        }
        fun finish(expectFailure: Boolean = false) {
            repeat(4) { player.currentMapClock++; player.processedMapClock = player.currentMapClock; coroutine.advance() }
            val result = checkNotNull(outcome)
            if (expectFailure) assertTrue(result.isFailure) else result.getOrThrow()
        }
    }
    companion object {
        private val sharedCollision = CollisionFlagMap()

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() }
    }
}
