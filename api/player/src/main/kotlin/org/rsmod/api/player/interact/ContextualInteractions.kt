package org.rsmod.api.player.interact

import jakarta.inject.Singleton
import org.rsmod.api.player.events.interact.OpEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.loc.BoundLocInfo

/** Active player-specific interactions take precedence only when their selector matches. */
@Singleton
public class ContextualInteractions {
    private val npcSelectors = linkedMapOf<String, (Player, Npc, InteractionOp) -> OpEvent?>()
    private val locSelectors = linkedMapOf<String, (Player, BoundLocInfo, InteractionOp) -> OpEvent?>()
    public fun npc(key: String, select: (Player, Npc, InteractionOp) -> OpEvent?) { check(npcSelectors.putIfAbsent(key, select) == null) }
    public fun loc(key: String, select: (Player, BoundLocInfo, InteractionOp) -> OpEvent?) { check(locSelectors.putIfAbsent(key, select) == null) }
    public fun unregister(key: String) { npcSelectors.remove(key); locSelectors.remove(key) }
    public fun npc(player: Player, npc: Npc, op: InteractionOp): OpEvent? = npcSelectors.values.firstNotNullOfOrNull { it(player, npc, op) }
    public fun loc(player: Player, loc: BoundLocInfo, op: InteractionOp): OpEvent? = locSelectors.values.firstNotNullOfOrNull { it(player, loc, op) }
}
