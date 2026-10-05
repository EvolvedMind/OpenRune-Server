package org.rsmod.content.skills.fletching

import dev.openrune.rscm.RSCM.asRSCM
import org.rsmod.api.invtx.*
import org.rsmod.api.player.events.skilling.SkillingActionCompleteEvent
import org.rsmod.api.player.events.skilling.SkillingActionContext
import org.rsmod.api.player.events.skilling.SkillingProductSource
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeldU
import org.rsmod.content.skills.Material
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillMultiEntry
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Initial Fletching recipes needed by Treasure Trails; available even without a clue. */
class ClueFletchingRecipes : PluginScript() {
    override fun ScriptContext.startup() {
        for (recipe in recipes) onOpHeldU(recipe.first, recipe.second) {
            if (stat("stat.fletching") < recipe.level) {
                mes("You need level ${recipe.level} Fletching to make this.")
                return@onOpHeldU
            }
            val entry = SkillMultiEntry(recipe.output, listOf(Material(recipe.first), Material(recipe.second)))
            openSkillMulti(SkillMultiConfig(verb = "make", entries = listOf(entry), maxCountProvider = { inventory, _ ->
                minOf(inventory.count(recipe.first), inventory.count(recipe.second)).let { (it + recipe.batch - 1) / recipe.batch }
            })) { selection ->
                repeat(selection.amount) {
                    if (stat("stat.fletching") < recipe.level || !inv.contains(recipe.first) || !inv.contains(recipe.second)) return@openSkillMulti
                    anim(recipe.animation)
                    delay(recipe.ticks)
                    if (!fletch(recipe)) return@openSkillMulti
                }
                resetAnim()
            }
        }
    }
    companion object {
        val recipes = listOf(
            FletchingRecipe("obj.unstrung_yew_longbow", "obj.bow_string", "obj.yew_longbow", 70, 75.0, 1, 3, "seq.stringing_yew_longbow"),
            FletchingRecipe("obj.rune_dart_tip", "obj.feather", "obj.rune_dart", 81, 18.8, 10, 2, "seq.human_fletching_add_dart_feathers_rune"),
        )
    }
}
data class FletchingRecipe(val first: String, val second: String, val output: String, val level: Int, val xp: Double, val batch: Int, val ticks: Int, val animation: String)

internal fun ProtectedAccess.fletch(recipe: FletchingRecipe): Boolean {
    if (stat("stat.fletching") < recipe.level) return false
    val count = minOf(recipe.batch, inv.count(recipe.first), inv.count(recipe.second))
    if (count <= 0) return false
    val result = player.invTransaction(inv) {
        val inventory = select(inv)
        delete(inventory, recipe.first.asRSCM(), count)
        delete(inventory, recipe.second.asRSCM(), count)
        add(inventory, recipe.output.asRSCM(), count)
    }
    if (!result.success) return false
    statAdvance("stat.fletching", recipe.xp * count)
    publish(SkillingActionCompleteEvent(player, SkillingActionContext.Product("stat.fletching", recipe.output, count, recipe.xp * count, SkillingProductSource.Fletching)))
    return true
}
