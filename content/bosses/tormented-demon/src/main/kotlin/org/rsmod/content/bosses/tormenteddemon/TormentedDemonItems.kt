package org.rsmod.content.bosses.tormenteddemon

import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj

internal var Player.tdCraftingState by intVarp("varp.td_crafting_state")

internal object ArclightState {
    const val CAPACITY = 10000
    private const val MASK = 16383
    fun charges(item: InvObj): Int = item.vars and MASK
    fun infusion(item: InvObj): Int = (item.vars ushr 14) and MASK
    fun pack(charges: Int, infusion: Int): Int = charges.coerceIn(0, CAPACITY) or (infusion.coerceIn(0, CAPACITY) shl 14)
    fun ready(item: InvObj): Boolean = charges(item) + infusion(item) >= CAPACITY
}
