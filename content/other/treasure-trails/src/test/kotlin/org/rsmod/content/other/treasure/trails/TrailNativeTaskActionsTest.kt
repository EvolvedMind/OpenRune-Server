package org.rsmod.content.other.treasure.trails

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.Mockito.*
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.music.MusicRepository
import org.rsmod.api.player.events.skilling.LampRepairedEvent
import org.rsmod.api.player.events.skilling.ShadeCrematedEvent
import org.rsmod.api.player.events.skilling.SkillingActionCompleteEvent
import org.rsmod.api.player.events.skilling.SkillingActionContext
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.ContextualInteractions
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.interact.LocUInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.IfOverlayButtonT
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.script.onEvent
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.spells.runes.combo.ComboRuneRepository
import org.rsmod.api.spells.runes.compact.CompactRuneRepository
import org.rsmod.api.spells.runes.fake.FakeRuneRepository
import org.rsmod.api.spells.runes.staves.StaffSubstituteRepository
import org.rsmod.api.spells.runes.subs.RuneSubstituteRepository
import org.rsmod.api.spells.runes.unlimited.UnlimitedRuneRepository
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.firemaking.DorgeshLampScript
import org.rsmod.content.skills.firemaking.FiyrCremationScript
import org.rsmod.content.skills.magic.utility.BarrowsTabletScript
import org.rsmod.content.skills.magic.utility.DragonstoneEnchantmentScript
import org.rsmod.content.skills.smithing.AnvilSmithingScript
import org.rsmod.content.skills.smithing.ShayzienSmithingScript
import org.rsmod.content.skills.thieving.ArdougneChestScript
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
import org.rsmod.routefinder.collision.CollisionFlagMap

@ResourceLock("ServerCacheManager")
class TrailNativeTaskActionsTest {
    @Test fun `all four real dragonstone spell targets consume native rune requirements and complete assigned task`() {
        for ((input, output) in DragonstoneEnchantmentScript.recipes) {
            val f = Fixture(TrailSkillChallenges.enchantmentTask)
            f.player.inv[1] = InvObj(ServerCacheManager.getItem(input)!!)
            f.enchantRunes()
            f.enchant()
            assertEquals(output.asRSCM(), f.player.inv[1]!!.id)
            assertEquals(0, f.player.inv.count("obj.cosmicrune"))
            assertEquals(0, f.player.inv.count("obj.waterrune"))
            assertEquals(0, f.player.inv.count("obj.earthrune"))
            f.assertCompleted("stat.magic")
            f.sherlock()
        }
    }

    @Test fun `spell refusal preserves jewellery runes XP and task for wrong book low level or insufficient runes`() {
        for (failure in 0..2) {
            val f = Fixture(TrailSkillChallenges.enchantmentTask)
            f.player.inv[1] = InvObj("obj.dragonstone_ring"); f.enchantRunes()
            when (failure) { 0 -> VarPlayerIntMapSetter.set(f.player, "varbit.spellbook", 1); 1 -> f.player.statMap.setCurrentLevel("stat.magic", 67); else -> f.player.inv[2] = null }
            val before = f.player.inv.objs.toList()
            f.enchant(); assertEquals(before, f.player.inv.objs.toList())
            assertEquals(9, f.state().phase); assertTrue(f.products.isEmpty())
            assertEquals(0, f.player.statMap.getXP("stat.magic"))
        }
    }

    @Test fun `mud staff and real rune pouch supply enchantments without deleting unrelated inventory items`() {
        val f = Fixture(TrailSkillChallenges.enchantmentTask)
        f.player.inv[1] = InvObj("obj.dragonstone_ring")
        f.player.worn[3] = InvObj("obj.mud_battlestaff")
        f.player.inv[2] = InvObj("obj.bh_rune_pouch")
        val compact = repository(CompactRuneRepository())
        VarPlayerIntMapSetter.set(f.player, "varbit.rune_pouch_type_1", compact[ServerCacheManager.getItem("obj.cosmicrune".asRSCM())!!]!!)
        VarPlayerIntMapSetter.set(f.player, "varbit.rune_pouch_quantity_1", 5)
        f.enchant()
        assertEquals(4, f.player.vars["varbit.rune_pouch_quantity_1"])
        assertEquals("obj.bh_rune_pouch".asRSCM(), f.player.inv[2]!!.id)
        f.assertCompleted("stat.magic")
    }

