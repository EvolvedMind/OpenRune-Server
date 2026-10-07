package org.rsmod.content.skills.farming

import jakarta.inject.Inject

/** Wall-clock minutes allow saved crops to grow while the player is offline. */
open class FarmingClock @Inject constructor() {
    open fun minute(): Int = (System.currentTimeMillis() / 60_000L).toInt()
}
