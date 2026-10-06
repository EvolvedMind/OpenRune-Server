package org.rsmod.api.player.events

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player

public class PlayerDigEvent(public val player: Player, public var handled: Boolean = false) : UnboundEvent
