package org.rsmod.content.other.treasure.trails

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import kotlin.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.Mockito.mock
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.events.skilling.RunesCraftedEvent
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.script.onEvent
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.table.runecrafting.RunecraftingAltarsRow
import org.rsmod.content.skills.runecrafting.altar.AltarEvents
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@ResourceLock("ServerCacheManager")
class TrailNativeRunecraftingTest {
    @Test fun `native altar output completes nature multiple-cosmic and both blood altar tasks`() {
        for ((row, altar) in listOf(
            "dbrow.cluehelper_skillchallenge_elite_2" to "loc.nature_altar",
            "dbrow.cluehelper_skillchallenge_elite_20" to "loc.cosmic_altar",
            "dbrow.cluehelper_skillchallenge_master_14" to "loc.blood_altar",
            "dbrow.cluehelper_skillchallenge_master_14" to "loc.archeus_altar_blood")) {
            val f = Fixture(row)
            val definition = f.addEssence(altar)
            f.craft(definition)
            assertEquals(10, f.state().phase)
            assertEquals(2, f.state().completed)
            assertEquals(0, f.player.inv.count(definition.rune.input.first().internalName))
            assertTrue(f.player.inv.count(definition.rune.output.internalName) >= 2)
            assertEquals(definition.rune.output.internalName, f.products.single().rune)
            assertEquals(altar, f.products.single().altar)
        }
    }

    @Test fun `multiple cosmics require native base multiplier rather than boosted visible level`() {
        val f = Fixture("dbrow.cluehelper_skillchallenge_elite_20")
        f.player.statMap.setBaseLevel("stat.runecrafting", 58.toByte())
        f.player.statMap.setCurrentLevel("stat.runecrafting", 99)
        f.craft(f.addEssence("loc.cosmic_altar"))
        assertEquals(1, f.products.single().baseMultiplier)
        assertEquals(9, f.state().phase)
        f.player.statMap.setBaseLevel("stat.runecrafting", 59.toByte())
        f.craft(f.addEssence("loc.cosmic_altar"))
        assertEquals(2, f.products.last().baseMultiplier)
        assertEquals(10, f.state().phase)
    }

    @Test fun `unassigned and wrong altar productions retain the current clue`() {
        val f = Fixture("dbrow.cluehelper_skillchallenge_elite_2", phase = 0)
        f.craft(f.addEssence("loc.nature_altar"))
        assertEquals(0, f.state().phase)
        assertTrue(f.progress.phase(f.player, 0, f.player.inv[0]!!, 9))
        f.craft(f.addEssence("loc.cosmic_altar"))
        assertEquals(9, f.state().phase)
        f.craft(f.addEssence("loc.nature_altar"))
        assertEquals(10, f.state().phase)
        f.player.inv[0] = f.player.inv[0]!!.copy()
        assertEquals(10, f.state().phase)
    }

    @Test fun `cancelled native altar delay consumes no essence and gives no XP or clue credit`() {
        val f = Fixture("dbrow.cluehelper_skillchallenge_elite_2")
        val altar = f.addEssence("loc.nature_altar")
        var outcome: Result<Boolean>? = null
        suspend { f.bus.publish(f.access, f.event(altar)) }.startCoroutine(object : Continuation<Boolean> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Boolean>) { outcome = result }
        })
        assertNull(outcome)
        assertTrue(f.coroutine.isSuspended)
        f.coroutine.cancel()
        assertTrue(outcome!!.isFailure)
        assertEquals(2, f.player.inv.count(altar.rune.input.first().internalName))
        assertEquals(0, f.player.inv.count(altar.rune.output.internalName))
        assertEquals(0, f.player.statMap.getXP("stat.runecrafting"))
        assertEquals(9, f.state().phase)
        assertTrue(f.products.isEmpty())
    }

    private class Fixture(row: String, phase: Int = 9) {
        val bus = EventBus()
        val scripts = ScriptContext(bus, CheatCommandMap(), EngineQueueCache())
        val random = DefaultGameRandom(42)
        val progress = TrailProgress(TrailCatalog(), random)
        val coroutine = GameCoroutine("native-altar-test")
        val products = mutableListOf<RunesCraftedEvent>()
        val player = Player().apply {
            currentMapClock = 100; processedMapClock = 100; activeCoroutine = coroutine
            inv = Inventory(ServerCacheManager.getInventory("inv.inv".asRSCM())!!, arrayOfNulls(28))
            worn = Inventory(ServerCacheManager.getInventory("inv.worn".asRSCM())!!, arrayOfNulls(14))
            statMap.setBaseLevel("stat.runecrafting", 99.toByte())
            statMap.setCurrentLevel("stat.runecrafting", 99)
        }
        val access = ProtectedAccess(player, coroutine, ProtectedAccessContextFactory.empty().copy(getEventBus = { bus }, getRandom = { random }))
        init {
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { scripts.startup() }
            with(AltarEvents(XpModifiers(emptySet()))) { scripts.startup() }
            scripts.onEvent<RunesCraftedEvent> {
                assertTrue(player.inv.count(rune) > 0)
                assertTrue(player.statMap.getXP("stat.runecrafting") > 0)
                products += this
            }
            with(TrailSkillChallenges(progress, TrailTargets(progress), TrailRequirements(progress.catalog))) { scripts.startup() }
            val clue = progress.catalog.clues.getValue(row.asRSCM())
            player.inv[0] = InvObj(ServerCacheManager.getItem(progress.catalog.item(clue))!!, 1, TrailState(clue.row, 6, 2, phase).encode())
        }
        fun state() = checkNotNull(progress.state(player.inv[0]!!))
        fun addEssence(symbol: String): RunecraftingAltarsRow {
            val altar = RunecraftingAltarsRow.all().single { it.altarObject.internalName == symbol }
            assertTrue(player.invAdd(player.inv, altar.rune.input.first().internalName, 2).success)
            return altar
        }
        fun event(altar: RunecraftingAltarsRow): LocEvents.Op1 {
            // The module handler uses the real cache altar type; network route geometry is separate.
            val loc = mock(BoundLocInfo::class.java)
            return LocEvents.Op1(loc, loc, altar.altarObject)
        }
        fun craft(altar: RunecraftingAltarsRow) {
            var outcome: Result<Boolean>? = null
            suspend { bus.publish(access, event(altar)) }.startCoroutine(object : Continuation<Boolean> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Boolean>) { outcome = result }
            })
            repeat(5) {
                if (outcome == null) {
                    player.currentMapClock++; player.processedMapClock = player.currentMapClock
                    coroutine.advance()
                }
            }
            assertTrue(checkNotNull(outcome).getOrThrow())
        }
    }
    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
