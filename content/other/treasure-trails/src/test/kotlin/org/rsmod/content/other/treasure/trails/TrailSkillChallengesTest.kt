package org.rsmod.content.other.treasure.trails

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.util.Wearpos
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.events.interact.HeldEquipEvents
import org.rsmod.api.player.events.skilling.LogBurnedEvent
import org.rsmod.api.player.events.skilling.RunesCraftedEvent
import org.rsmod.api.player.events.skilling.SkillingActionCompleteEvent
import org.rsmod.api.player.events.skilling.SkillingActionContext
import org.rsmod.api.player.events.skilling.SkillingProductSource
import org.rsmod.api.player.worn.HeldEquipOp
import org.rsmod.api.player.worn.HeldEquipResult
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@ResourceLock("ServerCacheManager")
class TrailSkillChallengesTest {
    @Test fun `Sherlock assigns an elite task without advancing or rerolling an assigned clue`() {
        val f = Fixture()
        f.clue(TrailCatalog.sherlockIntro, 0)
        val initial = f.player.inv[0]!!
        assertTrue(f.progress.assignSherlock(f.player, 0, initial))
        val assigned = f.state()
        assertEquals("skillchallenge", f.progress.catalog.clues.getValue(assigned.row).kind)
        assertEquals(9, assigned.phase)
        assertEquals(2, assigned.completed)
        assertEquals(6, assigned.total)
        assertFalse(f.progress.assignSherlock(f.player, 0, initial))
        assertFalse(f.progress.assignSherlock(f.player, 0, f.player.inv[0]!!))
        f.player.inv[0] = f.player.inv[0]!!.copy()
        assertEquals(assigned, f.state())
    }

    @Test fun `crafting tasks require assignment and the correct successful primary product`() {
        for ((row, expected) in TrailSkillChallenges.products.filterValues { it.first == "stat.crafting" }) {
            val f = Fixture()
            f.clue(row, 0)
            f.product(expected.second)
            assertEquals(0, f.state().phase)
            assertTrue(f.progress.phase(f.player, 0, f.player.inv[0]!!, 9))
            f.product("obj.abyssal_whip")
            f.product(expected.second, skill = "stat.smithing")
            f.product(expected.second, count = 0)
            f.product(expected.second, bonus = true)
            assertEquals(9, f.state().phase)
            f.product(expected.second)
            assertEquals(10, f.state().phase)
            assertEquals(2, f.state().completed)
            f.product(expected.second)
            assertEquals(10, f.state().phase)
        }
    }

    @Test fun `a correct product below the required skill level does not finish the task`() {
        val f = Fixture()
        f.clue("dbrow.cluehelper_skillchallenge_master_6".asRSCM(), 9)
        f.player.statMap.setCurrentLevel("stat.crafting", 1)
        f.product("obj.unstrung_dragonstone_amulet")
        assertEquals(9, f.state().phase)
    }

    @Test fun `master gathering requires every outfit piece worn at the time of production`() {
        for ((row, outfit) in TrailSkillOutfits.sets) {
            val expected = TrailSkillChallenges.products.getValue(row)
            for (missingSlot in outfit.keys) {
                val f = Fixture()
                f.clue(row, 9)
                for ((slot, ids) in outfit) f.player.worn[slot] = InvObj(checkNotNull(ServerCacheManager.getItem(ids.first())))
                f.player.inv[1] = f.player.worn[missingSlot]
                f.player.worn[missingSlot] = null
                f.product(expected.second, expected.first)
                assertEquals(9, f.state().phase, "Missing worn slot $missingSlot for row $row")
                f.player.worn[missingSlot] = f.player.inv[1]
                f.player.inv[1] = null
                f.product(expected.second, expected.first, bonus = true)
                assertEquals(9, f.state().phase)
                f.product(expected.second, expected.first)
                assertEquals(10, f.state().phase)
                assertEquals(2, f.state().completed)
                for (slot in outfit.keys) f.player.worn[slot] = null
                assertEquals(10, f.state().phase)
            }
        }
    }

