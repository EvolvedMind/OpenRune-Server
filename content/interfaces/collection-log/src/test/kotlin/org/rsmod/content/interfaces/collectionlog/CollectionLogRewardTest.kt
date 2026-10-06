package org.rsmod.content.interfaces.collectionlog

import dev.openrune.ServerCacheManager
import dev.openrune.types.ItemServerType
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.table.CollectionLogCategoriesRow
import org.rsmod.api.player.output.PlayerNotifications
import org.rsmod.api.player.output.ChatType
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
    @Test fun `each qualifying obtain sends styled chat news to everyone without banners or observer popups`() {
        val f = Fixture()
        f.log.grant(f.player, 4151, 2, "Abyssal demon")
        f.log.grant(f.player, 4151, 1, "Abyssal demon")
        assertEquals(3, f.player.collectionTransmit.countOf(4151))
        val broadcasts = f.messages(f.observer)
        assertEquals(2, broadcasts.size)
        assertTrue(broadcasts.all { it.type == ChatType.GameMessage.id })
        assertEquals("<img=19> <col=ff0000>News:</col> Bram received <col=008000>2 x Abyssal whip</col> from Abyssal demon!", broadcasts[0].message)
        assertTrue(broadcasts[1].message.contains("1 x Abyssal whip</col> from Abyssal demon!"))
        assertFalse(f.messages(f.player).any { it.type == ChatType.Broadcast.id })
        assertEquals(broadcasts.map { it.message }, f.messages(f.player).filter { it.message.startsWith("<img=19>") }.map { it.message })
        assertTrue((f.observer.client as RecordingClient).messages.filterIsInstance<RunClientScript>().isEmpty())
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
        assertTrue(f.messages(f.observer).single().message.contains("1 x Abyssal whip</col>!"))
    }
    @Test fun `personal settings do not silence chat news and saturated counts stay safe`() {
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
    @Test fun `news checks unit GE value inclusively while cheap large stacks still update the log`() {
        val f = Fixture()
        f.gePrice = 999_999L
        f.log.grant(f.player, 4151, 100)
        assertEquals(100, f.player.collectionTransmit.countOf(4151))
        assertTrue(f.messages(f.observer).isEmpty())
        assertEquals(1, f.messages(f.player).count { it.message.startsWith("New item added") })
        PlayerNotifications.pulse(f.player)
        assertEquals(1, f.popups().size)
        f.gePrice = 1_000_000L
        f.log.grant(f.player, 4151)
        assertEquals(1, f.messages(f.observer).size)
        f.gePrice = Long.MAX_VALUE
        f.log.grant(f.player, 4151)
        assertEquals(2, f.messages(f.observer).size)
    }

    @Test fun `missing GE value cannot broadcast using an expensive cache fallback`() {
        val f = Fixture()
        f.gePrice = null
        f.log.grant(f.player, 4151, Int.MAX_VALUE)
        assertEquals(Int.MAX_VALUE, f.player.collectionTransmit.countOf(4151))
        assertTrue(f.messages(f.observer).isEmpty())
    }

    @Test fun `all native collection pets broadcast without a GE price including duplicates`() {
        val f = Fixture()
        f.gePrice = null
        val row = CollectionLogCategoriesRow.getRow("dbrow.collection_log_category_all_pets")
        val pets = CollectionLogItems.itemsInCategoryStruct(row.structId)
        assertTrue(pets.isNotEmpty())
        for (pet in pets) {
            assertTrue(CollectionLogItems.contains(pet))
            f.log.grant(f.player, pet)
        }
        f.log.grant(f.player, pets.first())
        assertEquals(pets.size + 1, f.messages(f.observer).size)
        assertTrue(f.messages(f.observer).all { it.type == ChatType.GameMessage.id })
        assertEquals(2, f.player.collectionTransmit.countOf(pets.first()))
    }

    private class Fixture {
        val player = Player(RecordingClient()).apply { displayName = "Bram" }
        val observer = Player(RecordingClient())
        val players = PlayerList().apply { this[1] = player; this[2] = observer }
        var gePrice: Long? = 1_000_000L
        private val prices = object : MarketPrices {
            override fun get(type: ItemServerType): Int = 2_000_000
            override fun gePrice(type: ItemServerType): Long? = gePrice
        }
        val log = CollectionLog(players, prices)
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
