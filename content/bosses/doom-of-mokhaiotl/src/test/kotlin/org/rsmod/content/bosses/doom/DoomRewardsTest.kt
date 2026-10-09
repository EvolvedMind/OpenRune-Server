package org.rsmod.content.bosses.doom

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dtx.impl.chance.RateBoosts
import dtx.rs.RSDropTable
import dtx.rs.rsGuaranteedTable
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.Mockito.*
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.DropTableRegistry
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.drops.tables.monsters.doomOfMokhaiotlDropTable
import org.rsmod.content.interfaces.collectionlog.CollectionLog
import org.rsmod.content.interfaces.collectionlog.collectionTransmit
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.inv.InvObj
import org.rsmod.game.obj.Obj
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@ResourceLock("ServerCacheManager")
@ResourceLock("RateBoosts")
class DoomRewardsTest {
    @Test fun `production drop table flows through native reward rolls without changing the active delve`() {
        val player = Player()
        VarPlayerIntMapSetter.set(player, DoomRewards.LEVEL_VARP, 7)
        val npc = Npc(checkNotNull(ServerCacheManager.getNpc("npc.dom_boss".asRSCM())), CoordGrid(1310, 9570, 0))
        val registry = mock(DropTableRegistry::class.java)
        `when`(registry.forNpc("npc.dom_boss")).thenReturn(doomOfMokhaiotlDropTable)
        val rewards = DoomRewards(registry, DefaultGameRandom(42))
        val principal = listOf("obj.mokhaiotl_cloth", "obj.eye_of_ayak_uncharged", "obj.avernic_treads").map { it.asRSCM() }
        val previous = RateBoosts.multiplier
        try {
            // Saturating the existing boost makes rare-roll coverage deterministic.
            RateBoosts.multiplier = { _, _ -> Double.POSITIVE_INFINITY }
            for (level in listOf(1, 2, 3, 4, 5, 6, 8, 9, 1000)) {
                val rolled = rewards.roll(player, npc, level)
                val unique = rolled.filter { it.id in principal }
                assertEquals(if (level == 1) 0 else 1, unique.size, "delve $level")
                assertTrue(unique.all { it.id in principal.take((level - 1).coerceIn(0, 3)) && it.count == 1 })
                assertEquals(if (level >= 6) 1 else 0, rolled.count { it.id == "obj.dompet".asRSCM() })
                assertEquals(7, player.vars[DoomRewards.LEVEL_VARP])
                assertTrue(player.invMap.isEmpty())
            }
        } finally {
            RateBoosts.multiplier = previous
        }
    }

    @Test fun `quantity scaling and guaranteed tears cover shallow and unbounded deep levels`() {
        assertEquals(50, DoomRewards.scaledCount(100, 1))
        assertEquals(65, DoomRewards.scaledCount(100, 2))
        assertEquals(100, DoomRewards.scaledCount(100, 3))
        assertEquals(117, DoomRewards.scaledCount(100, 8))
        assertEquals(120, DoomRewards.scaledCount(100, 1000))
        assertEquals(1, DoomRewards.scaledCount(1, 1))
        assertEquals(Int.MAX_VALUE, DoomRewards.scaledCount(Int.MAX_VALUE, 1000))
        assertEquals(0, DoomRewards.guaranteedTears(2))
        assertEquals(50, DoomRewards.guaranteedTears(3))
        assertEquals(100, DoomRewards.guaranteedTears(8))
        assertEquals(100, DoomRewards.guaranteedTears(Int.MAX_VALUE))
    }

    @Test fun `roll evaluates requested delve and restores the active level even after failure`() {
        val player = Player().apply { VarPlayerIntMapSetter.set(this, DoomRewards.LEVEL_VARP, 3) }
        val npc = Npc(checkNotNull(ServerCacheManager.getNpc("npc.dom_boss".asRSCM())), CoordGrid(1310, 9570, 0))
        val registry = mock(DropTableRegistry::class.java)
        val item = DropRollItem("obj.coins", 100, condition = { it.vars[DoomRewards.LEVEL_VARP] == 7 })
        val table = RSDropTable("test", guaranteed = rsGuaranteedTable<Player, DropRollItem> { add(item) })
        `when`(registry.forNpc("npc.dom_boss")).thenReturn(table)
        val roller = DoomRewards(registry, DefaultGameRandom(42))
        val rolled = roller.roll(player, npc, 8)
        assertEquals(117, rolled.first { it.id == "obj.coins".asRSCM() }.count)
        assertEquals(100, rolled.first { it.id == "obj.demon_tear".asRSCM() }.count)
        assertEquals(3, player.vars[DoomRewards.LEVEL_VARP])
        `when`(registry.forNpc("npc.dom_boss")).thenThrow(IllegalStateException("broken table"))
        assertThrows(IllegalStateException::class.java) { roller.roll(player, npc, 8) }
        assertEquals(3, player.vars[DoomRewards.LEVEL_VARP])
    }

