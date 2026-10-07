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
import org.rsmod.api.music.MusicRepository
import org.rsmod.api.player.events.PlayerTimerEvent
import org.rsmod.api.player.events.skilling.FarmingSeedPlantedEvent
import org.rsmod.api.player.interact.ContextualInteractions
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.interact.LocUInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.script.onEvent
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.farming.*
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@ResourceLock("ServerCacheManager")
class TrailNativeFarmingTest {
    @Test fun `native multiloc rake and planting commit seeds patch XP and the assigned clue`() {
        val f = Fixture()
        f.rake()
        assertEquals(3, f.player.inv.count("obj.weeds"))
        assertTrue(f.patch().cleared)
        assertEquals(3, f.player.vars[f.transmit])
        f.use(2)
        assertEquals(0, f.player.inv.count("obj.watermelon_seed"))
        assertEquals(7, f.patch().cropIndex)
        assertEquals(52, f.player.vars[f.transmit])
        assertTrue(f.player.statMap.getXP("stat.farming") >= 60)
        assertEquals(10, f.clue().phase)
        assertEquals(2, f.clue().completed)
        assertEquals(f.loc.coords, f.products.single().coords)
        f.player.inv[2] = InvObj("obj.watermelon_seed", 3)
        f.use(2)
        assertEquals(3, f.player.inv.count("obj.watermelon_seed"))
        assertEquals(1, f.products.size)
    }

    @Test fun `Sherlock consumes a completed native planting task or awards the final casket once`() {
        for (last in listOf(false, true)) {
            val f = Fixture()
            if (last) f.setClue(total = 3)
            f.rake(); f.use(2)
            val contextual = ContextualInteractions()
            val script = TrailInteractionScript(f.progress, TrailTargets(f.progress), TrailRequirements(f.progress.catalog),
                mock(TrailPuzzleScript::class.java), mock(TrailGuards::class.java), contextual, mock(MusicRepository::class.java))
            with(script) { f.scripts.startup() }
            val fields = f.progress.catalog.fields(f.progress.catalog.clues.getValue(TrailSkillChallenges.watermelonTask).targets.single())
            val npc = Npc(ServerCacheManager.getNpc(fields.int("npc"))!!, CoordGrid(fields.int("coord")))
            val event = contextual.npc(f.player, npc, InteractionOp.Op1)!!
            f.run { f.bus.publish(f.access, event); Unit }
            if (last) assertEquals(1, f.player.inv.objs.filterNotNull().filter { it.id == TrailTier.ELITE.casket }.sumOf { it.count })
            else assertEquals(3, f.clue().completed)
            val before = f.player.inv.objs.toList()
            f.run { f.bus.publish(f.access, event); Unit }
            assertEquals(before, f.player.inv.objs.toList())
            with(script) { f.scripts.shutdown() }
        }
    }

    @Test fun `unassigned or different clues and planting other seeds give no watermelon credit`() {
        val f = Fixture()
        f.setClue(phase = 0)
        f.rake(); f.use(2)
        assertEquals(0, f.clue().phase)
        f.store(PatchState(weeds = 3))
        f.setClue(row = "dbrow.cluehelper_skillchallenge_elite_20")
        f.player.inv[2] = InvObj("obj.watermelon_seed", 3)
        f.use(2)
        assertEquals(9, f.clue().phase)
        f.store(PatchState(weeds = 3))
        f.setClue()
        f.player.inv[2] = InvObj("obj.potato_seed", 3)
        f.use(2)
        assertEquals(9, f.clue().phase)
        assertEquals(1, f.patch().cropIndex)
    }

    @Test fun `uncleared patches wrong patch kind missing tools low levels and too few seeds reject planting`() {
        val f = Fixture()
        f.use(2)
        f.store(PatchState(weeds = 3))
        f.use(2, "loc.farming_flower_patch_1")
        f.player.inv[1] = null
        f.use(2)
        f.player.inv[1] = InvObj("obj.dibber")
        f.player.statMap.setCurrentLevel("stat.farming", 46)
        f.use(2)
        f.player.statMap.setCurrentLevel("stat.farming", 99)
        f.player.inv[2] = InvObj("obj.watermelon_seed", 2)
        f.use(2)
        assertEquals(0, f.patch().cropIndex)
        assertEquals(0, f.player.statMap.getXP("stat.farming"))
        assertEquals(9, f.clue().phase)
        assertTrue(f.products.isEmpty())
    }

    @Test fun `native plant interruption never consumes seeds or completes the clue`() {
        val f = Fixture()
        f.store(PatchState(weeds = 3))
        val finish = f.begin { f.useAction(2) }
        assertNull(finish())
        f.coroutine.cancel()
        assertTrue(finish()!!.isFailure)
        assertEquals(3, f.player.inv.count("obj.watermelon_seed"))
        assertEquals(0, f.patch().cropIndex)
        assertEquals(9, f.clue().phase)
        assertTrue(f.products.isEmpty())
    }