    @Test fun `mixed outfit variants are accepted but lesser Varrock armour is not`() {
        for ((row, outfit) in TrailSkillOutfits.sets) {
            for ((changedSlot, alternatives) in outfit) for (alternative in alternatives) {
                val f = Fixture()
                f.clue(row, 9)
                for ((slot, ids) in outfit) f.player.worn[slot] = InvObj(checkNotNull(ServerCacheManager.getItem(ids.first())))
                f.player.worn[changedSlot] = InvObj(checkNotNull(ServerCacheManager.getItem(alternative)))
                assertTrue(TrailSkillOutfits.matches(f.player, row))
                val expected = TrailSkillChallenges.products.getValue(row)
                f.product(expected.second, expected.first)
                assertEquals(10, f.state().phase)
            }
        }
        val f = Fixture()
        val row = "dbrow.cluehelper_skillchallenge_master_11".asRSCM()
        for ((slot, ids) in TrailSkillOutfits.sets.getValue(row)) f.player.worn[slot] = InvObj(checkNotNull(ServerCacheManager.getItem(ids.first())))
        f.player.worn[4] = InvObj("obj.varrock_armour_hard")
        assertFalse(TrailSkillOutfits.matches(f.player, row))
    }

    @Test fun `Herblore tasks accept brewed doses only after assignment`() {
        for ((row, expected) in TrailSkillChallenges.products.filterValues { it.first == "stat.herblore" }) {
            for (item in TrailSkillChallenges.alternatives[row] ?: setOf(expected.second)) {
                val f = Fixture()
                f.clue(row, 0)
                f.product(item, "stat.herblore")
                assertEquals(0, f.state().phase)
                assertTrue(f.progress.phase(f.player, 0, f.player.inv[0]!!, 9))
                f.product("obj.antivenom+4", "stat.herblore")
                f.product(item, "stat.crafting")
                f.product(item, "stat.herblore", bonus = true)
                assertEquals(9, f.state().phase)
                f.product(item, "stat.herblore")
                assertEquals(10, f.state().phase)
                assertEquals(2, f.state().completed)
            }
        }
    }

    @Test fun `equipping the scimitar through the inventory transaction completes only an assigned task`() {
        val f = Fixture()
        f.player.statMap.setBaseLevel("stat.attack", 99.toByte())
        f.player.statMap.setCurrentLevel("stat.attack", 99)
        f.clue("dbrow.cluehelper_skillchallenge_elite_0".asRSCM(), 0)
        f.player.inv[1] = InvObj("obj.dragon_scimitar")
        assertTrue(HeldEquipOp(f.events).equip(f.player, 1, f.player.inv) is HeldEquipResult.Success)
        assertEquals(0, f.state().phase)
        assertTrue(f.progress.phase(f.player, 0, f.player.inv[0]!!, 9))
        val scimitar = f.player.worn[Wearpos.RightHand.slot]!!
        f.player.worn[Wearpos.RightHand.slot] = null
        f.events.publish(HeldEquipEvents.WearposChange(f.player, Wearpos.RightHand, checkNotNull(ServerCacheManager.getItem(scimitar.id))))
        assertEquals(9, f.state().phase)
        f.player.inv[1] = InvObj("obj.abyssal_whip")
        assertTrue(HeldEquipOp(f.events).equip(f.player, 1, f.player.inv) is HeldEquipResult.Success)
        assertEquals(9, f.state().phase)
        f.player.inv[1] = scimitar
        assertTrue(HeldEquipOp(f.events).equip(f.player, 1, f.player.inv) is HeldEquipResult.Success)
        assertEquals(10, f.state().phase)
        assertEquals(2, f.state().completed)
        assertEquals("obj.dragon_scimitar".asRSCM(), f.player.worn[Wearpos.RightHand.slot]!!.id)
    }

