package org.rsmod.content.other.commands

import jakarta.inject.Inject
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.other.pets.PetFollowers
import org.rsmod.content.other.pets.PetForm
import org.rsmod.content.other.pets.PetInsurance
import org.rsmod.content.other.pets.Pets
import org.rsmod.content.other.pets.cats.Cats
import org.rsmod.content.other.pets.dogs.Dogs
import org.rsmod.game.cheat.Cheat
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PersonalCommands
@Inject
constructor(
    private val protectedAccess: ProtectedAccessLauncher,
    private val followers: PetFollowers,
    private val insurance: PetInsurance,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("zulrah", "Teleport to the Zul-Andra boat", ::zulrah)
        onCommand("pets", "List pet names: ::pets [page]", ::pets)
        onCommand("pet", "Spawn a follower: ::pet key [form] or ::pet item_symbol", ::pet)
        onCommand("allpets", "Give pets to your bank: ::allpets [forms]", ::allPets)
        onCommand("petcall", "Call your current follower", ::petCall)
        onCommand("petpickup", "Put your current follower in your inventory", ::petPickup)
    }

    private fun zulrah(cheat: Cheat) {
        protectedAccess.launch(cheat.player) {
            telejump(CoordGrid(2196, 3056, 0), TeleportType.Exempt)
            mes("Board the boat to enter your Zulrah instance.")
        }
    }

    private fun pets(cheat: Cheat) = with(cheat) {
        val entries = Pets.all.sortedBy { it.key }
        val pages = (entries.size + PAGE_SIZE - 1) / PAGE_SIZE
        val page = args.firstOrNull()?.toIntOrNull() ?: 1
        if (page !in 1..pages) {
            player.mes("Use ::pets 1 through $pages.")
            return
        }
        player.mes("Pets $page/$pages. Use ::pet key [form-number], starting at 1.")
        for (entry in entries.drop((page - 1) * PAGE_SIZE).take(PAGE_SIZE)) {
            player.mes("${entry.key}: ${entry.name} (${entry.forms.size} forms)")
        }
        player.mes("Cats/dogs: use ::pet item_symbol, matching the native item name.")
    }

    private fun pet(cheat: Cheat): Unit = with(cheat) {
        if (args.isEmpty()) {
            player.mes("Use ::pets to list keys, then ::pet key [form-number].")
            return
        }
        val numbered = args.size > 1 && args.last().toIntOrNull() != null
        val name = (if (numbered) args.dropLast(1) else args).joinToString(" ")
        val number = if (numbered) args.last().toIntOrNull()!! else 1
        val entry = Pets.all.firstOrNull {
            petNameKey(it.key) == petNameKey(name) || petNameKey(it.name) == petNameKey(name)
        }
        val symbol = "obj." + name.removePrefix("obj.")
        val directForm = allForms().firstOrNull { it.obj == symbol }
        val form = if (entry != null) entry.forms.getOrNull(number - 1) else directForm
        if (form == null || (entry == null && numbered && number != 1)) {
            player.mes("Unknown pet or form. Use ::pets; form numbers start at 1.")
            return
        }
        protectedAccess.launch(player) {
            val previous = followers.followerForm(player)
            if (previous != null) {
                val stored = player.invAdd(player.invMap.getOrPut("inv.bank"), previous.objId, 1)
                if (stored.completed() != 1) {
                    mes("Your bank is full. Your current follower has been kept.")
                    return@launch
                }
            }
            form.unlock?.let { VarPlayerIntMapSetter.set(player, it, 1) }
            Dogs.forObj(form.objId)?.breed?.unlock?.let {
                VarPlayerIntMapSetter.set(player, it, 1)
            }
            Pets.forObj(form.objId)?.first?.let { insurance.insure(player, it) }
            followers.spawn(player, form)
            mes("Following: ${form.obj}. Previous follower, if any, is in your bank.")
        }
    }

    private fun allPets(cheat: Cheat) = with(cheat) {
        if (args.size > 1 || (args.isNotEmpty() && args[0] != "forms")) {
            player.mes("Use ::allpets or ::allpets forms.")
            return
        }
        val forms = if (args.isEmpty()) {
            Pets.all.map { it.base } + Cats.all.map { it.form } + Dogs.all.map { it.form }
        } else {
            allForms()
        }
        val bank = player.invMap.getOrPut("inv.bank")
        var added = 0
        var missing = 0
        for (form in forms.distinctBy { it.objId }) {
            val held = form.obj in bank || form.obj in player.inv ||
                followers.followerForm(player)?.objId == form.objId
            if (!held) {
                val result = player.invAdd(bank, form.objId, 1)
                if (result.completed() == 1) added++ else missing++
            }
            if (held || form.obj in bank) {
                Pets.forObj(form.objId)?.first?.let { insurance.insure(player, it) }
            }
        }
        player.mes("Added $added pets to your bank; $missing could not fit. Use ::openbank.")
    }

    private fun petCall(cheat: Cheat) {
        cheat.player.mes(if (followers.call(cheat.player)) "Pet called." else "You have no active follower.")
    }

    private fun petPickup(cheat: Cheat) {
        protectedAccess.launch(cheat.player) {
            val form = followers.followerForm(player)
            if (form == null) {
                mes("You have no follower.")
                return@launch
            }
            if (player.invAdd(player.inv, form.objId, 1).completed() != 1) {
                mes("Make room in your inventory first.")
                return@launch
            }
            followers.dismiss(player)
            mes("Your pet is in your inventory.")
        }
    }

    private fun allForms(): List<PetForm> =
        Pets.all.flatMap { it.forms } + Cats.all.map { it.form } + Dogs.all.map { it.form }

    private companion object {
        const val PAGE_SIZE = 8
    }
}

internal fun petNameKey(name: String): String =
    name.lowercase().filter { it.isLetterOrDigit() }
