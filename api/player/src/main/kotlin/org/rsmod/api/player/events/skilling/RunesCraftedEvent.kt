package org.rsmod.api.player.events.skilling

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player

/** Successful altar output; multiplier excludes outfit, extract and other bonus runes. */
public data class RunesCraftedEvent(
    public val player: Player,
    public val rune: String,
    public val essenceConsumed: Int,
    public val baseMultiplier: Int,
    public val ourania: Boolean,
) : UnboundEvent
