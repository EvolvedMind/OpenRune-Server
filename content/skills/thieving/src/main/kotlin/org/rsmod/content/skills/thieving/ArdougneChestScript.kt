package org.rsmod.content.skills.thieving

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.events.skilling.SkillingActionCompleteEvent
import org.rsmod.api.player.events.skilling.SkillingActionContext
import org.rsmod.api.player.events.skilling.SkillingProductSource
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Scoped adaptation of upstream #216's trapped chests; loot is committed before restocking. */
class ArdougneChestScript @Inject constructor(private val locs: LocRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        for (chest in listOf(CHEST, "loc.trapchest5")) {
        onOpLoc1(chest) {
            arriveDelay(); faceLoc(it.loc)
            if (!available(it.loc)) return@onOpLoc1
            mes("You have activated a trap on the chest.")
            queueHit(delay = 1, type = HitType.Typeless, damage = (player.hitpoints / 8).coerceAtLeast(1))
        }
        onOpLoc2(chest) {
            val loc = it.loc
            arriveDelay(); faceLoc(loc)
            if (!available(loc) || stat("stat.thieving") < 72) { mes("You need level 72 Thieving to disarm this chest."); return@onOpLoc2 }
            val start = coords
            anim("seq.human_pickuptable"); delay(3)
            if (coords != start || !available(loc) || stat("stat.thieving") < 72) return@onOpLoc2
            if (!player.invTransaction(inv) {
                val target = select(inv)
                add(target, "obj.coins".asRSCM(), 1000)
                add(target, "obj.raw_shark".asRSCM(), 1)
                add(target, "obj.adamantite_ore".asRSCM(), 1)
                add(target, "obj.uncut_sapphire".asRSCM(), 1)
            }.success) { mes("You don't have enough inventory space."); return@onOpLoc2 }
            locs.change(loc, "loc.emptypickchest", 250)
            statAdvance("stat.thieving", 500.0)
            publish(SkillingActionCompleteEvent(player, SkillingActionContext.Product("stat.thieving", "obj.raw_shark", 1, 500.0, SkillingProductSource.ThievingChest(CHEST, loc.coords))))
            teleport(CoordGrid(2680, 3273, 0), TeleportType.Exempt)
            mes("You disarm the trap and steal the treasure. A second trap teleports you away.")
        }
        }
        onOpLoc2("loc.emptypickchest") { mes("This chest has already been looted.") }
    }
    private fun ProtectedAccess.available(loc: BoundLocInfo): Boolean =
        loc.coords.level == 1 && loc.coords.x in 2570..2590 && loc.coords.z in 3285..3315 && locs.findExact(loc.coords, dev.openrune.ServerCacheManager.getObject(loc.id)!!) != null
    companion object { const val CHEST = "loc.pickchest3" }
}