    @Test fun `burning challenges require the assigned log and successful burn event`() {
        for ((row, log) in TrailSkillChallenges.burning) {
            val f = Fixture()
            f.clue(row, 0)
            f.events.publish(LogBurnedEvent(f.player, log))
            assertEquals(0, f.state().phase)
            assertTrue(f.progress.phase(f.player, 0, f.player.inv[0]!!, 9))
            f.events.publish(LogBurnedEvent(f.player, "obj.logs"))
            f.product(log, "stat.woodcutting")
            assertEquals(9, f.state().phase)
            f.player.statMap.setCurrentLevel("stat.firemaking", 1)
            f.events.publish(LogBurnedEvent(f.player, log))
            assertEquals(9, f.state().phase)
            f.player.statMap.setCurrentLevel("stat.firemaking", 99)
            f.events.publish(LogBurnedEvent(f.player, log))
            assertEquals(10, f.state().phase)
            assertEquals(2, f.state().completed)
            f.events.publish(LogBurnedEvent(f.player, log))
            assertEquals(10, f.state().phase)
        }
    }

    @Test fun `altar challenges require assignment and genuine multiple cosmic runes per essence`() {
        for ((row, rune, multiplier) in listOf(
            Triple("dbrow.cluehelper_skillchallenge_elite_2", "obj.nature_rune", 1),
            Triple("dbrow.cluehelper_skillchallenge_elite_20", "obj.cosmic_rune", 2),
        )) {
            val f = Fixture()
            f.clue(row.asRSCM(), 0)
            f.events.publish(RunesCraftedEvent(f.player, rune, 1, multiplier, false))
            assertEquals(0, f.state().phase)
            assertTrue(f.progress.phase(f.player, 0, f.player.inv[0]!!, 9))
            f.events.publish(RunesCraftedEvent(f.player, rune, 1, multiplier, true))
            f.events.publish(RunesCraftedEvent(f.player, rune, 0, multiplier, false))
            f.events.publish(RunesCraftedEvent(f.player, "obj.air_rune", 28, 10, false))
            if (multiplier == 2) f.events.publish(RunesCraftedEvent(f.player, rune, 28, 1, false))
            assertEquals(9, f.state().phase)
            f.events.publish(RunesCraftedEvent(f.player, rune, 1, multiplier, false))
            assertEquals(10, f.state().phase)
            assertEquals(2, f.state().completed)
        }
    }

    @Test fun `Charlie assigns once and accepts existing unnoted items only after assignment`() {
        val intro = Fixture()
        intro.clue(TrailCatalog.charlieIntro, 0)
        assertTrue(intro.progress.assignCharlie(intro.player, 0, intro.player.inv[0]!!))
        val assigned = intro.state()
        assertTrue(assigned.row in TrailCharlie.products)
        assertEquals(9, assigned.phase)
        assertEquals(2, assigned.completed)
        assertFalse(intro.progress.assignCharlie(intro.player, 0, intro.player.inv[0]!!))
        assertEquals(assigned, intro.state())
        for ((row, expected) in TrailCharlie.products) {
            val f = Fixture()
            f.clue(row, 0)
            f.product(expected.second, expected.first)
            assertEquals(0, f.state().phase)
            assertTrue(f.progress.phase(f.player, 0, f.player.inv[0]!!, 9))
            fun active() = TrailTargets(f.progress).active(f.player).single()
            assertFalse(TrailCharlie.handIn(f.progress, f.player, active()))
            f.player.inv[1] = InvObj(expected.second.replace("obj.", "obj.cert_"))
            assertFalse(TrailCharlie.handIn(f.progress, f.player, active()))
            f.player.inv[1] = InvObj(expected.second)
            assertTrue(TrailCharlie.handIn(f.progress, f.player, active()))
            assertEquals(3, f.state().completed)
            assertEquals(0, f.player.inv.count(expected.second))
        }
    }