    @Test fun `state movement tool or seed changes during the native delay prevent stale planting`() {
        for (change in listOf<(Fixture) -> Unit>(
            { it.store(PatchState()) },
            { it.player.coords = CoordGrid(2811, 3464, 0) },
            { it.player.inv[1] = null },
            { it.player.inv[2] = null },
            { it.player.statMap.setCurrentLevel("stat.farming", 46) })) {
            val f = Fixture()
            f.store(PatchState(weeds = 3))
            val finish = f.begin { f.useAction(2) }
            change(f); f.finish(finish)
            assertEquals(0, f.patch().cropIndex)
            assertEquals(0, f.player.statMap.getXP("stat.farming"))
            assertEquals(9, f.clue().phase)
            assertTrue(f.products.isEmpty())
        }
    }

    @Test fun `full inventory compost and watering swaps preserve inputs on failure and commit replacements together`() {
        val f = Fixture()
        f.store(PatchState(weeds = 3))
        for (i in 1..27) f.player.inv[i] = InvObj("obj.abyssal_whip")
        f.player.inv[2] = InvObj("obj.bucket_ultracompost")
        f.use(2)
        assertEquals(Compost.ULTRA, f.patch().compost)
        assertEquals("obj.bucket_empty".asRSCM(), f.player.inv[2]!!.id)
        f.player.inv[2] = InvObj("obj.dibber")
        f.player.inv[3] = InvObj("obj.watermelon_seed", 3)
        f.use(3)
        f.player.inv[3] = InvObj("obj.watering_can_8")
        f.use(3)
        assertTrue(f.patch().watered)
        assertEquals("obj.watering_can_7".asRSCM(), f.player.inv[3]!!.id)
        val before = f.player.inv.objs.toList()
        f.use(3)
        assertEquals(before, f.player.inv.objs.toList())
        f.store(f.patch().copy(watered = false))
        val finish = f.begin { f.useAction(3) }
        f.player.inv[3] = null
        f.finish(finish)
        assertFalse(f.patch().watered)
        assertEquals(0, f.player.inv.count("obj.watering_can_6"))
    }

    @Test fun `saved patches grow offline and transmit the right farm without repeated growth on clock reversal`() {
        val f = Fixture()
        f.store(PatchState(weeds = 3))
        f.player.inv[4] = InvObj("obj.bucket_ultracompost")
        f.use(4); f.use(2)
        val saved = f.player.vars[f.varp]
        f.store(PatchState.unpack(saved))
        f.clock.now += 80
        f.timer()
        assertEquals(8, f.patch().stage)
        assertEquals(6, f.patch().produce)
        assertEquals(60, f.player.vars[f.transmit])
        assertEquals(Health.HEALTHY, f.patch().health)
        val completed = f.patch()
        f.clock.now -= 10; f.timer()
        assertEquals(completed, f.patch())
        f.player.coords = CoordGrid(2811, 3464, 0)
        f.timer()
        assertEquals(0, f.player.vars[f.transmit])
        f.player.coords = f.loc.coords; f.timer()
        assertEquals(60, f.player.vars[f.transmit])
    }

    @Test fun `harvest output capacity and stale clear leave patch and items consistent`() {
        val f = Fixture()
        val grown = PatchState(weeds = 3, cropIndex = 7, stage = 8, produce = 3)
        f.store(grown)
        for (i in 1..27) f.player.inv[i] = InvObj("obj.abyssal_whip")
        f.player.inv[3] = InvObj("obj.spade")
        f.rake()
        assertEquals(grown, f.patch())
        assertEquals(0, f.player.statMap.getXP("stat.farming"))
        f.player.inv[4] = null; f.rake()
        assertEquals(2, f.patch().produce)
        assertEquals(1, f.player.inv.count("obj.watermelon"))
        val before = f.patch()
        val finish = f.begin { f.useAction(3) }
        f.player.inv[3] = null; f.finish(finish)
        assertEquals(before, f.patch())
    }

    private class TestClock : FarmingClock() { var now = 1000000; override fun minute() = now }