    @Test fun `native spell target stale item and cooldown cannot consume or duplicate output`() {
        val f = Fixture(TrailSkillChallenges.enchantmentTask)
        f.player.inv[1] = InvObj("obj.dragonstone_ring"); f.enchantRunes()
        val stale = f.enchantEvent()
        f.player.inv[1] = InvObj("obj.abyssal_whip")
        f.bus.publish(stale)
        assertEquals(1, f.player.inv.count("obj.cosmicrune")); assertTrue(f.products.isEmpty())
        f.player.inv[1] = InvObj("obj.dragonstone_ring"); f.enchant()
        val before = f.player.inv.objs.toList(); f.bus.publish(stale)
        assertEquals(before, f.player.inv.objs.toList()); assertEquals(1, f.products.size)
    }

    @Test fun `native broken lamp repair consumes one orb repairs world object and advances once at Sherlock`() {
        val f = Fixture(TrailSkillChallenges.lampTask)
        val loc = f.loc("loc.dorgesh_lamp_stand_light_off", DorgeshLampScript.lamps.first())
        f.player.inv[1] = InvObj("obj.dorgesh_light_bulb")
        f.use(loc, 1)
        assertEquals(0, f.player.inv.count("obj.dorgesh_light_bulb")); f.assertCompleted("stat.firemaking")
        val repair = mockingDetails(f.locs).invocations.single { it.method.name.startsWith("add") }
        assertEquals("loc.dorgesh_lamp_stand_light_on", repair.arguments[1])
        assertEquals(600, repair.arguments[2])
        f.sherlock()
    }

    @Test fun `lamp repair interruption moved player and stolen orb do not award XP or task credit`() {
        for (failure in 0..2) {
            val f = Fixture(TrailSkillChallenges.lampTask)
            val loc = f.loc("loc.dorgesh_lamp_stand_light_off", DorgeshLampScript.lamps.first())
            f.player.inv[1] = InvObj("obj.dorgesh_light_bulb")
            val finish = f.begin { f.useAction(loc, 1) }
            when (failure) { 0 -> f.coroutine.cancel(); 1 -> f.player.coords = f.player.coords.translate(3, 0); else -> f.player.inv[1] = null }
            if (failure != 0) f.finish(finish)
            assertEquals(9, f.state().phase); assertTrue(f.products.isEmpty())
            assertEquals(0, f.player.statMap.getXP("stat.firemaking"))
        }
    }

    @Test fun `map alias castle chest commits four rewards before native teleport and clue completion`() {
        val f = Fixture(TrailSkillChallenges.chestTask)
        val loc = f.loc("loc.trapchest5", CoordGrid(2588, 3291, 1))
        f.op(loc, InteractionOp.Op2)
        assertEquals(1000, f.player.inv.count("obj.coins")); assertEquals(1, f.player.inv.count("obj.raw_shark"))
        assertEquals(1, f.player.inv.count("obj.adamantite_ore")); assertEquals(1, f.player.inv.count("obj.uncut_sapphire"))
        assertEquals(CoordGrid(2680, 3273, 0), f.player.coords)
        f.assertCompleted("stat.thieving"); f.sherlock()
    }

    @Test fun `full inventory and concurrent looted chest roll back every reward and task`() {
        for (failure in 0..1) {
            val f = Fixture(TrailSkillChallenges.chestTask)
            val loc = f.loc("loc.trapchest5", CoordGrid(2588, 3302, 1))
            if (failure == 0) for (i in 1..27) f.player.inv[i] = InvObj("obj.abyssal_whip")
            val before = f.player.inv.objs.toList()
            val finish = f.begin { f.opAction(loc, InteractionOp.Op2) }
            if (failure == 1) `when`(f.locs.findExact(loc.coords, ServerCacheManager.getObject(loc.id)!!)).thenReturn(null)
            f.finish(finish)
            assertEquals(before, f.player.inv.objs.toList()); assertEquals(9, f.state().phase)
            assertEquals(loc.coords, f.player.coords); assertTrue(f.products.isEmpty())
        }
    }

    @Test fun `native anvil category UI and committed tiers two through five qualify but tier one does not`() {
        for (tier in 1..5) {
            val f = Fixture(TrailSkillChallenges.shayzienTask)
            val type = ServerCacheManager.getObjects().values.first { it.category == "category.anvil".asRSCM() }
            val loc = f.bound(type.id, CoordGrid(3200, 3200, 0))
            for (slot in 1..4) f.player.inv[slot] = InvObj("obj.lovakite_bar"); f.player.inv[5] = InvObj("obj.hammer")
            val finish = f.begin { f.useAction(loc, 1) }
            assertTrue(f.coroutine.isAwaiting(ResumePauseButtonInput::class))
            f.coroutine.resumeWith(ResumePauseButtonInput("component.skillmulti:${('a'.code + tier - 1).toChar()}", 1))
            f.finish(finish)
            assertEquals(0, f.player.inv.count("obj.lovakite_bar"))
            assertEquals(1, f.player.inv.count("obj.shayzien_body_$tier"))
            assertEquals(if (tier == 1) 9 else 10, f.state().phase)
            if (tier > 1) f.sherlock()
        }
    }

