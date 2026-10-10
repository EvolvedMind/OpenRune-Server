package org.rsmod.content.bosses.shaman

import dev.openrune.ServerCacheManager
import dev.or2.central.account.Rights
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.Mockito.*
import org.rsmod.api.player.protect.*
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@ResourceLock("ServerCacheManager")
internal class ShamanCommandsTest {
    @Test fun `registered test command teleports to owner coordinates and rejects invalid inputs`() {
        ServerCacheManager.init(240).close()
        val collision = CollisionFlagMap(); collision.allocateIfAbsent(1451, 3696, 0)
        val factory = mock(ProtectedAccessContextFactory::class.java)
        `when`(factory.create()).thenReturn(ProtectedAccessContextFactory.empty().copy(getCollision = { collision }))
        val commands = CheatCommandMap(); val script = ShamanCommands(ProtectedAccessLauncher(factory))
        with(script) { ScriptContext(EventBus(), commands, EngineQueueCache()).startup() }
        val player = Player().apply { modLevel = Rights.ADMINISTRATOR; coords = CoordGrid(3222, 3218) }
        assertTrue(commands.execute(player, "test", listOf("shamans")))
        assertEquals(CoordGrid(1451, 3696, 0), player.coords)
        player.coords = CoordGrid(3222, 3218)
        commands.execute(player, "test", emptyList()); commands.execute(player, "test", listOf("shamans", "oops"))
        assertEquals(CoordGrid(3222, 3218), player.coords)
    }
}
