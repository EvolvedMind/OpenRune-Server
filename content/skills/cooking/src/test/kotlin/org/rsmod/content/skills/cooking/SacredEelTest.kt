package org.rsmod.content.skills.cooking

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
import org.rsmod.plugin.scripts.ScriptContext

@ResourceLock("ServerCacheManager")
class SacredEelTest {
    @Test fun `dissection replaces one eel keeps knife and awards XP for the produced scales`() {
        for ((level, range) in listOf(72 to 3..5, 79 to 3..5, 80 to 4..6, 87 to 4..6, 88 to 5..7, 95 to 5..7, 96 to 6..8, 103 to 6..8, 104 to 7..9, 120 to 7..9)) {
            val f = Fixture()
            f.player.statMap.setCurrentLevel("stat.cooking", level.toByte())
            f.player.inv[0] = InvObj("obj.knife")
            f.player.inv[1] = InvObj(SacredEelScript.EEL)
            assertTrue(f.access.dissectSacredEel())
            assertEquals(range, SacredEelScript.scaleRange(level))
            val count = f.player.inv.count(SacredEelScript.SCALES)
            assertTrue(count in range)
            assertEquals(1, f.player.inv.count("obj.knife"))
            assertEquals(0, f.player.inv.count(SacredEelScript.EEL))
            assertEquals(100 + 3 * count, f.player.statMap.getXP("stat.cooking"))
            assertEquals(SkillingProductSource.SacredEel, f.products.single().source)
            assertEquals(count, f.products.single().count)
        }
    }

    @Test fun `missing tool missing eel and insufficient level never award anything`() {
        val f = Fixture()
        f.player.inv[0] = InvObj(SacredEelScript.EEL)
        assertFalse(f.access.dissectSacredEel())
        f.player.inv[1] = InvObj("obj.knife")
        f.player.statMap.setCurrentLevel("stat.cooking", 71)
        assertFalse(f.access.dissectSacredEel())
        assertEquals(1, f.player.inv.count(SacredEelScript.EEL))
        f.player.statMap.setCurrentLevel("stat.cooking", 99)
        f.player.inv[0] = null
        assertFalse(f.access.dissectSacredEel())
        assertTrue(f.products.isEmpty())
        assertEquals(0, f.player.statMap.getXP("stat.cooking"))
    }

    @Test fun `a full inventory can replace an eel while a saturated scale stack rolls back`() {
        val f = Fixture()
        for (slot in 0..27) f.player.inv[slot] = InvObj("obj.abyssal_whip")
        f.player.inv[0] = InvObj("obj.knife")
        f.player.inv[1] = InvObj(SacredEelScript.EEL)
        assertTrue(f.access.dissectSacredEel())
        val g = Fixture()
        g.player.inv[0] = InvObj("obj.knife")
        g.player.inv[1] = InvObj(SacredEelScript.EEL)
        g.player.inv[2] = InvObj(SacredEelScript.SCALES, Int.MAX_VALUE)
        assertFalse(g.access.dissectSacredEel())
        assertEquals(1, g.player.inv.count(SacredEelScript.EEL))
        assertEquals(Int.MAX_VALUE, g.player.inv.count(SacredEelScript.SCALES))
        assertTrue(g.products.isEmpty())
        assertEquals(0, g.player.statMap.getXP("stat.cooking"))
    }

    private class Fixture {
        val bus = EventBus()
        val products = mutableListOf<SkillingActionContext.Product>()
        val player = Player().apply {
            inv = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())), arrayOfNulls(28))
            statMap.setBaseLevel("stat.cooking", 99.toByte())
            statMap.setCurrentLevel("stat.cooking", 99)
        }
        val access = ProtectedAccess(player, GameCoroutine("eel-test"), ProtectedAccessContextFactory.empty().copy(getEventBus = { bus }, getRandom = { DefaultGameRandom(42) }))
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
