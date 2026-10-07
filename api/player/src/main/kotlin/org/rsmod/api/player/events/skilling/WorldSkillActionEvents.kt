package org.rsmod.api.player.events.skilling

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/** Successful world actions, after their inventory transaction, object update and XP award. */
public data class LampRepairedEvent(public val player: Player, public val coords: CoordGrid) : UnboundEvent

public data class AgilityLapCompletedEvent(public val player: Player, public val course: String, public val fullGraceful: Boolean) : UnboundEvent

public data class SkullballGoalScoredEvent(public val player: Player, public val goal: Int) : UnboundEvent

public data class OwnedSpiritTreeTravelEvent(public val player: Player, public val destination: CoordGrid) : UnboundEvent

public data class ReanimatedAbyssalKilledEvent(public val player: Player) : UnboundEvent

public data class ShadeCrematedEvent(
    public val player: Player,
    public val remains: Int,
    public val logs: Int,
    public val coords: CoordGrid,
) : UnboundEvent
