package org.rsmod.content.other.pets

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.or2.central.account.Rights
import net.rsprot.protocol.game.incoming.buttons.If3Button
import net.rsprot.protocol.game.outgoing.interfaces.IfSetObject
import net.rsprot.protocol.game.outgoing.interfaces.IfSetText
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.net.rsprot.handlers.If3ButtonHandler
import org.rsmod.api.player.input.ResumePStringDialogInput
import org.rsmod.api.player.interact.HeldInteractions
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.worn.HeldEquipOp
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
@OptIn(InternalApi::class)
class PetMenuTest {
    @Test fun `gallery sends inventory models searches and uses existing reward rules`() {
        val f = Fixture()
        f.open()
        assertTrue(f.player.ui.containsModal("interface.pet_menu"))
        assertEquals(18, f.client.messages.count { it is IfSetObject })
        f.click("search")
        val pet = Pets.all.first { it.name == "Butch" }
        f.player.resumeActiveCoroutine(ResumePStringDialogInput(pet.name))
        assertEquals(pet.name, f.text("pet_0"))
        f.click("icon_0")
        verify(f.rewards).give(f.player, pet.base.obj)
        assertFalse(f.player.ui.containsModal("interface.pet_menu"))
    }

    @Test fun `non-admin cannot open or submit a stale gallery action`() {
        val f = Fixture()
        f.open()
        f.player.modLevel = Rights.NONE
        f.click("icon_0")
        f.open()
        verifyNoInteractions(f.rewards)
        assertFalse(f.player.ui.containsModal("interface.pet_menu"))
    }

    @Test fun `empty results and out of range clicks never award a pet`() {
        val f = Fixture()
        f.open(); f.click("search")
        f.player.resumeActiveCoroutine(ResumePStringDialogInput("no-pet-has-this-name"))
        f.click("next"); f.click("icon_17")
        assertEquals("No pets found", f.text("status"))
        assertEquals("Page 1/1", f.text("page"))
        verifyNoInteractions(f.rewards)
    }

    @Test fun `gallery fits fixed mode and every pet has a valid item model`() {
        val ui = ServerCacheManager.getInterface("interface.pet_menu".asRSCM())!!
        assertEquals(18, ui.components.values.count { it.type == 5 })
        for (c in ui.components.values) {
            assertTrue(c.x >= 0 && c.y >= 0 && c.x + c.width <= 512 && c.y + c.height <= 334, c.internalName)
            if (c.type == 4) assertTrue(c.textShadow)
        }
        for (pet in PetMenu.entries) assertNotNull(ServerCacheManager.getItem(pet.obj.asRSCM()), pet.name)
        assertEquals(PetMenu.entries.size, PetMenu.entries.map { it.obj }.toSet().size)
        assertEquals(194, PetMenu.entries.size)
    }

    @Test fun `all screenshot companion categories are selectable without boss rewards`() {
        for (category in listOf("Cats", "Dogs", "Hellcats", "Archibald", "Broav", "Humphrey Dumphrey", "Mayor of Catherby", "Pet fish", "Pet rock", "Spooky chair", "Toy cat")) {
            val f = Fixture()
            f.open(); f.click("search")
            f.player.resumeActiveCoroutine(ResumePStringDialogInput(category))
            val choice = PetMenu.matches(category).first()
            assertEquals(choice.name, f.text("pet_0"))
            f.click("icon_0")
            assertEquals(1, f.player.inv.count(choice.obj), category)
            verifyNoInteractions(f.rewards)
        }
    }

    @Test fun `full inventory cannot receive companions`() {
        val f = Fixture()
        repeat(28) { f.player.inv[it] = InvObj("obj.spade", 1) }
        f.open(); f.click("search")
        f.player.resumeActiveCoroutine(ResumePStringDialogInput("Broav"))
        f.click("icon_0")
        assertEquals(28, f.player.inv.count("obj.spade"))
        assertEquals(0, f.player.inv.count(CompanionPets.broav.obj))
        verifyNoInteractions(f.rewards)
    }

    @Test fun `ordinary owner paints held Archibald and cannot turn paint into a pet grant`() {
        val f = Fixture()
        f.player.modLevel = Rights.NONE
        val original = InvObj(CompanionPets.archibald[0].obj, 1)
        f.player.inv[0] = original
        repeat(27) { f.player.inv[it + 1] = InvObj("obj.spade", 1) }
        f.paint()
        f.click("search"); f.click("clear")
        assertEquals("Select a pattern to paint your Archibald", f.text("status"))
        f.click("icon_6")
        assertEquals(CompanionPets.archibald[6].objId, f.player.inv[0]?.id)
        assertEquals(27, f.player.inv.count("obj.spade"))
        f.open()
        assertFalse(f.player.ui.containsModal("interface.pet_menu"))
        verifyNoInteractions(f.rewards)
    }

    @Test fun `stale painting cannot replace a changed item or grant an extra egg`() {
        val f = Fixture()
        val original = InvObj(CompanionPets.archibald[0].obj, 1)
        f.player.inv[0] = original
        f.paint()
        val replacement = InvObj(CompanionPets.archibald[0].obj, 1)
        f.player.inv[0] = replacement
        f.click("icon_6")
        assertSame(replacement, f.player.inv[0])
        assertEquals(0, f.player.inv.count(CompanionPets.archibald[6].obj))
    }

    private class Fixture {
        val events = EventBus()
        val client = RecordingClient()
        val player = Player(client).apply {
            username = "pet-menu-test"; modLevel = Rights.ADMINISTRATOR
            inv = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())), arrayOfNulls(28))
        }
        val rewards = mock(PetRewards::class.java)
        val menu = PetMenu(rewards)
        val launcher: ProtectedAccessLauncher
        val held: HeldInteractions
        init {
            val factory = mock(ProtectedAccessContextFactory::class.java)
            `when`(factory.create()).thenReturn(ProtectedAccessContextFactory.empty().copy(getEventBus = { events }))
            launcher = ProtectedAccessLauncher(factory)
            with(menu) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
            val constructor = HeldInteractions::class.java.declaredConstructors.single().apply { isAccessible = true }
            held = constructor.newInstance(events, mock(constructor.parameterTypes[1]), mock(constructor.parameterTypes[2]), HeldEquipOp(events)) as HeldInteractions
            with(CompanionPetScript(mock(PetFollowers::class.java), menu, held)) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
        }
        fun open() { launcher.launchLenient(player) { menu.open(this) } }
        fun paint() { launcher.launchLenient(player) { held.interact(this, player.inv, 0, HeldOp.Op3) } }
        fun click(name: String) {
            val constructor = If3Button::class.java.declaredConstructors.single { it.parameterCount == 5 }
            val packet = constructor.newInstance("component.pet_menu:$name".asRSCM(), -1, -1, 1, null) as If3Button
            If3ButtonHandler(events, launcher).handle(player, packet)
        }
        fun text(name: String) = client.messages.filterIsInstance<IfSetText>().last { it.combinedId == "component.pet_menu:$name".asRSCM() }.text
    }

    private class RecordingClient : Client<Any, Any> {
        val messages = mutableListOf<Any>()
        override fun write(message: Any) { messages += message }
        override fun close() = Unit
        override fun read(player: Player) = Unit
        override fun flush() = Unit
        override fun flushHighPriority() = Unit
        override fun unregister(service: Any, player: Player) = Unit
    }

    companion object {
        @JvmStatic @BeforeAll fun loadCache() { CompanionPetTest.cache() }

        @JvmStatic @AfterAll fun restore() { CompanionPetTest.restore() }
    }
}