    @Test fun `smithing and cooking challenges reject wrong and bonus products`() {
        for ((row, expected) in TrailSkillChallenges.products.filterValues { it.first in setOf("stat.cooking", "stat.smithing") }) {
            val f = Fixture()
            f.clue(row, 9)
            f.product("obj.burnt_swordfish", expected.first)
            f.product(expected.second, expected.first, bonus = true)
            assertEquals(9, f.state().phase)
            f.product(expected.second, expected.first)
            assertEquals(10, f.state().phase)
        }
    }

    @Test fun `Falo assigns a persistent riddle and requires the correct item group`() {
        val f = Fixture()
        f.clue(TrailCatalog.faloIntro, 0)
        assertTrue(f.progress.assignFalo(f.player, 0, f.player.inv[0]!!))
        val assigned = f.state()
        assertEquals("falobard", f.progress.catalog.clues.getValue(assigned.row).kind)
        assertEquals(2, assigned.completed)
        assertFalse(f.progress.assignFalo(f.player, 0, f.player.inv[0]!!))
        assertEquals(assigned, f.state())
        val clue = f.progress.catalog.clues.getValue("dbrow.cluehelper_falobard_master_0".asRSCM())
        val requirements = TrailRequirements(f.progress.catalog)
        assertNotNull(requirements.missing(f.player, clue))
        f.player.inv[1] = InvObj("obj.dragon_scimitar")
        assertNull(requirements.missing(f.player, clue))
        assertEquals(1, f.player.inv.count("obj.dragon_scimitar"))
    }

    @Test fun `Watson stores one clue per tier and preserves deposits until a master fits`() {
        val f = Fixture()
        val watson = TrailWatson(f.progress)
        for ((slot, tier) in TrailWatson.tiers.withIndex()) {
            val clue = f.progress.catalog.forTier(tier).first()
            f.player.inv[slot] = InvObj(checkNotNull(ServerCacheManager.getItem(f.progress.catalog.item(clue))))
        }
        assertEquals(4, watson.deposit(f.player))
        assertEquals(0, watson.deposit(f.player))
        for (slot in 0..27) f.player.inv[slot] = InvObj("obj.abyssal_whip")
        assertFalse(watson.claim(f.player))
        for (tier in TrailWatson.tiers) assertEquals(1, f.player.vars[TrailWatson.flag(tier)])
        f.player.inv[0] = null
        assertTrue(watson.claim(f.player))
        assertEquals(1, f.player.inv.count("obj.trail_clue_master"))
        assertFalse(watson.claim(f.player))
        for (tier in TrailWatson.tiers) assertEquals(0, f.player.vars[TrailWatson.flag(tier)])
    }

    @Test fun `Watson accepts partial deposits but keeps duplicate tiers and blocks banked masters`() {
        val f = Fixture()
        val watson = TrailWatson(f.progress)
        val clue = f.progress.catalog.forTier(TrailTier.EASY).first()
        val item = InvObj(checkNotNull(ServerCacheManager.getItem(f.progress.catalog.item(clue))))
        f.player.inv[0] = item
        f.player.inv[1] = item.copy()
        assertEquals(1, watson.deposit(f.player))
        assertEquals(1, f.player.inv.objs.count { it?.id == item.id })
        assertEquals(0, watson.deposit(f.player))
        assertFalse(watson.claim(f.player))
        for (tier in TrailWatson.tiers) org.rsmod.api.player.vars.VarPlayerIntMapSetter.set(f.player, TrailWatson.flag(tier), 1)
        val bank = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.bank".asRSCM())), arrayOfNulls(800))
        bank[0] = InvObj("obj.trail_clue_master")
        f.player.invMap["inv.bank"] = bank
        assertFalse(watson.claim(f.player))
        for (tier in TrailWatson.tiers) assertEquals(1, f.player.vars[TrailWatson.flag(tier)])
    }

