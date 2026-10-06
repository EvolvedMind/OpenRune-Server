package org.rsmod.content.interfaces.collectionlog

import dev.openrune.ServerCacheManager
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.output.PlayerNotifications
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.inv.InvObj
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@ResourceLock("ServerCacheManager")
class CollectionLogRewardTest {
    @Test fun `every repeat broadcasts to the world but personal unlock and popup occur only once`() {
        val f = Fixture()
        f.log.grant(f.player, 4151, 2, "Abyssal demon")
        f.log.grant(f.player, 4151, 1, "Abyssal demon")
        assertEquals(3, f.player.collectionTransmit.countOf(4151))
        val broadcasts = f.messages(f.observer).filter { it.type == 14 }
        assertEquals(2, broadcasts.size)
        assertTrue(broadcasts[0].message.contains("Bram received 2 x Abyssal whip from Abyssal demon!"))
        assertEquals(1, f.messages(f.player).count { it.message.startsWith("New item added") })
        repeat(30) { PlayerNotifications.pulse(f.player) }
        assertEquals(1, f.popups().size)
    }
    @Test fun `several new rewards queue sequentially and copies do not overwrite each other`() {
        val f = Fixture()
        val second = CollectionLogCategories.allCategoryStructIds.asSequence()
            .flatMap { CollectionLogItems.itemsInCategoryStruct(it).asSequence() }.first { it != 4151 }
        f.log.grant(f.player, 4151, 4)
        f.log.grant(f.player, second)
        f.log.grant(f.player, second, 3)
        PlayerNotifications.pulse(f.player)
        assertEquals(1, f.popups().size)
        repeat(14) { PlayerNotifications.pulse(f.player) }
        assertEquals(2, f.popups().size)
        assertNotEquals(f.popups()[0].values[1], f.popups()[1].values[1])
    }
    @Test fun `notes normalize membership and non log items or nonpositive quantities are ignored`() {
        val f = Fixture(); val type = ServerCacheManager.getItem(4151)!!
        f.log.grant(f.player, type.certlink)
        f.log.grant(f.player, 995, 100)
        f.log.grant(f.player, 4151, 0)
        f.log.grant(f.player, 4151, -1)
        assertEquals(1, f.player.collectionTransmit.countOf(4151))
        assertEquals(1, f.messages(f.observer).size)
        assertTrue(f.messages(f.observer).single().message.contains("Abyssal whip!"))
    }
    @Test fun `personal settings do not silence world announcements and saturated counts stay safe`() {
        val f = Fixture()
        VarPlayerIntMapSetter.set(f.player, "varbit.option_collection_new_item", 0)
        f.log.grant(f.player, 4151, Int.MAX_VALUE)
        f.log.grant(f.player, 4151, 10)
        assertEquals(Int.MAX_VALUE, f.player.collectionTransmit.countOf(4151))
        assertEquals(2, f.messages(f.observer).size)
        assertFalse(f.messages(f.player).any { it.message.startsWith("New item added") })
        PlayerNotifications.pulse(f.player); assertTrue(f.popups().isEmpty())
    }
    @Test fun `failed log transaction does not claim a new unlock or announce it`() {
        val f = Fixture()
        for (slot in f.player.collectionTransmit.indices) f.player.collectionTransmit[slot] = InvObj("obj.coins", Int.MAX_VALUE)
        f.log.grant(f.player, 4151)
        assertEquals(0, f.player.collectionTransmit.countOf(4151))
        assertTrue(f.messages(f.observer).isEmpty())
        PlayerNotifications.pulse(f.player); assertTrue(f.popups().isEmpty())
    }
    private class Fixture {
        val player = Player(RecordingClient()).apply { displayName = "Bram" }
        val observer = Player(RecordingClient())
        val players = PlayerList().apply { this[1] = player; this[2] = observer }
        val log = CollectionLog(players)
        init {
            val context = ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache())
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { context.startup() }
            VarPlayerIntMapSetter.set(player, "varbit.option_collection_new_item", 3)
        }
        fun messages(p: Player) = (p.client as RecordingClient).messages.filterIsInstance<MessageGame>()
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