    @Test fun `native dead-crop clear consumes no produce and inspect never harvests`() {
        val f = Fixture()
        f.store(PatchState(weeds = 3, cropIndex = 7, stage = 1, health = Health.DEAD))
        f.rake()
        assertEquals(PatchState(), f.patch())
        f.store(PatchState(weeds = 3, cropIndex = 7, stage = 8, produce = 3))
        val before = f.player.inv.objs.toList()
        f.run { f.opAction(InteractionOp.Op2) }
        assertEquals(3, f.patch().produce)
        assertEquals(before, f.player.inv.objs.toList())
        assertEquals(0, f.player.statMap.getXP("stat.farming"))
        assertEquals(9, f.clue().phase)
    }
    private class Fixture {
        val bus = EventBus()
        val scripts = ScriptContext(bus, CheatCommandMap(), EngineQueueCache())
        val progress = TrailProgress(TrailCatalog(), DefaultGameRandom(42))
        val coroutine = GameCoroutine("native-farming-test")
        val clock = TestClock()
        val products = mutableListOf<FarmingSeedPlantedEvent>()
        val varp = "varp.farming_patch_00"
        val type = ServerCacheManager.getObject("loc.farming_veg_patch_1".asRSCM())!!
        val transmit = dev.openrune.rscm.RSCM.getReverseMapping(dev.openrune.rscm.RSCMType.VARBIT, type.multiVarBit)
        val loc = BoundLocInfo(LocInfo(2, CoordGrid(3056, 3309, 0), LocEntity(type.id, 10, 0)), type)
        val player = Player().apply {
            currentMapClock = 100; processedMapClock = 100; activeCoroutine = coroutine; coords = loc.coords
            inv = Inventory(ServerCacheManager.getInventory("inv.inv".asRSCM())!!, arrayOfNulls(28))
            worn = Inventory(ServerCacheManager.getInventory("inv.worn".asRSCM())!!, arrayOfNulls(14))
            statMap.setBaseLevel("stat.farming", 99.toByte()); statMap.setCurrentLevel("stat.farming", 99)
            inv[1] = InvObj("obj.dibber"); inv[2] = InvObj("obj.watermelon_seed", 3)
            inv[3] = InvObj("obj.spade"); inv[4] = InvObj("obj.rake")
        }
        val access = ProtectedAccess(player, coroutine, ProtectedAccessContextFactory.empty().copy(getEventBus = { bus }, getRandom = { DefaultGameRandom(42) }))
        val itemOps = LocUInteractions::class.java.getDeclaredConstructor(EventBus::class.java).apply { isAccessible = true }.newInstance(bus)
        val locOps = LocInteractions(mock(BoundValidator::class.java), bus)
        init {
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { scripts.startup() }
            with(FarmingScript(XpModifiers(emptySet()), DefaultGameRandom(42), clock)) { scripts.startup() }
            scripts.onEvent<FarmingSeedPlantedEvent> {
                assertEquals(0, player.inv.count("obj.watermelon_seed").takeIf { seed == "obj.watermelon_seed".asRSCM() } ?: 0)
                assertTrue(player.statMap.getXP("stat.farming") > 0)
                assertTrue(PatchState.unpack(player.vars[varp]).cropIndex > 0)
                products += this
            }
            with(TrailSkillChallenges(progress, TrailTargets(progress), TrailRequirements(progress.catalog))) { scripts.startup() }
            VarPlayerIntMapSetter.set(player, "varp.farming_clock", clock.now)
            setClue()
        }
        fun setClue(row: String = "dbrow.cluehelper_skillchallenge_elite_21", phase: Int = 9, total: Int = 6) {
            val clue = progress.catalog.clues.getValue(row.asRSCM())
            player.inv[0] = InvObj(ServerCacheManager.getItem(progress.catalog.item(clue))!!, 1, TrailState(clue.row, total, 2, phase).encode())
        }
        fun clue() = progress.state(player.inv[0]!!)!!
        fun patch() = PatchState.unpack(player.vars[varp])
        fun store(state: PatchState) = VarPlayerIntMapSetter.set(player, varp, state.pack())
        fun timer() { bus.publish(PlayerTimerEvent.Soft(player, "timer.farming_transmit".asRSCM())) }
        suspend fun useAction(slot: Int, symbol: String = "loc.farming_veg_patch_1") {
            val type = ServerCacheManager.getObject(symbol.asRSCM())!!
            val target = BoundLocInfo(LocInfo(2, loc.coords, LocEntity(type.id, 10, 0)), type)
            val obj = ServerCacheManager.getItem(player.inv[slot]!!.id)!!
            itemOps.interactOp(access, target, target, type, obj, player.inv, slot)
        }
        suspend fun opAction(op: InteractionOp) {
            val event = checkNotNull(locOps.opTrigger(player, loc, op))
            bus.publish(access, event)
        }
        fun use(slot: Int, symbol: String = "loc.farming_veg_patch_1") = run { useAction(slot, symbol) }
        fun rake() = run { opAction(InteractionOp.Op1) }
        fun begin(block: suspend () -> Unit): () -> Result<Unit>? {
            var result: Result<Unit>? = null
            block.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(value: Result<Unit>) { result = value }
            })
            return { result }
        }
        fun finish(outcome: () -> Result<Unit>?) {
            repeat(40) { if (outcome() == null) { player.currentMapClock++; player.processedMapClock = player.currentMapClock; coroutine.advance() } }
            checkNotNull(outcome()).getOrThrow()
        }
        fun run(block: suspend () -> Unit) { finish(begin(block)) }
    }
    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
