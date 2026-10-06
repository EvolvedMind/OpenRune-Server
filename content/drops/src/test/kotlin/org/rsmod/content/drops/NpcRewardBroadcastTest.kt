package org.rsmod.content.drops

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dtx.core.Single
import dtx.rs.RSDropTable
import dtx.rs.RSGuaranteedTable
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.Mockito.*
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.config.constants
import org.rsmod.api.death.NpcDeathDropHook
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.DropTableRegistry
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.interfaces.collectionlog.CollectionLog
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.obj.Obj
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@ResourceLock("ServerCacheManager")
class NpcRewardBroadcastTest {
    @Test fun `native kill roll broadcasts source on each real reward and preserves ground delivery`() {
        val f = Fixture(false)
        f.hook.onKill(f.context); f.hook.onKill(f.context)
        verify(f.repository, times(2)).add(f.symbol, f.coords, constants.lootdrop_duration, f.player, 1)
        assertEquals(2, f.broadcasts().size)
        assertTrue(f.broadcasts().all { it.message.contains("from ${f.npc.name}!") })
        assertEquals(2, f.player.invMap.getOrPut("inv.collection_transmit").objs.filterNotNull().single().count)
    }
    @Test fun `consumed table reward still broadcasts exactly once without spawning a second item`() {
        val f = Fixture(true)
        f.hook.onKill(f.context)
        verifyNoInteractions(f.repository)
        assertEquals(1, f.broadcasts().size)
    }
    private class Fixture(consumed: Boolean) {
        val coords = CoordGrid(3200, 3200)
        val player = Player(RecordingClient()).apply { displayName = "Bram"; uuid = 1; observerUUID = 1; currentMapClock = 100 }
        val observer = Player(RecordingClient())
        val npc = Npc(ServerCacheManager.getNpc(415)!!, coords)
        val symbol = RSCM.getReverseMapping(RSCMType.OBJ, 4151)
        val repository = mock(ObjRepository::class.java)
        val context = NpcDeathKillContext(player, npc, 0)
        val hook: NpcDropTableKillHook
        init {
            val scriptContext = ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache())
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { scriptContext.startup() }
            val registry = mock(DropTableRegistry::class.java)
            val checker = mock(AreaChecker::class.java)
            val table = RSDropTable<Player, DropRollItem>("test", guaranteed = RSGuaranteedTable("test", listOf(Single(DropRollItem(symbol, 1)))))
            `when`(registry.forNpc(npc, checker)).thenReturn(table)
            if (!consumed) `when`(repository.add(symbol, coords, constants.lootdrop_duration, player, 1)).thenReturn(Obj.fromOwner(player, coords, symbol, 1))
            val players = PlayerList().apply { this[1] = player; this[2] = observer }
            val prices = mock(MarketPrices::class.java)
            `when`(prices.gePrice(ServerCacheManager.getItem(4151)!!)).thenReturn(1_000_000L)
            hook = NpcDropTableKillHook(CollectionLog(players, prices), registry, checker, repository, mock(GameRandom::class.java),
                if (consumed) setOf(NpcDeathDropHook { true }) else emptySet())
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
    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() }
    }
}
