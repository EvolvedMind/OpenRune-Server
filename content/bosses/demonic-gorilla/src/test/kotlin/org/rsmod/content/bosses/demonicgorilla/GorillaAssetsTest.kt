package org.rsmod.content.bosses.demonicgorilla

import dev.openrune.ServerCacheManager
import dev.openrune.definition.codec.NPCCodec
import dev.openrune.definition.codec.ObjectCodec
import dev.openrune.filesystem.Cache
import dev.openrune.rscm.RSCM.asRSCM
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.config.refs.params

@ResourceLock("ServerCacheManager")
internal class GorillaAssetsTest {
    @Test fun `native Tortured appearance preserves compatible idle walk size and death animation`() {
        ServerCacheManager.init(240).close()
        val live = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            val codec = NPCCodec(240)
            val demonic = codec.loadData("npc.mm2_demon_gorilla_1_melee".asRSCM(), live.data(2, 9, "npc.mm2_demon_gorilla_1_melee".asRSCM())!!)
            for (symbol in listOf("npc.mm2_tortured_gorilla_1", "npc.mm2_tortured_gorilla_2")) {
                val id = symbol.asRSCM(); val npc = codec.loadData(id, live.data(2, 9, id)!!)
                assertFalse(npc.models.isNullOrEmpty()); assertEquals(demonic.standAnim, npc.standAnim)
                assertEquals(demonic.walkAnim, npc.walkAnim); assertEquals(demonic.size, npc.size)
                val server = ServerCacheManager.getNpc(id)!!
                assertEquals(210, server.hitpoints)
                assertEquals("seq.demonic_gorilla_death".asRSCM(), server.param(params.death_anim).id)
            }
            val objectCodec = ObjectCodec(240)
            for ((symbol, native, operation) in listOf(
                Triple(GorillaAccessScript.ROPE, "loc.climbing_rope2", "Climb-up"),
                Triple(GorillaAccessScript.HOLE, "loc.yanilleholein", "Climb-down"),
                Triple(GorillaAccessScript.SIGN, "loc.dangersign", "Read"),
            )) {
                val id = symbol.asRSCM(); val original = native.asRSCM()
                val definition = objectCodec.loadData(id, live.data(2, 6, id)!!)
                val base = objectCodec.loadData(original, live.data(2, 6, original)!!)
                assertEquals(base.objectModels, definition.objectModels)
                assertEquals(operation, definition.actions.getOpOrNull(0))
                assertEquals(operation, ServerCacheManager.getObject(id)!!.actions.getOpOrNull(0))
            }
            assertEquals(0, ServerCacheManager.getObject(GorillaAccessScript.ROPE.asRSCM())!!.blockWalk)
            assertEquals(0, ServerCacheManager.getObject(GorillaAccessScript.HOLE.asRSCM())!!.blockWalk)
        } finally { live.close() }
    }
}