    @Test fun `Chivalry must actually be enabled after assignment`() {
        val f = Fixture()
        f.clue("dbrow.cluehelper_skillchallenge_elite_22".asRSCM(), 9)
        val event = org.rsmod.api.player.events.skilling.PrayerActivatedEvent(f.player, "varbit.prayer_chivalry")
        f.events.publish(event)
        assertEquals(9, f.state().phase)
        org.rsmod.api.player.vars.VarPlayerIntMapSetter.set(f.player, "varbit.prayer_chivalry", 1)
        f.events.publish(event)
        assertEquals(10, f.state().phase)
    }

    @Test fun `nickel and fletching products complete their assigned tasks`() {
        for (row in listOf("dbrow.cluehelper_skillchallenge_elite_nickel", "dbrow.cluehelper_skillchallenge_elite_9", "dbrow.cluehelper_skillchallenge_master_16")) {
            val f = Fixture()
            f.clue(row.asRSCM(), 9)
            val expected = TrailSkillChallenges.products.getValue(row.asRSCM())
            f.product(expected.second, expected.first)
            assertEquals(10, f.state().phase)
        }
    }

    @Test fun `hot cold introductions assign a search without completing a step or rerolling`() {
        for (row in TrailCatalog.hotColdIntros) {
            val f = Fixture()
            f.clue(row, 0)
            val original = f.player.inv[0]!!
            assertTrue(f.progress.assignHotCold(f.player, 0, original))
            val state = f.state()
            val clue = f.progress.catalog.clues.getValue(state.row)
            assertEquals("hotcold", clue.kind)
            assertEquals(2, state.completed)
            assertEquals(6, state.total)
            assertEquals(1, f.player.inv.count("obj.${clue.tier.key}_device"))
            assertFalse(f.progress.assignHotCold(f.player, 0, original))
            assertFalse(f.progress.assignHotCold(f.player, 0, f.player.inv[0]!!))
            assertEquals(state, f.state())
        }
    }

    @Test fun `hot cold assignment is atomic when inventory is full and resets an existing device`() {
        val f = Fixture()
        f.clue("dbrow.cluehelper_cryptic_beginner_reldo".asRSCM(), 0)
        for (slot in 1..27) f.player.inv[slot] = InvObj("obj.abyssal_whip")
        val original = f.player.inv[0]!!
        assertFalse(f.progress.assignHotCold(f.player, 0, original))
        assertSame(original, f.player.inv[0])
        f.player.inv[1] = InvObj("obj.beginner_device", 1, 12345)
        assertTrue(f.progress.assignHotCold(f.player, 0, original))
        assertEquals(0, f.player.inv[1]!!.vars)
        assertEquals(1, f.player.inv.count("obj.beginner_device"))
    }

    @Test fun `blood rune task requires a real blood altar and successful assigned output`() {
        for (altar in listOf("loc.archeus_altar_blood", "loc.blood_altar")) {
            val f = Fixture()
            f.clue("dbrow.cluehelper_skillchallenge_master_14".asRSCM(), 9)
            f.events.publish(RunesCraftedEvent(f.player, "obj.blood_rune", 1, 1, false))
            f.events.publish(RunesCraftedEvent(f.player, "obj.blood_rune", 1, 1, true, altar))
            f.events.publish(RunesCraftedEvent(f.player, "obj.blood_rune", 0, 1, false, altar))
            assertEquals(9, f.state().phase)
            f.events.publish(RunesCraftedEvent(f.player, "obj.blood_rune", 1, 1, false, altar))
            assertEquals(10, f.state().phase)
            assertEquals(2, f.state().completed)
        }
    }

