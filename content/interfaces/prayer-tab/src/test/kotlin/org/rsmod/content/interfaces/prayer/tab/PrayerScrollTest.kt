package org.rsmod.content.interfaces.prayer.tab

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.aconverted.interf.IfButtonOp
import kotlin.coroutines.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.parallel.ResourceLock
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mockito.*
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.HeldInteractions
import org.rsmod.api.player.protect.*
import org.rsmod.api.player.ui.IfOverlayButton
import org.rsmod.api.player.worn.HeldEquipOp
import org.rsmod.content.interfaces.prayer.tab.scripts.*
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.HeldOp
import org.rsmod.game.inv.*
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@ResourceLock("ServerCacheManager")
internal class PrayerScrollTest {
    @ParameterizedTest @EnumSource(PrayerScroll::class)
    fun `native Read consumes one scroll and unlocks only its prayer`(scroll: PrayerScroll) {
        val f = Fixture(scroll)
        f.read(); assertEquals(0, f.player.vars[scroll.unlock]); assertEquals(2, f.player.inv.count(scroll.item))
        f.finish()
        assertEquals(1, f.player.vars[scroll.unlock]); assertEquals(1, f.player.inv.count(scroll.item))
        for (other in PrayerScroll.entries.filter { it != scroll }) assertEquals(0, f.player.vars[other.unlock])
        val flag = ServerCacheManager.getVarbit(scroll.unlock.asRSCM())!!
        assertEquals(dev.openrune.types.varp.VarpLifetime.Perm, ServerCacheManager.getVarp(flag.varp)!!.scope)
        val loaded = Player()
        loaded.vars.backing.putAll(f.player.vars.backing)
        assertEquals(1, loaded.vars[scroll.unlock])
        f.result = null; f.read(); f.finish()
        assertEquals(1, f.player.inv.count(scroll.item), "duplicate Read preserves scroll")
    }

    @ParameterizedTest @EnumSource(PrayerScroll::class)
    fun `cancel or stale inventory never unlocks or removes replacement`(scroll: PrayerScroll) {
        val cancel = Fixture(scroll); cancel.read(); cancel.finish(false)
        assertEquals(0, cancel.player.vars[scroll.unlock]); assertEquals(2, cancel.player.inv.count(scroll.item))
        val stale = Fixture(scroll); stale.read(); stale.player.inv[0] = InvObj("obj.coins", 123); stale.finish()
        assertEquals(0, stale.player.vars[scroll.unlock]); assertEquals(123, stale.player.inv.count("obj.coins"))
    }

    @Test fun `Chivalry and Piety need levels but no quest and scroll prayers stay locked`() {
        val repo = PrayerRepository().also { it.load() }
        val player = Player().apply { statMap.setBaseLevel("stat.prayer", 99); statMap.setBaseLevel("stat.defence", 99) }
        for (name in listOf("Chivalry", "Piety")) {
            val prayer = repo.prayerList.single { it.name == name }
            assertTrue(prayer.unlocked == null); assertTrue(prayer.hasAllRequirements(player))
            player.statMap.setBaseLevel("stat.defence", 1); assertFalse(prayer.hasAllRequirements(player))
            player.statMap.setBaseLevel("stat.defence", 99)
            assertFalse(prayer.lockedMessage.orEmpty().contains("quest", true))
        }
        for (scroll in PrayerScroll.entries.take(3)) assertFalse(repo.prayerList.single { it.name.equals(scroll.prayer, true) }.hasAllRequirements(player))
        val deadeye = Fixture(PrayerScroll.Deadeye); deadeye.read(); deadeye.finish()
        val upgraded = repo.resolve(deadeye.player, repo.prayerList.single { it.name == "Eagle Eye" })
        assertEquals("Deadeye", upgraded.name); assertEquals(62, upgraded.level)
        assertFalse(upgraded.hasAllRequirements(deadeye.player))
        val vigour = Fixture(PrayerScroll.MysticVigour); vigour.read(); vigour.finish()
        assertEquals("Mystic Vigour", repo.resolve(vigour.player, repo.prayerList.single { it.name == "Mystic Might" }).name)
        assertEquals(0, player.vars["varbit.kr_knightwaves_state"])
    }

