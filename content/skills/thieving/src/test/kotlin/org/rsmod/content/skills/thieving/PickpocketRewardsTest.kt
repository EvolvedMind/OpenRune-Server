package org.rsmod.content.skills.thieving

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.events.skilling.PickpocketSuccessEvent
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onEvent
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@ResourceLock("ServerCacheManager")
class PickpocketRewardsTest {
    @Test fun `all 53 native elf targets have pickpocket option and resolved loot`() {
        val symbols = ElfPickpockets.targets.flatMap { it.npcs }
        assertEquals(53, symbols.size)
        assertEquals(53, symbols.map { it.asRSCM() }.distinct().size)
        for (symbol in symbols) {
            val npc = ServerCacheManager.getNpc(symbol.asRSCM())!!
            assertEquals("Pickpocket", npc.actions.getOpOrNull(2))
        }
        for (symbol in listOf("obj.pickpocket_coin_pouch_elf", "obj.prif_teleport_seed",
            "obj.prif_crystal_shard", "obj.deathrune", "obj.naturerune", "obj.jug_wine",
            "obj.fire_orb", "obj.diamond", "obj.gold_ore") + ElfPickpockets.rogueOutfit.values) {
            assertNotNull(ServerCacheManager.getItem(symbol.asRSCM()), symbol)
        }
    }

    @Test fun `main loot weights produce either a pouch or a primary reward with fractional XP`() {
        for ((roll, item, count) in listOf(Triple(0, "obj.pickpocket_coin_pouch_elf", 1),
            Triple(105, "obj.deathrune", 2), Triple(113, "obj.naturerune", 3),
            Triple(118, "obj.jug_wine", 1), Triple(124, "obj.fire_orb", 1),
            Triple(126, "obj.diamond", 1), Triple(127, "obj.gold_ore", 1))) {
            val f = Fixture(rolls = listOf(roll))
            val reward = f.award()!!
            assertEquals(item, reward.single().first.obj)
            assertEquals(count, f.player.inv.count(item))
            assertEquals(if (item.contains("pouch")) count else 0, f.player.inv.count("obj.pickpocket_coin_pouch_elf"))
            assertEquals(353, f.player.statMap.getXP("stat.thieving"))
            assertEquals(PickpocketSuccessEvent.Group.Elf, f.events.single().group)
        }
    }

    @Test fun `Prif special rolls replace primary loot and rogue outfit doubles seeds but not shards`() {
        for ((rolls, item) in listOf(listOf(0) to "obj.prif_teleport_seed",
            listOf(1, 0) to "obj.prif_crystal_shard", listOf(1, 1, 105) to "obj.deathrune")) {
            val f = Fixture(prif = true, rolls = rolls)
            for ((slot, obj) in ElfPickpockets.rogueOutfit) f.player.worn[slot] = InvObj(obj)
            f.award()
            assertEquals(if (item.contains("shard")) 1 else if (item.contains("rune")) 4 else 2, f.player.inv.count(item))
            assertEquals(0, f.player.inv.count("obj.pickpocket_coin_pouch_elf"))
            assertEquals(1, f.events.size)
        }
    }

    @Test fun `every missing rogue slot prevents doubling`() {
        for (missing in ElfPickpockets.rogueOutfit.keys) {
            val f = Fixture(rolls = listOf(105))
            for ((slot, obj) in ElfPickpockets.rogueOutfit) if (slot != missing) f.player.worn[slot] = InvObj(obj)
            f.award()
            assertEquals(2, f.player.inv.count("obj.deathrune"))
        }
    }

