package org.rsmod.content.skills.thieving

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.events.skilling.SkillingActionCompleteEvent
import org.rsmod.api.player.events.skilling.SkillingActionContext
import org.rsmod.api.player.events.skilling.SkillingProductSource
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.script.onEvent
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@ResourceLock("ServerCacheManager")
class StallRewardsTest {
    @Test fun `gem theft places actual loot then publishes its stall and location with XP`() {
        val f = Fixture()
        val stall = ThievingTables.stalls.first { it.loc == "loc.gemthiefstall" }
        val coords = CoordGrid(2667, 3303, 0)
        val (loot, count) = checkNotNull(f.access.awardStallLoot(stall, coords))
        assertEquals(1, count)
        assertTrue(loot.obj in setOf("obj.uncut_sapphire", "obj.uncut_emerald", "obj.uncut_ruby", "obj.uncut_diamond"))
        assertEquals(1, f.player.inv.count(loot.obj))
        assertEquals(408, f.player.statMap.getXP("stat.thieving"))
        assertEquals(SkillingProductSource.ThievingStall(stall.loc, coords), f.products.single().source)
        for (symbol in stall.owners + stall.guards) assertNotNull(ServerCacheManager.getNpc(symbol.asRSCM()), symbol)
        assertNotNull(ServerCacheManager.getObject(stall.emptyLoc!!.asRSCM()))
    }

    @Test fun `full inventory or insufficient level gives no loot XP or completion`() {
        val f = Fixture()
        val stall = ThievingTables.stalls.first { it.loc == "loc.gemthiefstall" }
        val coords = CoordGrid(2667, 3303, 0)
        for (slot in 0..27) f.player.inv[slot] = InvObj("obj.abyssal_whip")
        assertNull(f.access.awardStallLoot(stall, coords))
        assertEquals(28, f.player.inv.count("obj.abyssal_whip"))
        f.player.inv[0] = null
        f.player.statMap.setCurrentLevel("stat.thieving", 74)
        assertNull(f.access.awardStallLoot(stall, coords))
        assertNull(f.player.inv[0])
        assertEquals(0, f.player.statMap.getXP("stat.thieving"))
        assertTrue(f.products.isEmpty())
    }

    private class Fixture {
        val bus = EventBus()
        val products = mutableListOf<SkillingActionContext.Product>()
        val player = Player().apply {
            inv = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())), arrayOfNulls(28))
            statMap.setBaseLevel("stat.thieving", 99.toByte())
            statMap.setCurrentLevel("stat.thieving", 99)
        }
        val access = ProtectedAccess(player, GameCoroutine("stall-test"), ProtectedAccessContextFactory.empty().copy(getEventBus = { bus }, getRandom = { DefaultGameRandom(42) }))
        init {
            val scripts = ScriptContext(bus, CheatCommandMap(), EngineQueueCache())
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { scripts.startup() }
            scripts.onEvent<SkillingActionCompleteEvent> {
                val product = context as SkillingActionContext.Product
                assertEquals(product.count, player.inv.count(product.item))
                products += product
            }
        }
    }
    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
