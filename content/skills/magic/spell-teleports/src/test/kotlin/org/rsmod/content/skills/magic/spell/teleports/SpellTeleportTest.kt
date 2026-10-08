package org.rsmod.content.skills.magic.spell.teleports

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.aconverted.interf.IfButtonOp
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.Mockito.*
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.combat.commons.magic.MagicSpellType
import org.rsmod.api.config.refs.params
import org.rsmod.api.game.process.player.PlayerQueueProcessor
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.hook.*
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.PlayerTInteractions
import org.rsmod.api.player.protect.*
import org.rsmod.api.player.ui.IfOverlayButton
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.*
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@OptIn(org.rsmod.annotations.InternalApi::class)
internal class SpellTeleportTest {
    @Test fun `native buttons on all four books teleport a level one player without runes or quests`() {
        val names = listOf("obj.51_ardougne_teleport", "obj.96_ghorrock_teleport", "obj.69_tele_moonclan", "obj.br_air_staff")
        for (name in names) {
            val f = Fixture(); val spell = spells.getObjSpell(ServerCacheManager.getItem(name.asRSCM())!!)!!
            val destination = spell.obj.param(params.spell_telecoord)
            f.collision.allocateIfAbsent(destination.x, destination.z, destination.level)
            f.click(name); f.finish()
            assertEquals(destination, f.player.coords, name)
            assertTrue(f.player.inv.isEmpty())
            assertEquals(0, f.player.statMap.getFineXP("stat.magic"))
        }
    }

    @Test fun `teleport validator at cast and landing blocks travel and leaves inventory untouched`() {
        for (late in listOf(true, false)) {
            val f = Fixture(); val start = f.player.coords; f.player.inv[0] = InvObj("obj.lawrune", 100)
            if (!late) f.denial = "Blocked"
            f.click("obj.25_varrock_teleport"); f.denial = "Blocked"; f.finish()
            assertEquals(start, f.player.coords); assertEquals(100, f.player.inv.count("obj.lawrune"))
        }
    }

    @Test fun `home group and alternate destination buttons are registered`() {
        for ((name, destination, op) in listOf(
            Triple("obj.48_home_teleport", CoordGrid(3222, 3222), IfButtonOp.Op1),
            Triple("obj.01_zaros_home_tele", CoordGrid(3087, 3496), IfButtonOp.Op1),
            Triple("obj.01_lunar_home_tele", CoordGrid(2114, 3915), IfButtonOp.Op1),
            Triple("obj.deadman_level99_lamp", CoordGrid(1699, 3882), IfButtonOp.Op1),
            Triple("obj.70_tele_moonclan_group", CoordGrid(2114, 3915), IfButtonOp.Op1),
            Triple("obj.25_varrock_teleport", CoordGrid(3164, 3487), IfButtonOp.Op2),
        )) {
            val f = Fixture(); f.collision.allocateIfAbsent(destination.x, destination.z, destination.level)
            f.click(name, op); f.finish(); assertEquals(destination, f.player.coords, name)
        }
    }

    @Test fun `every native fixed destination teleport has a level one rune-free button route`() {
        val fixed = spells.allSpells().filter { it.type == MagicSpellType.Teleport && it.obj.paramOrNull(params.spell_telecoord) != null }
        assertTrue(fixed.size >= 30)
        for (spell in fixed) {
            val f = Fixture()
            val name = dev.openrune.rscm.RSCM.getReverseMapping(dev.openrune.rscm.RSCMType.OBJ, spell.obj.id)
            val expected = spell.obj.param(params.spell_telecoord).let { if (name == "obj.64_ape_atoll_teleport") it.copy(level = 1) else it }
            f.collision.allocateIfAbsent(expected.x, expected.z, expected.level)
            f.click(name); f.finish(); assertEquals(expected, f.player.coords, name)
            assertTrue(f.player.inv.isEmpty(), name)
            assertEquals(0, f.player.statMap.getFineXP("stat.magic"), name)
        }
    }

    @Test fun `free travel preserves native Barrows spell metadata used by tablet crafting`() {
        val spell = spells.getObjSpell(ServerCacheManager.getItem("obj.br_air_staff".asRSCM())!!)!!
        assertEquals(83, spell.levelReq)
        assertEquals(90.0, spell.castXp)
        assertEquals(3, spell.objReqs.size)
    }

    @Test fun `native Teleother requires Accept Aid and explicit acceptance before recipient travels`() {
        for (accept in listOf(true, false)) {
            val f = Fixture(); val target = f.recipient(); val start = target.coords
            f.target("obj.74_teleother_lumbridge", target)
            assertEquals(start, target.coords)
            assertTrue(target.activeCoroutine?.isAwaiting(ResumePauseButtonInput::class) == true)
            f.respond(target, accept); f.finish(target)
            assertEquals(if (accept) CoordGrid(3221, 3218) else start, target.coords)
            assertEquals(CoordGrid(3222, 3218), f.player.coords, "caster stays put")
        }
        val f = Fixture(); val target = f.recipient(false)
        f.target("obj.74_teleother_lumbridge", target)
        assertTrue(target.activeCoroutine == null)
    }