    @Test fun `smithing rechecks materials after the native selection menu before consuming bars`() {
        val f = Fixture(TrailSkillChallenges.shayzienTask)
        val type = ServerCacheManager.getObjects().values.first { it.category == "category.anvil".asRSCM() }
        val loc = f.bound(type.id, CoordGrid(3200, 3200, 0))
        for (slot in 1..4) f.player.inv[slot] = InvObj("obj.lovakite_bar")
        f.player.inv[5] = InvObj("obj.hammer")
        val finish = f.begin { f.useAction(loc, 1) }
        f.player.inv[4] = null
        val before = f.player.inv.objs.toList()
        f.coroutine.resumeWith(ResumePauseButtonInput("component.skillmulti:b", 1)); f.finish(finish)
        assertEquals(before, f.player.inv.objs.toList()); assertEquals(9, f.state().phase)
        assertEquals(0, f.player.statMap.getXP("stat.smithing"))
    }

    @Test fun `native lectern creates a Barrows tablet with real Arceuus rune and essence requirements`() {
        val f = Fixture(TrailSkillChallenges.tabletTask)
        val loc = f.loc("loc.arceuus_lectern", CoordGrid(1679, 3765, 0))
        f.tabletRunes(); f.use(loc, 1)
        assertEquals(1, f.player.inv.count("obj.teletab_barrows"))
        for (item in listOf(BarrowsTabletScript.ESSENCE, "obj.lawrune", "obj.soulrune", "obj.bloodrune")) assertEquals(0, f.player.inv.count(item))
        f.assertCompleted("stat.magic"); f.sherlock()
    }

    @Test fun `lectern cancel wrong book insufficient runes and stale essence preserve input and completion`() {
        for (failure in 0..3) {
            val f = Fixture(TrailSkillChallenges.tabletTask)
            val loc = f.loc("loc.arceuus_lectern", CoordGrid(1679, 3765, 0)); f.tabletRunes()
            val before = f.player.inv.objs.toList()
            if (failure == 0) { val pending = f.begin { f.useAction(loc, 1) }; f.coroutine.cancel(); assertTrue(pending()!!.isFailure) } else if (failure == 1) { VarPlayerIntMapSetter.set(f.player, "varbit.spellbook", 0); f.use(loc, 1) } else if (failure == 2) { f.player.inv[4] = null; f.use(loc, 1) } else { val pending = f.begin { f.useAction(loc, 1) }; f.player.inv[1] = InvObj(BarrowsTabletScript.ESSENCE); f.finish(pending) }
            if (failure < 2) assertEquals(before, f.player.inv.objs.toList())
            assertEquals(0, f.player.inv.count("obj.teletab_barrows")); assertEquals(9, f.state().phase)
            assertTrue(f.products.isEmpty()); assertEquals(0, f.player.statMap.getXP("stat.magic"))
        }
    }

    @Test fun `native funeral pyre consumes Fiyr remains and valid logs before cremation completion`() {
        for (log in listOf("obj.magic_logs_pyre", "obj.redwood_logs_pyre")) {
            val f = Fixture(TrailSkillChallenges.cremationTask)
            val loc = f.loc("loc.temple_pyre", CoordGrid(3462, 3282, 0))
            f.player.inv[1] = InvObj("obj.shade_bones5"); f.player.inv[2] = InvObj(log); f.player.inv[3] = InvObj("obj.tinderbox")
            f.op(loc, InteractionOp.Op1)
            assertEquals(0, f.player.inv.count("obj.shade_bones5")); assertEquals(0, f.player.inv.count(log))
            assertTrue(f.player.statMap.getXP("stat.firemaking") > 0); f.assertCompleted("stat.prayer")
            assertTrue(f.player.inv.objs.filterNotNull().any { it.id == "obj.coins".asRSCM() || it.id in (FiyrCremationScript.silver + FiyrCremationScript.gold).map { s -> s.asRSCM() } })
            f.sherlock()
        }
    }

