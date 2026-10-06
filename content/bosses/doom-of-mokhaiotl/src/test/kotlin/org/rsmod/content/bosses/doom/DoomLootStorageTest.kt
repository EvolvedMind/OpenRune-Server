package org.rsmod.content.bosses.doom

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.Mockito.*
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.market.MarketPrices
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.inv.InvObj
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@ResourceLock("ServerCacheManager")
class DoomLootStorageTest {
    @Test fun `cache packs persistent stacked piles larger than the complete reward item set`() {
        val loot = fixture()
        val player = Player(RecordingClient())
        for (inv in listOf(loot.earned(player), loot.claimed(player))) {
            assertEquals(40, inv.objs.size)
            assertEquals("Perm", inv.type.scope.toString())
            assertEquals("Always", inv.type.stack.toString())
        }
    }
    @Test fun `failed stashing preserves both reward piles including item vars`() {
        val loot = fixture()
        val player = Player(RecordingClient())
        val earned = loot.earned(player)
        val claimed = loot.claimed(player)
        earned[0] = InvObj("obj.coins", 2)
        claimed[0] = InvObj("obj.coins", Int.MAX_VALUE)
        val beforeEarned = earned.objs.toList()
        val beforeClaimed = claimed.objs.toList()
        assertFalse(loot.stash(player))
        assertEquals(beforeEarned, earned.objs.toList())
        assertEquals(beforeClaimed, claimed.objs.toList())
    }
    @Test fun `successful stashing moves the whole pile once and repeating cannot duplicate it`() {
        val loot = fixture()
        val player = Player(RecordingClient())
        loot.earned(player)[0] = InvObj("obj.coins", 100)
        loot.claimed(player)[0] = InvObj("obj.coins", 50)
        assertTrue(loot.stash(player))
        assertTrue(loot.earned(player).objs.all { it == null })
        assertEquals(150, loot.claimed(player).objs.filterNotNull().single().count)
        assertTrue(loot.stash(player))
        assertEquals(150, loot.claimed(player).objs.filterNotNull().single().count)
    }
    @Test fun `death loses only the active run pile and later leave cannot stash it`() {
        val loot = fixture()
        val player = Player(RecordingClient())
        loot.earned(player)[0] = InvObj("obj.coins", 100)
        loot.claimed(player)[0] = InvObj("obj.coins", 50)
        val instances = mock(InstanceManager::class.java)
        val session = mock(org.rsmod.api.instances.InstanceSession::class.java)
        `when`(session.key).thenReturn("doom_of_mokhaiotl")
        `when`(instances.sessionForPlayer(player)).thenReturn(session)
        assertEquals(DoomArena.LOBBY, DoomRespawnHook(instances, loot).respawnCoords(player))
        assertTrue(loot.earned(player).objs.all { it == null })
        assertTrue(loot.stash(player))
        assertEquals(50, loot.claimed(player).objs.filterNotNull().single().count)
    }
    @Test fun `unrelated death does not discard Doom chest or run rewards`() {
        val loot = fixture()
        val player = Player(RecordingClient())
        loot.earned(player)[0] = InvObj("obj.coins", 100)
        loot.claimed(player)[0] = InvObj("obj.coins", 50)
        assertNull(DoomRespawnHook(mock(InstanceManager::class.java), loot).respawnCoords(player))
        assertEquals(100, loot.earned(player).objs.filterNotNull().single().count)
        assertEquals(50, loot.claimed(player).objs.filterNotNull().single().count)
    }
    private fun fixture(): DoomLoot {
        val ctx = ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache())
        with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { ctx.startup() }
        return DoomLoot(mock(DoomDelves::class.java), mock(MarketPrices::class.java), mock(DoomRewards::class.java),
            mock(InstanceManager::class.java), PlayerList(), mock(DoomStats::class.java))
    }
    private class RecordingClient : Client<Any, Any> {
        override fun write(message: Any) = Unit
        override fun close() = Unit
        override fun read(player: Player) = Unit
        override fun flush() = Unit
        override fun flushHighPriority() = Unit
        override fun unregister(service: Any, player: Player) = Unit
    }
    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
