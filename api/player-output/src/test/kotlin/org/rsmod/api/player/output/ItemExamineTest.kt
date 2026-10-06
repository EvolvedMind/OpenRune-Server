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
class ItemExamineTest {
    @Test fun `examine preserves description and prints all values without client feature flags`() {
        val player = Player(RecordingClient()); val type = ServerCacheManager.getItem(4151)!!
        player.objExamine(type, 1, 150)
        val messages = (player.client as RecordingClient).messages.filterIsInstance<MessageGame>()
        assertEquals(2, messages.size)
        assertEquals("<img=15> ${type.name} - ${type.examine}", messages[0].message)
        assertEquals("<col=008000>GE: 150 gp</col> - <col=0000ff>High Alch: 72,000 gp</col> - <col=ff0000>Low Alch: 48,000 gp</col>", messages[1].message)
        assertTrue(messages.all { it.type == ChatType.GameMessage.id })
        assertTrue(scripts(player).isEmpty())
    }
    @Test fun `noted stacks use base alchemy values and stack totals use long arithmetic`() {
        val player = Player(RecordingClient()); val whip = ServerCacheManager.getItem(4151)!!
        val note = ServerCacheManager.getItem(whip.certlink)!!
        player.objExamine(note, 2, Int.MAX_VALUE)
        val description = (player.client as RecordingClient).messages.filterIsInstance<MessageGame>().first().message
        assertEquals("<img=15> ${whip.name} (x2) - ${note.examine}", description)
        val value = (player.client as RecordingClient).messages.filterIsInstance<MessageGame>().last().message
        assertTrue(value.contains("GE: 4,294,967,294 gp"), value)
        assertTrue(value.contains("High Alch: 144,000 gp"), value)
        assertTrue(value.contains("Low Alch: 96,000 gp"), value)
    }
    @Test fun `full market prices and extreme stack totals never wrap`() {
        val player = Player(RecordingClient()); val type = ServerCacheManager.getItem(4151)!!
        player.objExamine(type, 2, 5_000_000_000L)
        var text = (player.client as RecordingClient).messages.filterIsInstance<MessageGame>().last().message
        assertTrue(text.contains("GE: 10,000,000,000 gp"), text)
        player.objExamine(type, Int.MAX_VALUE, Long.MAX_VALUE)
        text = (player.client as RecordingClient).messages.filterIsInstance<MessageGame>().last().message
        assertTrue(text.contains("GE: 19,807,040,619,342,712,359,383,728,129 gp"), text)
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
