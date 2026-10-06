package org.rsmod.api.player.events.skilling

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player

/** A ground log has successfully become a fire, after the lighting attempt succeeds. */
public data class LogBurnedEvent(public val player: Player, public val log: String) : UnboundEvent
