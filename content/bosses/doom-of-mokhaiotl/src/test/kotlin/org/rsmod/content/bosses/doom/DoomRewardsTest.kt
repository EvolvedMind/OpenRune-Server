package org.rsmod.content.bosses.doom

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dtx.rs.RSDropTable
import dtx.rs.rsGuaranteedTable
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.Mockito.*
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.DropTableRegistry
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.map.CoordGrid

@ResourceLock("ServerCacheManager")
class DoomRewardsTest {
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
        val player = Player().apply { coords = CoordGrid(1311, 9551, 0); VarPlayerIntMapSetter.set(this, DoomRewards.LEVEL_VARP, 4) }
        val items = listOf(org.rsmod.game.inv.InvObj("obj.coins", 120))
        val roller = mock(DoomRewards::class.java) { call ->
            if (call.method.name == "roll") items else RETURNS_DEFAULTS.answer(call)
        }
        val objs = mock(ObjRepository::class.java)
        DoomTestLoot(roller, objs).generate(player, 2, 8)
        val rolls = mockingDetails(roller).invocations.filter { it.method.name == "roll" }
        assertEquals(2, rolls.size)
        assertTrue(rolls.all { it.arguments[0] === player && it.arguments[2] == 8 })
        val ground = mockingDetails(objs).invocations.filter { it.method.name.startsWith("add") }
        assertEquals(2, ground.size)
        assertTrue(ground.all { it.arguments[0] == "obj.coins" && it.arguments.contains(120) })
        assertEquals(4, player.vars[DoomRewards.LEVEL_VARP])
        assertTrue(player.invMap.isEmpty())
        assertThrows(IllegalArgumentException::class.java) { DoomTestLoot(roller, objs).generate(player, 0) }
    }
    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
