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

@ResourceLock("ServerCacheManager")
internal class GorillaAccessTest {
    @Test fun `native Enter and rope Climb-up move into cavern and back to entry point`() {
        val f = Fixture()
        f.operate(GorillaAccessScript.ACCESS, GorillaAccessScript.ENTRANCE)
        assertEquals(GorillaAccessScript.CAVERN, f.player.coords)
        f.operate("loc.mm2_cave_boss_exit", CoordGrid(2129, 5647))
        assertEquals(GorillaAccessScript.LOBBY, f.player.coords)
        assertEquals(0, f.player.vars["varp.gorilla_entry_return"])
    }

    @Test fun `unrecorded return uses approved screenshot arrival and startup owns only its entrance`() {
        val f = Fixture(); f.operate("loc.mm2_cave_boss_exit", CoordGrid(2129, 5647))
        assertEquals(GorillaAccessScript.LOBBY, f.player.coords)
        f.bus.publish(GameLifecycle.Startup)
        verify(f.repo).add(LocInfo(2, GorillaAccessScript.ENTRANCE, LocEntity(GorillaAccessScript.ACCESS.asRSCM(), 10, 0)), Int.MAX_VALUE, null)
        with(f.script) { f.scripts.shutdown() }
        verify(f.repo).del(LocInfo(2, GorillaAccessScript.ENTRANCE, LocEntity(GorillaAccessScript.ACCESS.asRSCM(), 10, 0)), Int.MAX_VALUE)
    }
    private class Fixture {
        val bus = EventBus(); val collision = CollisionFlagMap()
        val player = Player().apply { coords = GorillaAccessScript.LOBBY }
        val repo = mock(LocRepository::class.java) { invocation -> if (invocation.method.returnType == Boolean::class.javaPrimitiveType) true else org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation) }
        val script = GorillaAccessScript(repo, mock(ProtectedAccessLauncher::class.java))
        val scripts = ScriptContext(bus, CheatCommandMap(), EngineQueueCache())
        val locs = LocInteractions(mock(BoundValidator::class.java), bus)
        val context = ProtectedAccessContextFactory.empty().copy(getEventBus = { bus }, getCollision = { collision })
        init {
            for (c in listOf(GorillaAccessScript.LOBBY, GorillaAccessScript.CAVERN)) collision.allocateIfAbsent(c.x, c.z, c.level)
            with(script) { scripts.startup() }
        }
        fun operate(symbol: String, c: CoordGrid) {
            val type = ServerCacheManager.getObject(symbol.asRSCM())!!
            val loc = BoundLocInfo(LocInfo(2, c, LocEntity(type.id, 10, 0)), type)
            var outcome: Result<Unit>? = null
            val block: suspend () -> Unit = { val access = ProtectedAccess(player, GameCoroutine(), context); assertTrue(bus.publish(access, locs.opTrigger(player, loc, InteractionOp.Op1) ?: error("Missing native Enter"))) }
            block.startCoroutine(object : Continuation<Unit> { override val context = EmptyCoroutineContext; override fun resumeWith(r: Result<Unit>) { outcome = r } })
            checkNotNull(outcome).getOrThrow()
        }
    }
    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
