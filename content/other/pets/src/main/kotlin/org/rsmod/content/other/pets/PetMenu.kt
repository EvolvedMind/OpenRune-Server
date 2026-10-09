package org.rsmod.content.other.pets

import dev.openrune.rscm.RSCM.asRSCM
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.invtx.*
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.content.other.pets.cats.Cats
import org.rsmod.content.other.pets.dogs.Dogs
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

@Singleton
class PetMenu @Inject constructor(private val rewards: PetRewards) : PluginScript() {
    private data class Paint(val slot: Int, val original: InvObj)
    private data class View(var query: String = "", var page: Int = 0, val paint: Paint? = null)
    private val views = mutableMapOf<Player, View>()

    override fun ScriptContext.startup() {
        onIfClose(INTERFACE) { views.remove(player) }
        onPlayerLogout { views.remove(player) }
        onIfModalButton(comp("search")) {
            if (!allowed()) return@onIfModalButton
            val view = views[player] ?: return@onIfModalButton
            if (view.paint != null) return@onIfModalButton
            view.query = stringDialog("Find a pet by name or category:").trim().take(60)
            view.page = 0
            views[player] = view
            render(view)
        }
        onIfModalButton(comp("clear")) { if (allowed()) views[player]?.takeIf { it.paint == null }?.let { it.query = ""; it.page = 0; render(it) } }
        onIfModalButton(comp("previous")) { if (allowed()) views[player]?.let { it.page--; render(it) } }
        onIfModalButton(comp("next")) { if (allowed()) views[player]?.let { it.page++; render(it) } }
        repeat(PAGE_SIZE) { index ->
            for (part in listOf("pet", "icon")) onIfModalButton(comp("${part}_$index")) {
                if (!allowed()) return@onIfModalButton
                val view = views[player] ?: return@onIfModalButton
                val pet = choices(view).getOrNull(view.page * PAGE_SIZE + index) ?: return@onIfModalButton
                ifClose()
                if (view.paint != null) paint(player, view.paint, pet.obj)
                else give(player, pet.obj)
            }
        }
    }

    internal fun openPaint(access: ProtectedAccess, slot: Int, original: InvObj) = with(access) {
        if (inv[slot] !== original || CompanionPets.archibald.none { it.objId == original.id }) return@with
        ifClose()
        ifOpenMainModal(INTERFACE)
        val view = View(paint = Paint(slot, original))
        views[player] = view
        render(view)
    }

    fun give(player: Player, obj: String): Boolean {
        val pet = Pets.forObj(obj)
        if (pet != null) return rewards.give(player, obj)
        if (entries.none { it.obj == obj }) return false
        if (!player.invAdd(player.inv, obj, 1).success) player.mes("Make an inventory space for your companion.")
        return true
    }

    private fun paint(player: Player, target: Paint, obj: String) {
        if (player.inv[target.slot] !== target.original || CompanionPets.archibald.none { it.obj == obj }) return
        if (player.invTransaction(player.inv) {
            val inventory = select(player.inv)
            delete(inventory, target.original.id, 1, target.slot)
            add(inventory, obj.asRSCM(), 1, target.original.vars, target.slot)
        }.success) player.mes("You paint Archibald's new pattern.")
    }

    private fun choices(view: View): List<Choice> = if (view.paint != null)
        CompanionPets.archibald.map { Choice(it.name, it.obj, "Paint pattern") }
    else matches(view.query)

    fun open(access: ProtectedAccess) = with(access) {
        if (!player.modLevel.isAtLeast(Rights.ADMINISTRATOR)) { ifClose(); return@with }
        ifClose()
        ifOpenMainModal(INTERFACE)
        val view = View()
        views[player] = view
        render(view)
    }

    private fun ProtectedAccess.allowed(): Boolean {
        views[player]?.paint?.let { target ->
            if (inv[target.slot] === target.original) return true
        }
        if (player.modLevel.isAtLeast(Rights.ADMINISTRATOR)) return true
        ifClose()
        return false
    }

    private fun ProtectedAccess.render(view: View) {
        val pets = choices(view)
        val last = (pets.size - 1).coerceAtLeast(0) / PAGE_SIZE
        view.page = view.page.coerceIn(0, last)
        ifSetText(comp("query"), if (view.paint != null) "Paint Archibald: choose a pattern" else "Search: ${safe(view.query).ifEmpty { "all pets" }}")
        ifSetText(comp("page"), "Page ${view.page + 1}/${last + 1}")
        ifSetText(comp("status"), if (view.paint != null) "Select a pattern to paint your Archibald" else if (pets.isEmpty()) "No pets found" else "${pets.size} pets - select a pet to obtain it")
        repeat(PAGE_SIZE) { index ->
            val pet = pets.getOrNull(view.page * PAGE_SIZE + index)
            ifSetHide(comp("icon_$index"), pet == null)
            if (pet != null) ifSetObj(comp("icon_$index"), pet.obj, 1)
            ifSetText(comp("pet_$index"), pet?.name?.let(::safe).orEmpty())
            ifSetText(comp("category_$index"), pet?.category?.let(::safe).orEmpty())
        }
    }

    companion object {
        internal data class Choice(val name: String, val obj: String, val category: String)
        internal val entries: List<Choice> by lazy {
            Pets.all.map { Choice(it.name, it.base.obj, it.category.name) } +
                Cats.all.map { Choice(if (it.isHell) "${it.stage.name} (Hellcat)" else "${it.stage.name} (${it.colour.name})", it.obj, if (it.isHell) "Hellcats" else "Cats") } +
                Dogs.all.map { Choice("${it.breed.name}${if (it.puppy) " puppy" else ""} (${it.colour})", it.obj, "Dogs") } +
                CompanionPets.all.map { Choice(it.name, it.obj, "Other companions") }
        }
        private const val INTERFACE = "interface.pet_menu"
        private const val PAGE_SIZE = 18
        private fun comp(name: String) = "component.pet_menu:$name"
        private fun safe(text: String) = text.replace(Regex("[<>|]"), "")
        internal fun matches(query: String): List<Choice> = entries.filter {
            query.isBlank() || it.name.contains(query, ignoreCase = true) || it.category.contains(query, ignoreCase = true)
        }.sortedBy { it.name }
    }
}