    @Test fun `inventory level target and pouch cap failures award neither XP nor completion`() {
        val f = Fixture(rolls = listOf(105))
        for (slot in 0..27) f.player.inv[slot] = InvObj("obj.abyssal_whip")
        assertNull(f.award())
        f.player.inv[0] = null
        f.player.statMap.setCurrentLevel("stat.thieving", 84)
        assertNull(f.award())
        f.player.statMap.setCurrentLevel("stat.thieving", 99)
        assertNull(f.access.awardPickpocketLoot(f.target, "npc.man".asRSCM()))
        f.player.inv[0] = InvObj("obj.pickpocket_coin_pouch_elf", 28)
        assertNull(f.award())
        assertEquals(28, f.player.inv.count("obj.pickpocket_coin_pouch_elf"))
        assertEquals(0, f.player.statMap.getXP("stat.thieving"))
        assertTrue(f.events.isEmpty())
    }

    @Test fun `pouch opening is atomic with variable coins and gives no XP or pickpocket credit`() {
        val f = Fixture()
        val pouch = f.target.pouch!!
        for (slot in 0..27) f.player.inv[slot] = InvObj("obj.abyssal_whip")
        f.player.inv[0] = InvObj(pouch.obj, 2)
        assertFalse(f.access.openCoinPouches(pouch, false))
        assertEquals(2, f.player.inv.count(pouch.obj))
        assertTrue(f.access.openCoinPouches(pouch, true))
        assertEquals(0, f.player.inv.count(pouch.obj))
        assertTrue(f.player.inv.count("obj.coins") in 560..700)
        assertEquals(0, f.player.statMap.getXP("stat.thieving"))
        assertTrue(f.events.isEmpty())
        assertFalse(f.access.openCoinPouches(pouch, true))
    }

    @Test fun `existing citizen and farmer rewards retain their original amounts`() {
        for (target in ThievingTables.pickpockets.filterNot { it.elf }) {
            val f = Fixture()
            val rewards = f.access.awardPickpocketLoot(target, target.npcs.first().asRSCM())!!
            assertEquals(PickpocketSuccessEvent.Group.Other, f.events.single().group)
            assertEquals(target.xp.toInt(), f.player.statMap.getXP("stat.thieving"))
            for ((loot, count) in rewards) assertEquals(count, f.player.inv.count(loot.obj))
            if (target.pouch != null) {
                assertTrue(f.access.openCoinPouches(target.pouch, true))
                assertEquals(3, f.player.inv.count("obj.coins"))
            }
        }
    }

    private class Rolls(values: List<Int>) : GameRandom {
        private val values = ArrayDeque(values)
        override fun of(maxExclusive: Int) = if (values.isEmpty()) 0 else values.removeFirst().also { require(it in 0 until maxExclusive) }
        override fun of(minInclusive: Int, maxInclusive: Int) = if (minInclusive == maxInclusive) minInclusive else minInclusive + of(maxInclusive - minInclusive + 1)
        override fun randomDouble() = 0.0
    }
    private class Fixture(prif: Boolean = false, rolls: List<Int> = emptyList()) {
        val target = ElfPickpockets.targets[if (prif) 1 else 0]
        val bus = EventBus()
        val events = mutableListOf<PickpocketSuccessEvent>()
        val random = Rolls(rolls)
        val player = Player().apply {
            inv = Inventory(ServerCacheManager.getInventory("inv.inv".asRSCM())!!, arrayOfNulls(28))
            worn = Inventory(ServerCacheManager.getInventory("inv.worn".asRSCM())!!, arrayOfNulls(14))
            statMap.setBaseLevel("stat.thieving", 99.toByte())
            statMap.setCurrentLevel("stat.thieving", 99)
        }
        val access = ProtectedAccess(player, GameCoroutine("pickpocket-test"),
            ProtectedAccessContextFactory.empty().copy(getEventBus = { bus }, getRandom = { random }))
        init {
            val scripts = ScriptContext(bus, CheatCommandMap(), EngineQueueCache())
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { scripts.startup() }
            scripts.onEvent<PickpocketSuccessEvent> {
                assertTrue(player.inv.isNotEmpty())
                assertTrue(player.statMap.getXP("stat.thieving") > 0)
                events += this
            }
        }
        fun award() = access.awardPickpocketLoot(target, target.npcs.first().asRSCM())
    }
    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
