package org.rsmod.content.bosses.tormenteddemon

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.HeldInteractions
import org.rsmod.api.player.interact.HeldUInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.worn.HeldEquipOp
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.HeldOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class TormentedDemonCraftingTest {
    @Test fun `native Wield equips Arclight and synapse weapons without opening Check or Revert`() {
        for (weapon in listOf("obj.arclight", "obj.arclight_inactive", "obj.emberlight", "obj.scorching_bow", "obj.purging_staff")) {
            val f = Fixture(99); f.put(0, weapon)
            val type = ServerCacheManager.getItem(weapon.asRSCM())!!
            assertEquals("Wield", type.interfaceOptions[1], type.interfaceOptions.toString())
            f.heldOp(HeldOp.Op2)
            assertNotNull(f.result, "Wield must not suspend in a revert dialogue")
            f.result!!.getOrThrow()
            assertEquals(weapon.asRSCM(), f.player.worn[type.wearpos1]?.id)
            assertEquals(0, f.player.inv.count(weapon))
            assertEquals(0, f.player.inv.count("obj.tormented_synapse"))
            assertFalse(f.player.ui.containsModal("interface.messagebox"))
        }
    }

    @Test fun `native Arclight Check reports charges without equipping or consuming the blade`() {
        for (weapon in listOf("obj.arclight", "obj.arclight_inactive")) {
            val f = Fixture(99)
            f.player.inv[0] = InvObj(weapon, vars = ArclightState.pack(1000, 7000))
            val item = f.player.inv[0]
            val type = ServerCacheManager.getItem(weapon.asRSCM())!!
            assertEquals("Check", type.interfaceOptions[2], type.interfaceOptions.toString())
            f.heldOp(HeldOp.Op3)
            assertNotNull(f.result); f.result!!.getOrThrow()
            assertSame(item, f.player.inv[0])
            assertTrue(f.player.worn.objs.all { it == null })
            assertTrue(f.client.messages.any { it.toString().contains("1000 charges; 70% infusion") }, f.client.messages.toString())
        }
    }

    @Test fun `native notes read unlocks bow and subsequent craft gives one tenth XP`() {
        val f = Fixture(99)
        f.put(0, "obj.duradels_notes_on_demon_slaying"); f.read(); f.finish()
        assertEquals(1, f.player.tdCraftingState)
        f.put(0, "obj.tormented_synapse"); f.put(1, "obj.unstrung_magic_longbow")
        val before = f.player.statMap.getFineXP("stat.fletching")
        f.result = null; f.use(0, 1); f.finish()
        assertEquals(1, f.player.inv.count("obj.scorching_bow"))
        val first = f.player.statMap.getFineXP("stat.fletching") - before
        f.put(0, "obj.tormented_synapse"); f.put(1, "obj.unstrung_magic_longbow")
        val again = f.player.statMap.getFineXP("stat.fletching")
        f.result = null; f.use(1, 0); f.finish()
        assertEquals(first, (f.player.statMap.getFineXP("stat.fletching") - again) * 10)
        assertEquals(5, f.player.tdCraftingState)
    }

    @Test fun `unread notes low level and missing items preserve synapse and XP`() {
        for (level in listOf(73, 99)) {
            val f = Fixture(level); f.put(0, "obj.tormented_synapse"); f.put(1, "obj.unstrung_magic_longbow")
            if (level == 73) f.player.tdCraftingState = 1
            val before = f.player.statMap.getFineXP("stat.fletching")
            f.use(0, 1); f.finish()
            assertEquals(1, f.player.inv.count("obj.tormented_synapse")); assertEquals(1, f.player.inv.count("obj.unstrung_magic_longbow"))
            assertEquals(before, f.player.statMap.getFineXP("stat.fletching"))
        }
    }

    @Test fun `native anvil creates Emberlight only from ready charged or infused blade`() {
        for ((charges, infusion) in listOf(10000 to 0, 7000 to 3000, 0 to 10000, 7000 to 2999)) {
            val f = Fixture(99); f.player.tdCraftingState = 1
            f.put(0, "obj.tormented_synapse"); f.put(1, "obj.hammer")
            val weapon = if (charges == 0) "obj.arclight_inactive" else "obj.arclight"
            f.player.inv[2] = InvObj(weapon, vars = ArclightState.pack(charges, infusion))
            f.anvil(); f.finish()
            assertEquals(if (charges + infusion >= 10000) 1 else 0, f.player.inv.count("obj.emberlight"))
            assertEquals(if (charges + infusion >= 10000) 0 else 1, f.player.inv.count(weapon))
            assertEquals(1, f.player.inv.count("obj.hammer"))
        }
    }

    @Test fun `Purging staff requires unboosted Smithing 55 and returns native XP after atomic output`() {
        for (base in listOf(54, 55)) {
            val f = Fixture(99); f.player.tdCraftingState = 1
            f.player.statMap.setBaseLevel("stat.smithing", base.toByte())
            f.put(0, "obj.tormented_synapse"); f.put(1, "obj.iron_bar"); f.put(2, "obj.battlestaff"); f.put(3, "obj.hammer")
            val xp = f.player.statMap.getFineXP("stat.smithing")
            f.anvil(); f.finish()
            assertEquals(if (base == 55) 1 else 0, f.player.inv.count("obj.purging_staff"))
            assertEquals(base == 55, f.player.statMap.getFineXP("stat.smithing") > xp)
        }
    }

    @Test fun `revert returns only synapse cancellation is safe and repeated input does not duplicate`() {
        for (weapon in listOf("obj.emberlight", "obj.scorching_bow", "obj.purging_staff")) for (accept in listOf(true, false)) {
            val f = Fixture(99); f.put(0, weapon)
            val type = ServerCacheManager.getItem(weapon.asRSCM())!!
            assertEquals("Revert", type.interfaceOptions[3], type.interfaceOptions.toString())
            for (slot in 1..27) f.put(slot, "obj.abyssal_whip")
            f.revert(); f.finish(accept)
            assertEquals(if (accept) 1 else 0, f.player.inv.count("obj.tormented_synapse"))
            assertEquals(if (accept) 0 else 1, f.player.inv.count(weapon))
            assertEquals(27, f.player.inv.count("obj.abyssal_whip"))
        }
    }

    @Test fun `burning claws combine through both native held item orders in a full inventory`() {
        val f = Fixture(99); f.put(0, "obj.bone_claw"); f.put(1, "obj.bone_claw")
        for (slot in 2..27) f.put(slot, "obj.abyssal_whip")
        f.use(1, 0); f.finish()
        assertEquals(1, f.player.inv.count("obj.bone_claws")); assertEquals(0, f.player.inv.count("obj.bone_claw"))
        assertEquals(26, f.player.inv.count("obj.abyssal_whip"))
    }

    @Test fun `Catacombs altar creates charged Arclight and shards preserve spent infusion`() {
        val f = Fixture(99); f.put(0, "obj.darklight"); f.player.inv[1] = InvObj("obj.cata_shard", 3)
        for (slot in 2..27) f.put(slot, "obj.abyssal_whip")
        f.anvil(symbol = "loc.cata_altar"); f.finish()
        assertEquals(1000, ArclightState.charges(f.player.inv[0]!!))
        assertEquals(0, f.player.inv.count("obj.cata_shard"))
        f.player.inv[0] = InvObj("obj.arclight_inactive", vars = ArclightState.pack(0, 7000))
        f.player.inv[1] = InvObj("obj.cata_shard", 3)
        f.result = null; f.use(1, 0); f.finish()
        assertEquals(1000, ArclightState.charges(f.player.inv[0]!!))
        assertEquals(7000, ArclightState.infusion(f.player.inv[0]!!.copy()))
    }

    @Test fun `one two or three shards yield correct persistent charges without losing infusion`() {
        for ((count, expected) in listOf(1 to 333, 2 to 666, 3 to 1000)) {
            val f = Fixture(99); f.player.inv[0] = InvObj("obj.arclight", vars = ArclightState.pack(500, 500))
            f.player.inv[1] = InvObj("obj.cata_shard", count)
            f.use(0, 1); f.finish()
            assertEquals(500 + expected, ArclightState.charges(f.player.inv[0]!!))
            assertEquals(500, ArclightState.infusion(f.player.inv[0]!!))
            assertEquals(0, f.player.inv.count("obj.cata_shard"))
        }
    }
    private class Fixture(level: Int) {
        val events = EventBus()
        val coroutine = GameCoroutine("spirit-shield-test")
        var result: Result<Unit>? = null
        val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getAlignment = { TextAlignment() },
        )
        val client = RecordingClient()
        val player = Player(client).apply {
            inv = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())), arrayOfNulls(28))
            worn = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.worn".asRSCM())), arrayOfNulls(14))
            statMap.setCurrentLevel("stat.crafting", level.toByte())
            statMap.setCurrentLevel("stat.fletching", level.toByte())
        }
        init {
            for (stat in listOf("attack", "strength", "defence", "ranged", "magic")) player.statMap.setBaseLevel("stat.$stat", level.toByte())
            player.statMap.setBaseLevel("stat.smithing", level.toByte())
            player.statMap.setBaseLevel("stat.crafting", level.toByte())
            player.statMap.setBaseLevel("stat.fletching", level.toByte())
            player.statMap.setCurrentLevel("stat.smithing", level.toByte())
            player.statMap.setBaseLevel("stat.hitpoints", 99.toByte())
            player.statMap.setCurrentLevel("stat.hitpoints", 99)

            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { scripts.startup() }
            with(TormentedDemonCraftingScript()) { scripts.startup() }
            with(ArclightChargingScript()) { scripts.startup() }
        }
        fun put(slot: Int, item: String) { player.inv[slot] = InvObj(item, 1) }
        fun read(slot: Int = 0) = launch {
            val obj = player.inv[slot]!!
            events.publish(this, HeldObjEvents.Op1(slot, obj, ServerCacheManager.getItem(obj.id)!!, player.inv))
        }
        private val held by lazy {
            val constructor = HeldInteractions::class.java.declaredConstructors.single().apply { isAccessible = true }
            constructor.newInstance(events, org.mockito.Mockito.mock(constructor.parameterTypes[1]), org.mockito.Mockito.mock(constructor.parameterTypes[2]), HeldEquipOp(events)) as HeldInteractions
        }
        fun heldOp(op: HeldOp, slot: Int = 0) = launch { held.interact(this, player.inv, slot, op) }
        fun revert(slot: Int = 0) = heldOp(HeldOp.Op4, slot)
        fun anvil(slot: Int = 0, symbol: String = "loc.anvil") = launch {
            val type = ServerCacheManager.getObject(symbol.asRSCM())!!
            val loc = org.rsmod.game.loc.BoundLocInfo(org.rsmod.game.loc.LocInfo(2, org.rsmod.map.CoordGrid(3200, 3200), org.rsmod.game.loc.LocEntity(type.id, 10, 0)), type)
            val ops = org.rsmod.api.player.interact.LocUInteractions::class.java.getDeclaredConstructor(EventBus::class.java).apply { isAccessible = true }.newInstance(events)
            ops.interactOp(this, loc, loc, type, ServerCacheManager.getItem(player.inv[slot]!!.id)!!, player.inv, slot)
        }
        fun use(first: Int, second: Int) = launch {
            HeldUInteractions(events).interact(this, player.inv,
                ServerCacheManager.getItem(player.inv[first]!!.id)!!, first,
                ServerCacheManager.getItem(player.inv[second]!!.id)!!, second)
        }
        fun launch(action: suspend ProtectedAccess.() -> Unit) {
            player.activeCoroutine = coroutine
            val block: suspend () -> Unit = { action(ProtectedAccess(player, coroutine, context)) }
            block.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { this@Fixture.result = result }
            })
            result?.getOrThrow()
        }
        fun finish(accept: Boolean = true) {
            repeat(200) {
                result?.getOrThrow()
                if (result != null) return
                if (coroutine.isAwaiting(ResumePauseButtonInput::class)) {
                    val menu = player.ui.containsModal("interface.chatmenu")
                    val component = when {
                        menu -> "component.chatmenu:options"
                        player.ui.containsModal("interface.objectbox") -> "component.objectbox:universe"
                        else -> "component.messagebox:continue"
                    }
                    coroutine.resumeWith(ResumePauseButtonInput(component, if (menu) { if (accept) 1 else 2 } else -1))
                } else if (coroutine.isAwaiting(org.rsmod.api.player.input.ResumePCountDialogInput::class)) {
                    coroutine.resumeWith(org.rsmod.api.player.input.ResumePCountDialogInput(30))
                } else {
                    player.currentMapClock++
                    player.processedMapClock = player.currentMapClock
                    coroutine.advance()
                }
            }
            fail<Unit>("Crafting interaction did not finish")
        }
    }
    private class RecordingClient : Client<Any, Any> {
        val messages = mutableListOf<Any>()
        override fun write(message: Any) { messages.add(message) }
        override fun close() = Unit
        override fun read(player: Player) = Unit
        override fun flush() = Unit
        override fun flushHighPriority() = Unit
        override fun unregister(service: Any, player: Player) = Unit
    }
    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