    @Test fun `group teleport never moves a neighbour who declines and revalidates teleport denial`() {
        for (decline in listOf(true, false)) {
            val f = Fixture(); val target = f.recipient(); val start = target.coords
            f.collision.allocateIfAbsent(2114, 3915, 0)
            f.click("obj.70_tele_moonclan_group")
            if (!decline) f.denial = "Blocked"
            f.respond(target, !decline); f.finish(target)
            assertEquals(start, target.coords)
        }
    }

    @Test fun `accepted group teleport moves caster and adjacent consenting player`() {
        val f = Fixture(); val target = f.recipient()
        f.collision.allocateIfAbsent(2114, 3915, 0)
        f.click("obj.70_tele_moonclan_group"); f.respond(target, true)
        f.finish(target); f.finish()
        assertEquals(CoordGrid(2114, 3915), target.coords)
        assertEquals(target.coords, f.player.coords)
    }

    private class Fixture {
        val bus = EventBus(); val collision = sharedCollision; val areas = mock(AreaChecker::class.java)
        var denial: String? = null
        val validator = PlayerTeleportValidator(setOf(PlayerTeleportValidateHook { _, _, _ -> denial }))
        val player = Player().apply { inv = Inventory(ServerCacheManager.getInventory("inv.inv".asRSCM())!!, arrayOfNulls(28)); coords = CoordGrid(3222, 3218); currentMapClock = 100; processedMapClock = 100 }
        val context = ProtectedAccessContextFactory.empty().copy(getEventBus = { bus }, getCollision = { collision }, getTeleportValidator = { validator }, getAreaChecker = { areas }, getAlignment = { TextAlignment() })
        val factory = mock(ProtectedAccessContextFactory::class.java).apply { `when`(create()).thenReturn(context) }
        val launcher = ProtectedAccessLauncher(factory)
        val players = org.rsmod.game.entity.PlayerList()
        val queues = PlayerQueueProcessor(bus, launcher)
        init {
            collision.allocateIfAbsent(player.coords.x, player.coords.z, player.coords.level)
            val ctx = ScriptContext(bus, CheatCommandMap(), EngineQueueCache())
            with(SpellTeleportScript(spells, validator, areas, launcher, players)) { ctx.startup() }
        }
        fun click(name: String, op: IfButtonOp = IfButtonOp.Op1) {
            val spell = spells.getObjSpell(ServerCacheManager.getItem(name.asRSCM())!!)!!
            assertTrue(launcher.launch(player) { assertTrue(bus.publish(this, IfOverlayButton(spell.component, -1, null, op))) })
        }
        fun recipient(aid: Boolean = true): Player = Player().apply {
            coords = player.coords.translateX(1); currentMapClock = 100; processedMapClock = 100
            slotId = 2; uuid = 2; assignUid()
            inv = Inventory(ServerCacheManager.getInventory("inv.inv".asRSCM())!!, arrayOfNulls(28))
            collision.allocateIfAbsent(coords.x, coords.z, coords.level)
            players[2] = this
            launcher.launch(this) { vars["varbit.option_acceptaid"] = if (aid) 1 else 0 }
        }
        fun target(name: String, target: Player) {
            val spell = spells.getObjSpell(ServerCacheManager.getItem(name.asRSCM())!!)!!
            val producer = PlayerTInteractions(bus)
            assertTrue(launcher.launch(player) { assertTrue(bus.publish(this, producer.opTrigger(target, spell.component, -1, null)!!)) })
        }
        fun respond(target: Player, accept: Boolean) {
            repeat(3) {
                val coroutine = target.activeCoroutine ?: return
                if (!coroutine.isAwaiting(ResumePauseButtonInput::class)) return
                val menu = target.ui.containsModal("interface.chatmenu")
                coroutine.resumeWith(ResumePauseButtonInput(if (menu) "component.chatmenu:options" else "component.messagebox:continue", if (menu) { if (accept) 1 else 2 } else -1))
            }
        }
        fun finish(player: Player = this.player) { repeat(6) { player.currentMapClock++; player.processedMapClock = player.currentMapClock; queues.process(player) } }
    }
    companion object {
        private val sharedCollision = CollisionFlagMap()
        private lateinit var spells: MagicSpellRegistry

        @JvmStatic @BeforeAll fun cache() {
            ServerCacheManager.init(240).close()
            spells = MagicSpellRegistry()
            spells.javaClass.declaredMethods.first { it.name.startsWith("init$") }.apply { isAccessible = true }.invoke(spells)
        }
    }
}