    @Test fun `native prayer and quick-prayer buttons use default unlocks and upgraded tiers`() {
        val f = Fixture(PrayerScroll.Deadeye); f.read(); f.finish()
        f.player.statMap.setBaseLevel("stat.prayer", 99); f.player.statMap.setCurrentLevel("stat.prayer", 99)
        f.player.statMap.setBaseLevel("stat.defence", 99)
        val repo = PrayerRepository().also { it.load() }
        val factory = mock(ProtectedAccessContextFactory::class.java).apply { `when`(create()).thenReturn(f.context) }
        val launcher = ProtectedAccessLauncher(factory)
        val script = PrayerTabScript::class.java.declaredConstructors.single().apply { isAccessible = true }.newInstance(repo, f.bus, launcher) as PrayerTabScript
        val ctx = ScriptContext(f.bus, CheatCommandMap(), EngineQueueCache())
        with(script) { ctx.startup() }
        with(QuickPrayerScript(repo, f.bus, launcher)) { ctx.startup() }
        fun button(event: IfOverlayButton) {
            val action: suspend () -> Unit = { assertTrue(f.bus.publish(ProtectedAccess(f.player, GameCoroutine(), f.context), event)) }
            var result: Result<Unit>? = null
            action.startCoroutine(object : Continuation<Unit> { override val context = EmptyCoroutineContext; override fun resumeWith(r: Result<Unit>) { result = r } })
            checkNotNull(result).getOrThrow()
        }
        val piety = repo.prayerComponents.entries.single { it.value.name == "Piety" }
        button(IfOverlayButton(piety.key, -1, null, IfButtonOp.Op1))
        assertEquals(1, f.player.vars["varbit.prayer_piety"])
        val quick = ServerCacheManager.fromComponent("component.quickprayer:buttons".asRSCM())
        button(IfOverlayButton(quick, 22, null, IfButtonOp.Op1))
        val orb = ServerCacheManager.fromComponent("component.orbs:prayerbutton".asRSCM())
        button(IfOverlayButton(orb, -1, null, IfButtonOp.Op1))
        assertEquals(1, f.player.vars["varbit.prayer_eagleeye"])
        assertEquals(1, f.player.vars["varbit.prayer_deadeye_unlocked"])
        assertEquals(0, f.player.vars["varbit.prayer_piety"])
        assertEquals(0, f.player.vars["varbit.kr_knightwaves_state"])
    }

    private class Fixture(val scroll: PrayerScroll) {
        val bus = EventBus(); val coroutine = GameCoroutine("prayer-scroll-read")
        var result: Result<Unit>? = null
        val player = Player().apply { worn = Inventory(ServerCacheManager.getInventory("inv.worn".asRSCM())!!, arrayOfNulls(14)); inv = Inventory(ServerCacheManager.getInventory("inv.inv".asRSCM())!!, arrayOfNulls(28)); inv[0] = InvObj(scroll.item); inv[1] = InvObj(scroll.item) }
        val context = ProtectedAccessContextFactory.empty().copy(getEventBus = { bus }, getAlignment = { TextAlignment() })
        val held: HeldInteractions
        init {
            val scripts = ScriptContext(bus, CheatCommandMap(), EngineQueueCache())
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { scripts.startup() }
            with(PrayerScrollScript()) { scripts.startup() }
            val c = HeldInteractions::class.java.declaredConstructors.single().apply { isAccessible = true }
            held = c.newInstance(bus, mock(c.parameterTypes[1]), mock(c.parameterTypes[2]), HeldEquipOp(bus)) as HeldInteractions
        }
        fun read() {
            player.activeCoroutine = coroutine
            val action: suspend () -> Unit = { held.interact(ProtectedAccess(player, coroutine, context), player.inv, player.inv.indexOfFirst { it?.id == scroll.item.asRSCM() }, HeldOp.Op1) }
            action.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(r: Result<Unit>) { result = r }
            })
            result?.getOrThrow()
        }
        fun finish(accept: Boolean = true) {
            repeat(10) {
                result?.getOrThrow(); if (result != null) return
                val menu = player.ui.containsModal("interface.chatmenu")
                coroutine.resumeWith(ResumePauseButtonInput(if (menu) "component.chatmenu:options" else "component.messagebox:continue", if (menu) { if (accept) 1 else 2 } else -1))
            }
            fail<Unit>("Unfinished prayer scroll dialogue")
        }
    }
    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
