package org.rsmod.api.player.events.skilling

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/** Native planting only, after seeds, persistent patch state and XP have committed. */
public data class FarmingSeedPlantedEvent(
    public val player: Player,
    public val seed: Int,
    public val patch: Int,
    public val coords: CoordGrid,
) : UnboundEvent
