package org.rsmod.content.other.pets

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.IdentityHashMap
import org.rsmod.api.invtx.*
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.api.player.interact.HeldInteractions
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.*
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

@Singleton
internal class CompanionPetScript @Inject constructor(
    private val followers: PetFollowers,
    private val menu: PetMenu,
    private val held: HeldInteractions,
) : PluginScript(), PlayerPostTickHook {
    private class ChairReturn(val cycle: Int, var warned: Boolean = false)
    private val chairs = IdentityHashMap<Player, ChairReturn>()

    override fun ScriptContext.startup() {
        for (entry in CompanionPets.archibald) {
            onOpHeld1(entry.obj) { interact(entry, it.slot, it.obj) }
            onOpHeld3(entry.obj) { menu.openPaint(this, it.slot, it.obj) }
            onOpHeld4(entry.obj) { if (inv[it.slot] === it.obj) held.drop(this, it.inventory, it.slot) }
        }
        for (entry in listOf(CompanionPets.rock, CompanionPets.egg)) {
            onOpHeld1(entry.obj) { interact(entry, it.slot, it.obj) }
        }
        for (entry in CompanionPets.fish) {
            onOpHeld1(entry.obj) { mes("You talk to your pet fish. It answers with bubbles.") }
            onOpHeld2(entry.obj) { mes("You play with your pet fish. It swims excitedly around its bowl.") }
            onOpHeld3(entry.obj) { feed(entry, it.slot, it.obj) }
        }
        onOpHeld1(CompanionPets.mayor.obj) { mes("You consult the Mayor of Catherby. The mayor responds with a thoughtful bubble.") }
        onOpHeld2(CompanionPets.mayor.obj) { feed(CompanionPets.mayor, it.slot, it.obj) }
        for (entry in CompanionPets.fish + CompanionPets.mayor) {
            onOpHeldU("obj.fish_food", entry.obj) { feed(entry, it.secondSlot, inv[it.secondSlot] ?: return@onOpHeldU) }
        }
        onOpHeld4(CompanionPets.broav.obj) { release(CompanionPets.broav, it.slot, it.obj) }
        onOpHeld5(CompanionPets.toyCat.obj) { release(CompanionPets.toyCat, it.slot, it.obj) }
        onOpHeld1(CompanionPets.chair.obj) { release(CompanionPets.chair, it.slot, it.obj) }
        onOpHeld2(CompanionPets.chair.obj) {
            if (release(CompanionPets.chair, it.slot, it.obj)) followers.follower(player)?.anim("seq.draynor_poltergeist_chair")
        }
        for (entry in CompanionPets.all.filter { it.form != null }) {
            onPetOp(entry.npc!!, "Pick-up") { pickup(it, entry) }
        }
        onPetOp(CompanionPets.toyCat.npc!!, "Shoo") {
            if (!followers.requireOwned(this, it)) return@onPetOp
            if (choice2("Release the toy cat", true, "Keep it", false) && followers.isFollowerOf(it, player)) {
                followers.dismiss(player)
                mes("You let the toy cat go.")
            }
        }
        onPlayerLogout { returnChair(player); chairs.remove(player) }
    }

    private suspend fun ProtectedAccess.interact(entry: CompanionPets.Entry, slot: Int, original: InvObj) {
        val option = choice3("Talk", 1, "Stay", 2, "Fetch", 3, title = entry.name)
        if (inv[slot] !== original) return
        when (option) {
            1 -> mes("You talk to ${entry.name}. It seems happy to listen.")
            2 -> { anim("seq.petrock_human_stay"); mes("${entry.name} stays exactly where it is.") }
            3 -> { anim("seq.petrock_human_stick"); mes("${entry.name} leaves fetching the stick to you.") }
        }
    }

    private fun ProtectedAccess.feed(entry: CompanionPets.Entry, slot: Int, original: InvObj) {
        if (inv[slot] !== original) return
        val foodSlot = inv.objs.indexOfFirst { it?.id == "obj.fish_food".asRSCM() }
        if (foodSlot < 0) { mes("You need fish food to feed ${entry.name}."); return }
        if (player.invTransaction(inv) {
            val inventory = select(inv)
            delete(inventory, "obj.fish_food".asRSCM(), 1, foodSlot)
            add(inventory, "obj.empty_fishfood_box".asRSCM(), 1, slot = foodSlot)
        }.success) mes(if (entry == CompanionPets.mayor) "The mayor accepts your generous donation to his campaign." else "You feed your pet fish.")
    }

    private fun ProtectedAccess.release(entry: CompanionPets.Entry, slot: Int, original: InvObj): Boolean {
        if (inv[slot] !== original) return false
        if (followers.hasFollower(player)) { mes("You already have a follower."); return false }
        if (!player.invDel(inv, entry.obj, 1, slot = slot).success) return false
        followers.spawn(player, checkNotNull(entry.form))
        if (entry == CompanionPets.chair) chairs[player] = ChairReturn(mapClock + 20)
        return true
    }

    private fun ProtectedAccess.pickup(npc: Npc, entry: CompanionPets.Entry) {
        if (!followers.requireOwned(this, npc)) return
        if (!player.invAdd(inv, entry.obj, 1).success) { mes("Make an inventory space to pick up your companion."); return }
        followers.dismiss(player)
        chairs.remove(player)
    }

    override fun onPostTick(player: Player) {
        if (player.followerObj != CompanionPets.chair.objId) { chairs.remove(player); return }
        if (player.loggingOut) return
        val returning = chairs.getOrPut(player) { ChairReturn(player.currentMapClock + 20) }
        if (player.currentMapClock >= returning.cycle) returnChair(player)
    }

    private fun returnChair(player: Player) {
        if (player.followerObj != CompanionPets.chair.objId) return
        if (player.inv.isFull()) {
            val returning = chairs[player]
            if (returning != null && !returning.warned) {
                returning.warned = true
                player.mes("Make room so your spooky chair can return to your inventory.")
            }
            return
        }
        if (player.invAdd(player.inv, CompanionPets.chair.obj, 1).success) {
            followers.dismiss(player)
            chairs.remove(player)
            player.mes("The spooky chair makes its way back to you.")
        }
    }

    override fun ScriptContext.shutdown() { chairs.keys.toList().forEach(::returnChair); chairs.clear() }
}
