package org.rsmod.api.player.events.skilling

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player

public data class PrayerActivatedEvent(public val player: Player, public val prayer: String) : UnboundEvent
