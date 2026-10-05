package org.rsmod.content.skills.thieving

import org.rsmod.api.player.events.skilling.SkillingActionCompleteEvent
import org.rsmod.api.player.events.skilling.SkillingActionContext
import org.rsmod.api.player.events.skilling.SkillingProductSource
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.map.CoordGrid

/** Called only after the successful theft; failed inventory insertion grants no XP or clue credit. */
internal fun ProtectedAccess.awardStallLoot(stall: Stall, coords: CoordGrid): Pair<Loot, Int>? {
    if (stat("stat.thieving") < stall.level) return null
    val loot = stall.loot.roll(random)
    val count = random.of(loot.min, loot.max)
    if (invAdd(inv, loot.obj, count).failure) return null
    statAdvance("stat.thieving", stall.xp)
    publish(SkillingActionCompleteEvent(player, SkillingActionContext.Product("stat.thieving", loot.obj, count, stall.xp, SkillingProductSource.ThievingStall(stall.loc, coords))))
    return loot to count
}
