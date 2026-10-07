package org.rsmod.api.player.events.skilling

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player

/** Published after an NPC pickpocket's loot transaction commits and XP is awarded. */
public data class PickpocketSuccessEvent(
    public val player: Player,
    public val npcType: Int,
    public val group: Group,
) : UnboundEvent {
    public enum class Group { Other, Elf }
}