    @Test fun `pyre cancellation missing tinderbox low level and stale world object grant no XP or credit`() {
        for (failure in 0..3) {
            val f = Fixture(TrailSkillChallenges.cremationTask)
            val loc = f.loc("loc.temple_pyre", CoordGrid(3462, 3282, 0))
            f.player.inv[1] = InvObj("obj.shade_bones5"); f.player.inv[2] = InvObj("obj.magic_logs_pyre"); f.player.inv[3] = InvObj("obj.tinderbox")
            if (failure == 0) { f.begin { f.opAction(loc, InteractionOp.Op1) }; f.coroutine.cancel() } else if (failure == 1) { f.player.inv[3] = null; f.op(loc, InteractionOp.Op1) } else if (failure == 2) { f.player.statMap.setCurrentLevel("stat.firemaking", 79); f.op(loc, InteractionOp.Op1) } else { val pending = f.begin { f.opAction(loc, InteractionOp.Op1) }; `when`(f.locs.findExact(loc.coords, ServerCacheManager.getObject(loc.id)!!)).thenReturn(null); f.finish(pending) }
            assertEquals(1, f.player.inv.count("obj.shade_bones5")); assertEquals(1, f.player.inv.count("obj.magic_logs_pyre"))
            assertEquals(9, f.state().phase); assertTrue(f.products.isEmpty())
        }
    }
    private class Fixture(row: Int) {
        val bus = EventBus(); val scripts = ScriptContext(bus, CheatCommandMap(), EngineQueueCache())
        val random = DefaultGameRandom(42); val progress = TrailProgress(TrailCatalog(), random)
        val coroutine = GameCoroutine("native-clue-task-action"); val locs = mock(LocRepository::class.java)
        val products = mutableListOf<Any>()
        val spells = repository(MagicSpellRegistry())
        val runes = MagicRuneManager(repository(FakeRuneRepository()), repository(ComboRuneRepository()), repository(CompactRuneRepository()),
            repository(UnlimitedRuneRepository()), repository(StaffSubstituteRepository()), repository(RuneSubstituteRepository()), emptySet())
        val player = Player().apply {
            currentMapClock = 100; processedMapClock = 100; activeCoroutine = coroutine
            inv = Inventory(ServerCacheManager.getInventory("inv.inv".asRSCM())!!, arrayOfNulls(28))
            worn = Inventory(ServerCacheManager.getInventory("inv.worn".asRSCM())!!, arrayOfNulls(14))
            for (stat in listOf("stat.magic", "stat.smithing", "stat.mining", "stat.thieving", "stat.firemaking", "stat.prayer")) {
                statMap.setBaseLevel(stat, 99.toByte()); statMap.setCurrentLevel(stat, 99)
            }
        }
        val collision = CollisionFlagMap().apply { allocateIfAbsent(2680, 3273, 0) }
        val context = ProtectedAccessContextFactory.empty().copy(getEventBus = { bus }, getRandom = { random }, getCollision = { collision },
            getHitModifier = { NoopPlayerHitModifier }, getTeleportValidator = { PlayerTeleportValidator(emptySet()) }, getAreaChecker = { mock(AreaChecker::class.java) })
        val access = ProtectedAccess(player, coroutine, context)
        val itemOps = LocUInteractions::class.java.getDeclaredConstructor(EventBus::class.java).apply { isAccessible = true }.newInstance(bus)
        val locOps = LocInteractions(mock(BoundValidator::class.java), bus)
        init {
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { scripts.startup() }
            val factory = mock(ProtectedAccessContextFactory::class.java); `when`(factory.create()).thenReturn(context)
            with(DragonstoneEnchantmentScript(bus, ProtectedAccessLauncher(factory), spells, runes)) { scripts.startup() }
            with(BarrowsTabletScript(spells, runes)) { scripts.startup() }
            with(ArdougneChestScript(locs)) { scripts.startup() }
            with(DorgeshLampScript(locs)) { scripts.startup() }
            val xpMods = XpModifiers(emptySet())
            with(AnvilSmithingScript(xpMods, ShayzienSmithingScript(xpMods))) { scripts.startup() }
            with(FiyrCremationScript(locs, mock(WorldRepository::class.java))) { scripts.startup() }
            with(TrailSkillChallenges(progress, TrailTargets(progress), TrailRequirements(progress.catalog))) { scripts.startup() }
            scripts.onEvent<SkillingActionCompleteEvent> { products += context as SkillingActionContext.Product }
            scripts.onEvent<LampRepairedEvent> { products += this }
            scripts.onEvent<ShadeCrematedEvent> { products += this }
            val clue = progress.catalog.clues.getValue(row)
            player.inv[0] = InvObj(ServerCacheManager.getItem(progress.catalog.item(clue))!!, 1, TrailState(row, 3, 2, 9).encode())
        }
        fun state() = progress.state(player.inv[0]!!)!!
        fun enchantRunes() { player.inv[2] = InvObj("obj.cosmicrune"); player.inv[3] = InvObj("obj.waterrune", 15); player.inv[4] = InvObj("obj.earthrune", 15) }
        fun tabletRunes() {
            VarPlayerIntMapSetter.set(player, "varbit.spellbook", 3)
            player.inv[1] = InvObj(BarrowsTabletScript.ESSENCE); player.inv[2] = InvObj("obj.lawrune", 2)
            player.inv[3] = InvObj("obj.soulrune", 2); player.inv[4] = InvObj("obj.bloodrune")
        }
        fun enchantEvent() = IfOverlayButtonT(player, 0, null, 1, ServerCacheManager.getItem(player.inv[1]!!.id)!!,
            spells.getUtilitySpell(ServerCacheManager.getItem("obj.68_enchant_amulet_lvl5".asRSCM())!!).component.let { org.rsmod.game.ui.Component(it.packed) },
            org.rsmod.game.ui.Component("component.inventory:items".asRSCM()))
        fun enchant() { player.activeCoroutine = null; bus.publish(enchantEvent()) }
        fun bound(id: Int, coords: CoordGrid): BoundLocInfo {
            val type = ServerCacheManager.getObject(id)!!; val info = LocInfo(2, coords, LocEntity(id, 10, 0))
            `when`(locs.findExact(coords, type)).thenReturn(info)
            player.coords = coords
            return BoundLocInfo(info, type)
        }
        fun loc(symbol: String, coords: CoordGrid) = bound(symbol.asRSCM(), coords)
        suspend fun useAction(loc: BoundLocInfo, slot: Int) {
            player.activeCoroutine = coroutine
            itemOps.interactOp(access, loc, loc, ServerCacheManager.getObject(loc.id)!!, ServerCacheManager.getItem(player.inv[slot]!!.id)!!, player.inv, slot)
        }
        suspend fun opAction(loc: BoundLocInfo, op: InteractionOp) { player.activeCoroutine = coroutine; bus.publish(access, locOps.opTrigger(player, loc, op)!!) }
        fun use(loc: BoundLocInfo, slot: Int) = finish(begin { useAction(loc, slot) })
        fun op(loc: BoundLocInfo, op: InteractionOp) = finish(begin { opAction(loc, op) })
        fun begin(action: suspend () -> Unit): () -> Result<Unit>? {
            var outcome: Result<Unit>? = null
            action.startCoroutine(object : Continuation<Unit> { override val context = EmptyCoroutineContext; override fun resumeWith(result: Result<Unit>) { outcome = result } })
            return { outcome }
        }
        fun finish(outcome: () -> Result<Unit>?) {
            repeat(10) { if (outcome() == null) { player.currentMapClock++; player.processedMapClock = player.currentMapClock; coroutine.advance() } }
            checkNotNull(outcome()).getOrThrow()
        }
        fun assertCompleted(stat: String) {
            assertEquals(10, state().phase); assertEquals(2, state().completed)
            assertTrue(player.statMap.getXP(stat) > 0); assertEquals(1, products.size)
        }
        fun sherlock() {
            val active = state(); val clue = progress.catalog.clues.getValue(active.row)
            val contextual = ContextualInteractions()
            val script = TrailInteractionScript(progress, TrailTargets(progress), TrailRequirements(progress.catalog), mock(TrailPuzzleScript::class.java), mock(TrailGuards::class.java), contextual, mock(MusicRepository::class.java))
            with(script) { scripts.startup() }
            val fields = progress.catalog.fields(clue.targets.single())
            val npc = Npc(ServerCacheManager.getNpc(fields.int("npc"))!!, CoordGrid(fields.int("coord")))
            val event = contextual.npc(player, npc, InteractionOp.Op1)!!
            finish(begin { bus.publish(access, event); Unit })
            assertEquals(1, player.inv.objs.filterNotNull().filter { it.id == clue.tier.casket }.sumOf { it.count })
            val before = player.inv.objs.toList(); finish(begin { bus.publish(access, event); Unit })
            assertEquals(before, player.inv.objs.toList())
            with(script) { scripts.shutdown() }
        }
    }
    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() }
        private fun <T : Any> repository(value: T): T {
            value.javaClass.declaredMethods.single { it.name.startsWith("init") && it.parameterCount == 0 }.apply { isAccessible = true }.invoke(value)
            return value
        }
    }
}
