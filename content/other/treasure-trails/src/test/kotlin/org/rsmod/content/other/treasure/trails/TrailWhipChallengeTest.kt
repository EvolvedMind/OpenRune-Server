package org.rsmod.content.other.treasure.trails

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.util.Wearpos
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.Mockito
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.events.interact.HeldEquipEvents
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext

@ResourceLock("ServerCacheManager")
class TrailWhipChallengeTest {
    @Test fun `accepted whip variants require assignment and actual equipment in the tower near demons`() {
        for (weapon in TrailWhipChallenge.weapons) {
            val f = Fixture()
            f.player.worn[3] = InvObj(checkNotNull(ServerCacheManager.getItem(weapon)))
            f.emit(weapon)
            assertEquals(0, f.state().phase)
            f.progress.phase(f.player, 0, f.player.inv[0]!!, 9)
            f.emit(weapon)
            assertEquals(9, f.state().phase)
            Mockito.`when`(f.areas.inArea("area.slayer_tower", f.player.coords)).thenReturn(true)
            f.player.worn[3] = null
            f.emit(weapon)
            assertEquals(9, f.state().phase)
            f.player.worn[3] = InvObj(checkNotNull(ServerCacheManager.getItem(weapon)))
            f.emit(weapon)
            assertEquals(10, f.state().phase)
            assertEquals(2, f.state().completed)
            val completed = f.player.inv[0]
            f.emit(weapon)
            assertSame(completed, f.player.inv[0])
        }
    }

    @Test fun `wrong demon floor or distance does not meet the proximity requirement`() {
        val f = Fixture()
        assertTrue(TrailWhipChallenge.nearbyDemon(f.player, f.demon))
        f.demon.coords = f.player.coords.translateZ(11)
        assertFalse(TrailWhipChallenge.nearbyDemon(f.player, f.demon))
        f.demon.coords = CoordGrid(f.player.coords.x, f.player.coords.z, 0)
        assertFalse(TrailWhipChallenge.nearbyDemon(f.player, f.demon))
        assertFalse(TrailWhipChallenge.nearbyDemon(f.player, Npc("npc.kourend_abyssal", f.player.coords)))
    }
    private class Fixture {
        val events = EventBus()
        val progress = TrailProgress(TrailCatalog(), DefaultGameRandom(42))
        val areas = Mockito.mock(AreaChecker::class.java)
        val npcs = Mockito.mock(NpcRepository::class.java)
        val player = Player().apply {
            inv = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())), arrayOfNulls(28))
            worn = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.worn".asRSCM())), arrayOfNulls(14))
            coords = CoordGrid(3425, 3560, 2)
            statMap.setCurrentLevel("stat.attack", 99)
        }
        val demon = Npc("npc.slayer_abyssal", player.coords)
        init {
            Mockito.`when`(npcs.findAll(ZoneKey.from(player.coords), 2)).thenReturn(sequenceOf(demon))
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { scripts.startup() }
            with(TrailWhipChallenge(progress, TrailTargets(progress), TrailRequirements(progress.catalog), areas, npcs)) { scripts.startup() }
            player.inv[0] = InvObj("obj.trail_clue_master", 1, TrailState(TrailWhipChallenge.row, 6, 2).encode())
        }
        fun state() = progress.state(player.inv[0]!!)!!
        fun emit(id: Int) { events.publish(HeldEquipEvents.WearposChange(player, Wearpos.RightHand, checkNotNull(ServerCacheManager.getItem(id)))) }
    }
    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
