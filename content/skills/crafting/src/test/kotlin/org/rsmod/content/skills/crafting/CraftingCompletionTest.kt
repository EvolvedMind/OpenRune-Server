package org.rsmod.content.skills.crafting

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import kotlin.coroutines.*
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
import org.rsmod.api.script.onEvent
import org.rsmod.content.skills.Material
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@ResourceLock("ServerCacheManager")
class CraftingCompletionTest {
    @Test fun `crafting reports its product only after ingredients are consumed and output exists`() {
        val f = Fixture()
        f.player.inv[0] = InvObj("obj.wool")
        assertTrue(f.craft())
        val event = f.completed.single()
        assertEquals("stat.crafting", event.skill)
        assertEquals("obj.ball_of_wool", event.item)
        assertEquals(1, event.count)
        assertEquals(SkillingProductSource.Crafting, event.source)
        assertFalse(event.isBonus)
        assertEquals(0, f.ingredientsAtEvent)
        assertEquals(1, f.outputAtEvent)
    }

    @Test fun `missing ingredients and failed inventory output never report completion`() {
        val missing = Fixture()
        assertFalse(missing.craft())
        assertTrue(missing.completed.isEmpty())
        val full = Fixture()
        full.player.inv[0] = InvObj("obj.wool")
        for (slot in 1..27) full.player.inv[slot] = InvObj("obj.abyssal_whip")
        assertFalse(full.craft(outputCount = 2))
        assertTrue(full.completed.isEmpty())
        assertEquals(1, full.player.inv.count("obj.wool"))
        assertEquals(0, full.player.inv.count("obj.ball_of_wool"))
    }

    private class Fixture {
        val events = EventBus()
        val completed = mutableListOf<SkillingActionContext.Product>()
        var ingredientsAtEvent = -1
        var outputAtEvent = -1
        val player = Player().apply {
            inv = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())), arrayOfNulls(28))
        }
        init {
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { scripts.startup() }
            scripts.onEvent<SkillingActionCompleteEvent> {
                completed += context as SkillingActionContext.Product
                ingredientsAtEvent = player.inv.count("obj.wool")
                outputAtEvent = player.inv.count("obj.ball_of_wool")
            }
        }
        fun craft(outputCount: Int = 1): Boolean {
            val recipe = CraftingProduct(CraftingSection.SPINNING, "obj.ball_of_wool", outputCount,
                inputs = listOf(Material("obj.wool", 1)), level = 1, xp = 0.0, ticks = listOf(0), actionName = "spin wool")
            val access = ProtectedAccess(player, GameCoroutine("craft-complete-test"), ProtectedAccessContextFactory.empty().copy(getEventBus = { events }))
            var result: Result<Boolean>? = null
            val block: suspend () -> Boolean = { access.craftOnce(recipe) }
            block.startCoroutine(object : Continuation<Boolean> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(value: Result<Boolean>) { result = value }
            })
            return checkNotNull(result).getOrThrow()
        }
    }
    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