    @Test fun `hot cold and Lletya requirements respect the server quest policy`() {
        val previous = org.rsmod.content.quest.manager.QuestRequirements.activePolicy()
        try {
            val f = Fixture()
            val requirements = TrailRequirements(f.progress.catalog)
            val jorral = f.progress.catalog.clues.getValue("dbrow.cluehelper_cryptic_master_jorral".asRSCM())
            val lletya = f.progress.catalog.clues.values.first { "dbrow.cluehelper_requirement_quest_lletya".asRSCM() in it.requirements }
            org.rsmod.content.quest.manager.QuestRequirements.install(org.rsmod.content.quest.manager.QuestRequirementPolicy(org.rsmod.content.quest.manager.QuestRequirementMode.RespectProgress))
            assertNotNull(requirements.missing(f.player, jorral))
            assertNotNull(requirements.missing(f.player, lletya))
            org.rsmod.content.quest.manager.QuestRequirements.install(org.rsmod.content.quest.manager.QuestRequirementPolicy(org.rsmod.content.quest.manager.QuestRequirementMode.VirtualCompletions, setOf("quest_makinghistory", "quest_mourningsendpart1")))
            assertNull(requirements.missing(f.player, jorral))
            assertNull(requirements.missing(f.player, lletya))
        } finally {
            org.rsmod.content.quest.manager.QuestRequirements.install(previous)
        }
    }

    @Test fun `light orb must be assembled inside the ground floor Dorgesh Kaan bank`() {
        val f = Fixture()
        f.clue(TrailSkillChallenges.lightOrbTask, 9)
        val bank = org.rsmod.map.CoordGrid(2702, 5348, 0)
        for (coords in listOf(org.rsmod.map.CoordGrid(2709, 5348, 0), org.rsmod.map.CoordGrid(2702, 5348, 1), org.rsmod.map.CoordGrid(2702, 5340, 0))) {
            f.player.coords = coords
            f.product("obj.dorgesh_light_bulb")
            assertEquals(9, f.state().phase)
        }
        f.player.coords = bank
        f.product("obj.dorgesh_lightbulb_nofilament")
        assertEquals(9, f.state().phase)
        f.product("obj.dorgesh_light_bulb")
        assertEquals(10, f.state().phase)
        assertEquals(2, f.state().completed)
    }

    private class Fixture {
        val events = EventBus()
        val progress = TrailProgress(TrailCatalog(), DefaultGameRandom(42))
        val player = Player().apply {
            inv = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())), arrayOfNulls(28))
            worn = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.worn".asRSCM())), arrayOfNulls(14))
            statMap.setCurrentLevel("stat.prayer", 99)
            statMap.setCurrentLevel("stat.defence", 99)
            statMap.setCurrentLevel("stat.fletching", 99)
            statMap.setCurrentLevel("stat.cooking", 99)
            statMap.setCurrentLevel("stat.smithing", 99)
            statMap.setCurrentLevel("stat.runecrafting", 99)
            statMap.setCurrentLevel("stat.firemaking", 99)
            statMap.setCurrentLevel("stat.herblore", 99)
            statMap.setCurrentLevel("stat.crafting", 99)
            statMap.setCurrentLevel("stat.mining", 99)
            statMap.setCurrentLevel("stat.fishing", 99)
            statMap.setCurrentLevel("stat.woodcutting", 99)
        }
        init {
            val script = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { script.startup() }
            with(TrailSkillChallenges(progress, TrailTargets(progress), TrailRequirements(progress.catalog))) { script.startup() }
        }
        fun clue(row: Int, phase: Int) {
            if (row == TrailSkillChallenges.lightOrbTask) player.coords = org.rsmod.map.CoordGrid(2702, 5348, 0)
            val clue = progress.catalog.clues.getValue(row)
            player.inv[0] = InvObj(checkNotNull(ServerCacheManager.getItem(progress.catalog.item(clue))), 1, TrailState(row, 6, 2, phase).encode())
        }
        fun state() = checkNotNull(progress.state(player.inv[0]!!))
        fun product(item: String, skill: String = "stat.crafting", count: Int = 1, bonus: Boolean = false) {
            events.publish(SkillingActionCompleteEvent(player, SkillingActionContext.Product(skill, item, count, 0.0, SkillingProductSource.Crafting, bonus)))
        }
    }
    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
