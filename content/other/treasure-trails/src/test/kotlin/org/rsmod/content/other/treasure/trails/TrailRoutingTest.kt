package org.rsmod.content.other.treasure.trails

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.rsmod.api.player.events.interact.ContextualLocOp
import org.rsmod.api.player.events.interact.ContextualNpcOp
import org.rsmod.api.player.interact.ContextualInteractions
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.loc.BoundLocInfo

class TrailRoutingTest {
    @Test fun `contextual NPC handler only matches its owner target and operation`() {
        val routing = ContextualInteractions()
        val owner = Player()
        val target = mock(Npc::class.java)
        val event = ContextualNpcOp(target, 23L)
        routing.npc("test") { player, npc, op ->
            if (player === owner && npc === target && op == InteractionOp.Op1) event else null
        }
        assertSame(event, routing.npc(owner, target, InteractionOp.Op1))
        assertNull(routing.npc(Player(), target, InteractionOp.Op1))
        assertNull(routing.npc(owner, mock(Npc::class.java), InteractionOp.Op1))
        assertNull(routing.npc(owner, target, InteractionOp.Op2))
        routing.unregister("test")
        assertNull(routing.npc(owner, target, InteractionOp.Op1))
        routing.npc("test") { _, _, _ -> event }
        assertSame(event, routing.npc(owner, target, InteractionOp.Op1))
    }

    @Test fun `unmatched loc routes remain available to existing handlers after unload`() {
        val routing = ContextualInteractions()
        val owner = Player()
        val target = mock(BoundLocInfo::class.java)
        val event = ContextualLocOp(target, 24L)
        routing.loc("test") { player, loc, op ->
            if (player === owner && loc === target && op == InteractionOp.Op1) event else null
        }
        assertSame(event, routing.loc(owner, target, InteractionOp.Op1))
        assertNull(routing.loc(Player(), target, InteractionOp.Op1))
        assertNull(routing.loc(owner, target, InteractionOp.Op2))
        routing.unregister("test")
        assertNull(routing.loc(owner, target, InteractionOp.Op1))
    }
}