    @Test fun `test loot puts rewards on the ground without touching a live run or escrow`() {
        val player = Player().apply { coords = CoordGrid(1311, 9551, 0); uuid = 1; observerUUID = 1; currentMapClock = 100; VarPlayerIntMapSetter.set(this, DoomRewards.LEVEL_VARP, 4) }
        val items = listOf(org.rsmod.game.inv.InvObj("obj.coins", 120))
        val roller = mock(DoomRewards::class.java) { call ->
            if (call.method.name == "roll") items else RETURNS_DEFAULTS.answer(call)
        }
        val objs = mock(ObjRepository::class.java)
        val log = mock(CollectionLog::class.java)
        DoomTestLoot(roller, objs, log).generate(player, 2, 8)
        val rolls = mockingDetails(roller).invocations.filter { it.method.name == "roll" }
        assertEquals(2, rolls.size)
        assertTrue(rolls.all { it.arguments[0] === player && it.arguments[2] == 8 })
        val ground = mockingDetails(objs).invocations.filter { it.method.name.startsWith("add") }
        assertEquals(2, ground.size)
        assertTrue(ground.all { (it.arguments[0] as Obj).let { obj -> obj.type == "obj.coins".asRSCM() && obj.count == 120 && obj.ownerId == player.observerUUID } })
        assertEquals(4, player.vars[DoomRewards.LEVEL_VARP])
        assertTrue(player.invMap.isEmpty())
        verifyNoInteractions(log)
        assertThrows(IllegalArgumentException::class.java) { DoomTestLoot(roller, objs, log).generate(player, 0) }
    }

    @Test fun `delivered Doom test uniques increment real collection counts on each roll`() {
        val f = SampleFixture(delivered = true)
        f.loot.generate(f.player, 2, 8, logRewards = true)
        for (item in f.items) assertEquals(2, f.player.collectionTransmit.objs.filterNotNull().single { it.id == item.id }.count)
        assertEquals(8, f.broadcasts().size)
        assertTrue(f.broadcasts().all { it.message.contains("from Doom of Mokhaiotl!") })
        assertEquals(4, f.player.vars[DoomRewards.LEVEL_VARP])
        assertEquals(100, f.player.invMap.getOrPut("inv.dom_lootpile_during").objs.filterNotNull().single().count)
        assertEquals(50, f.player.invMap.getOrPut("inv.dom_lootpile").objs.filterNotNull().single().count)
    }

    @Test fun `rejected ground delivery and simulation never register collection obtains`() {
        val rejected = SampleFixture(delivered = false)
        rejected.loot.generate(rejected.player, 1, 8, logRewards = true)
        assertTrue(rejected.player.collectionTransmit.objs.all { it == null })
        assertTrue(rejected.broadcasts().isEmpty())
        val simulation = SampleFixture(delivered = true)
        simulation.loot.generate(simulation.player, 1, 8)
        assertTrue(simulation.player.collectionTransmit.objs.all { it == null })
        assertTrue(simulation.broadcasts().isEmpty())
    }

    @Test fun `delivered cheap Doom samples still log while only the valuable uniques and pet send news`() {
        val f = SampleFixture(delivered = true, filterCheapItems = true)
        f.loot.generate(f.player, 2, 8, logRewards = true)
        for (item in f.items) assertEquals(item.count * 2, f.player.collectionTransmit.objs.filterNotNull().single { it.id == item.id }.count)
        assertEquals(8, f.broadcasts().size)
        assertFalse(f.broadcasts().any { it.message.contains("Demon tear") })
        assertFalse(f.broadcasts().any { it.message.contains("Mokhaiotl waystone") })
        assertEquals(4, f.player.vars[DoomRewards.LEVEL_VARP])
        assertEquals(100, f.player.invMap.getOrPut("inv.dom_lootpile_during").objs.filterNotNull().single().count)
        assertEquals(50, f.player.invMap.getOrPut("inv.dom_lootpile").objs.filterNotNull().single().count)
    }
    private class SampleFixture(delivered: Boolean, filterCheapItems: Boolean = false) {
        val player = Player(RecordingClient()).apply {
            coords = CoordGrid(1311, 9551, 0); uuid = 1; observerUUID = 1; currentMapClock = 100
            displayName = "Bram"; VarPlayerIntMapSetter.set(this, DoomRewards.LEVEL_VARP, 4)
        }
        val observer = Player(RecordingClient())
        val items = DoomRewards.UNIQUES.map { InvObj(it, 1) } +
            if (filterCheapItems) listOf(InvObj("obj.demon_tear", 100), InvObj("obj.dom_teleport_item", 2)) else emptyList()
        val loot: DoomTestLoot
        init {
            val ctx = ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache())
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { ctx.startup() }
            player.invMap.getOrPut("inv.dom_lootpile_during")[0] = InvObj("obj.coins", 100)
            player.invMap.getOrPut("inv.dom_lootpile")[0] = InvObj("obj.coins", 50)
            val roller = mock(DoomRewards::class.java) { call -> if (call.method.name == "roll") items else RETURNS_DEFAULTS.answer(call) }
            val objs = mock(ObjRepository::class.java) { call ->
                if (call.method.name == "add" && call.method.parameterTypes.first() == Obj::class.java) delivered else RETURNS_DEFAULTS.answer(call)
            }
            val players = PlayerList().apply { this[1] = player; this[2] = observer }
            val prices = mock(MarketPrices::class.java) { call ->
                if (call.method.name == "gePrice") {
                    val type = call.arguments[0] as dev.openrune.types.ItemServerType
                    if (filterCheapItems && type.id in setOf("obj.demon_tear".asRSCM(), "obj.dom_teleport_item".asRSCM())) 999_999L else 1_000_000L
                } else RETURNS_DEFAULTS.answer(call)
            }
            loot = DoomTestLoot(roller, objs, CollectionLog(players, prices))
        }
        fun broadcasts() = (observer.client as RecordingClient).messages.filterIsInstance<MessageGame>()
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
    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
