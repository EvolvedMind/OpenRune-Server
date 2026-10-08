package org.rsmod.content.interfaces.prayer.tab.scripts

import dev.openrune.rscm.RSCM.asRSCM
import dev.or2.central.account.Rights
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onOpHeld1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PrayerScrollScript : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("testprayers") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Give the five permanent prayer unlock scrolls without changing unlock state"
            cheat {
                val transaction = player.invTransaction(player.inv) {
                    val inventory = select(player.inv)
                    for (scroll in PrayerScroll.entries) add(inventory, scroll.item.asRSCM(), 1, strict = true)
                }
                player.mes(if (transaction.success) "Added all five prayer scrolls." else "You need five free inventory slots.")
            }
        }
        for (scroll in PrayerScroll.entries) {
            onOpHeld1(scroll.item) { event ->
                if (inv[event.slot] !== event.obj) return@onOpHeld1
                if (vars[scroll.unlock] != 0) {
                    mes("You have already unlocked ${scroll.prayer}.")
                    return@onOpHeld1
                }
                mesbox("Reading this scroll permanently unlocks ${scroll.prayer} and consumes it.")
                val confirmed = choice2("Read", true, "Cancel", false)
                ifCloseChat()
                if (!confirmed) return@onOpHeld1
                if (inv[event.slot] !== event.obj || vars[scroll.unlock] != 0) return@onOpHeld1
                val transaction = player.invTransaction(inv, autoCommit = true) {
                    val inventory = select(inv)
                    delete { from = inventory; obj = event.obj.id; strictCount = 1; strictSlot = event.slot }
                }
                if (transaction.success) {
                    vars[scroll.unlock] = 1
                    mes("You have permanently unlocked ${scroll.prayer}.")
                }
            }
        }
    }
}

internal enum class PrayerScroll(val item: String, val prayer: String, val unlock: String) {
    Rigour("obj.raids_prayerscroll", "Rigour", "varbit.prayer_rigour_unlocked"),
    Augury("obj.raids_prayerscroll_augury", "Augury", "varbit.prayer_augury_unlocked"),
    Preserve("obj.raids_prayerscroll_preserve", "Preserve", "varbit.prayer_preserve_unlocked"),
    Deadeye("obj.deadeye_prayer_scroll", "Deadeye", "varbit.prayer_deadeye_unlocked"),
    MysticVigour("obj.mystic_vigour_prayer_scroll", "Mystic Vigour", "varbit.prayer_mystic_vigour_unlocked"),
}
