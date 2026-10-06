package org.rsmod.api.player.output

import dev.openrune.ServerCacheManager
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.parallel.ResourceLock
import org.junit.jupiter.api.Test
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player

@ResourceLock("ServerCacheManager")
class PlayerNotificationsTest {
    @Test fun `notifications preserve FIFO across titles colours and players`() {
        val first = Player(RecordingClient()); val second = Player(RecordingClient())
        ClientScripts.notificationDisplay(first, "Collection Log", "One")
        ClientScripts.notificationDisplay(first, "Quest", "Two", 123)
        ClientScripts.notificationDisplay(second, "Diary", "Other")
        assertTrue(scripts(first).isEmpty())
        PlayerNotifications.pulse(first); PlayerNotifications.pulse(second)
        assertEquals(listOf("Collection Log", "One", 0xff981f), scripts(first).single().values)
        repeat(13) { PlayerNotifications.pulse(first) }
        assertEquals(1, scripts(first).size)
        PlayerNotifications.pulse(first)
        assertEquals(listOf("Quest", "Two", 123), scripts(first).last().values)
        assertEquals("Other", scripts(second).single().values[1])
    }
    @Test fun `logout clear and invalid state discard pending popups`() {
        val player = Player(RecordingClient())
        ClientScripts.notificationDisplay(player, "A", "pending")
        PlayerNotifications.clear(player); PlayerNotifications.pulse(player)
        assertTrue(scripts(player).isEmpty())
        ClientScripts.notificationDisplay(player, "A", "pending")
        player.clientDisconnected.set(true); PlayerNotifications.pulse(player)
        player.clientDisconnected.set(false); PlayerNotifications.pulse(player)
        assertTrue(scripts(player).isEmpty())
        player.loggingOut = true
        ClientScripts.notificationDisplay(player, "A", "ignored")
        player.loggingOut = false; PlayerNotifications.pulse(player)
        assertTrue(scripts(player).isEmpty())
    }
    private fun scripts(player: Player) = (player.client as RecordingClient).messages.filterIsInstance<RunClientScript>().filter { it.id == 3343 }
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
