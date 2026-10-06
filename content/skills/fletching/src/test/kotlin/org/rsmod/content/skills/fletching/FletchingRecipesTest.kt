package org.rsmod.content.skills.fletching

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
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
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
class FletchingRecipesTest {
    @Test fun `recipes consume ingredients and publish only after creating output`() {
        for (recipe in ClueFletchingRecipes.recipes) {
            val f = Fixture()
            f.player.inv[0] = InvObj(recipe.first, recipe.batch)
            f.player.inv[1] = InvObj(recipe.second, recipe.batch)
            assertTrue(f.access.fletch(recipe.copy(xp = 0.0)))
            assertEquals(0, f.player.inv.count(recipe.first))
            assertEquals(0, f.player.inv.count(recipe.second))
            assertEquals(recipe.batch, f.player.inv.count(recipe.output))
            assertEquals(recipe.output, f.events.single().item)
            assertEquals(recipe.batch, f.events.single().count)
        }
    }

    @Test fun `failed output and low levels preserve materials and never publish completion`() {
        val recipe = ClueFletchingRecipes.recipes.last().copy(xp = 0.0)
        val f = Fixture()
        f.player.inv[0] = InvObj(recipe.first, 100)
        f.player.inv[1] = InvObj(recipe.second, 100)
        for (slot in 2..27) f.player.inv[slot] = InvObj("obj.abyssal_whip")
        assertFalse(f.access.fletch(recipe))
        assertEquals(100, f.player.inv.count(recipe.first))
        assertEquals(100, f.player.inv.count(recipe.second))
        f.player.inv[2] = null
        f.player.statMap.setCurrentLevel("stat.fletching", 1)
        assertFalse(f.access.fletch(recipe))
        assertTrue(f.events.isEmpty())
    }

    @Test fun `remaining dart ingredients create only the available partial batch`() {
        val f = Fixture()
        val recipe = ClueFletchingRecipes.recipes.last().copy(xp = 0.0)
        f.player.inv[0] = InvObj(recipe.first, 7)
        f.player.inv[1] = InvObj(recipe.second, 3)
        assertTrue(f.access.fletch(recipe))
        assertEquals(3, f.player.inv.count(recipe.output))
        assertEquals(4, f.player.inv.count(recipe.first))
        assertEquals(3, f.events.single().count)
    }
    private class Fixture {
        val bus = EventBus()
        val events = mutableListOf<SkillingActionContext.Product>()
        val player = Player().apply {
            inv = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())), arrayOfNulls(28))
            statMap.setCurrentLevel("stat.fletching", 99)
        }
        val access = ProtectedAccess(player, GameCoroutine("fletching-test"), ProtectedAccessContextFactory.empty().copy(getEventBus = { bus }))
        init {
            val scripts = ScriptContext(bus, CheatCommandMap(), EngineQueueCache())
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { scripts.startup() }
            scripts.onEvent<SkillingActionCompleteEvent> {
                val product = context as SkillingActionContext.Product
                assertEquals(product.count, player.inv.count(product.item))
                events += product
            }
        }
    }
    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
