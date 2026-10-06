package org.rsmod.content.interfaces.notifications

import dev.openrune.ServerCacheManager
import dev.or2.central.account.Rights
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

class NotificationScriptTest {
    @Test fun `administrator command uses real tick consumer and logout drops the rest`() {
        val f = Fixture()
        f.commands.execute(f.player, "testnotify", emptyList())
        f.bus.publish(GameLifecycle.LateCycle)
        assertEquals("Test notification 1/3", f.popups().single().values[1])
        repeat(14) { f.bus.publish(GameLifecycle.LateCycle) }
        assertEquals("Test notification 2/3", f.popups().last().values[1])
        f.bus.publish(SessionStateEvent.Logout(f.player))
        repeat(30) { f.bus.publish(GameLifecycle.LateCycle) }
        assertEquals(2, f.popups().size)
    }
    @Test fun `normal rights cannot enqueue test popups and shutdown clears queued state`() {
        val f = Fixture()
        f.player.modLevel = Rights.NONE
        f.commands.execute(f.player, "testnotify", emptyList())
        f.bus.publish(GameLifecycle.LateCycle)
        assertTrue(f.popups().isEmpty())
        f.player.modLevel = Rights.ADMINISTRATOR
        f.commands.execute(f.player, "testnotify", emptyList())
        f.bus.publish(GameLifecycle.Shutdown)
        f.bus.publish(GameLifecycle.LateCycle)
        assertTrue(f.popups().isEmpty())
    }
    private class Fixture {
        val player = Player(RecordingClient()).apply { modLevel = Rights.ADMINISTRATOR }
        val players = PlayerList().apply { this[1] = player }
        val bus = EventBus()
        val commands = CheatCommandMap()
        init {
            val ctx = ScriptContext(bus, commands, EngineQueueCache())
            with(NotificationScript(players)) { ctx.startup() }
        }
        fun popups() = (player.client as RecordingClient).messages.filterIsInstance<RunClientScript>().filter { it.id == 3343 }
    }
    private class RecordingClient : Client<Any, Any> {
        val messages = mutableListOf<Any>()
        override fun write(message: Any) { messages += message }
        override fun close() = Unit
        override fun read(player: Player) = Unit
        override fun flush() = Unit
        override fun flushHighPriority() = Unit
        override fun unregister(service: Any, player: Player) = Unit
    }
    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() }
    }
}
