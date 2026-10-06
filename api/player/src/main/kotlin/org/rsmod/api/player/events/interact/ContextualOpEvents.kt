package org.rsmod.api.player.events.interact

import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.BoundLocInfo

public class ContextualNpcOp(public val npc: Npc, key: Long) : OpEvent(key)
public class ContextualLocOp(public val loc: BoundLocInfo, key: Long) : OpEvent(key)
