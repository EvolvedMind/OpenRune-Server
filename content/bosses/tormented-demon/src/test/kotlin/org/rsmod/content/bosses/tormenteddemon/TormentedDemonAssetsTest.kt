package org.rsmod.content.bosses.tormenteddemon

import dev.openrune.ServerCacheManager
import dev.openrune.definition.codec.NPCCodec
import dev.openrune.filesystem.Cache
import dev.openrune.rscm.RSCM.asRSCM
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock

@ResourceLock("ServerCacheManager")
class TormentedDemonAssetsTest {
    @Test
    fun `native appearance variants preserve animations options and size in revision 240`() {
        ServerCacheManager.init(240).close()
        val live = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            val codec = NPCCodec(240)
            for ((baseName, variantName, model) in listOf(
                Triple("npc.tormented_demon_1", "npc.tormented_demon_defenceless_1", 55475),
                Triple("npc.tormented_demon_2", "npc.tormented_demon_defenceless_2", 55474),
            )) {
                val baseId = baseName.asRSCM()
                val variantId = variantName.asRSCM()
                val base = codec.loadData(baseId, checkNotNull(live.data(2, 9, baseId)))
                val variant = codec.loadData(variantId, checkNotNull(live.data(2, 9, variantId)))
                assertEquals(listOf(model), variant.models)
                assertEquals(base, variant.copy(id = base.id, models = base.models))
                assertNotNull(live.data(7, model), "client model $model")
                val server = checkNotNull(ServerCacheManager.getNpc(baseId))
                assertEquals(3, server.size)
                assertEquals(600, server.hitpoints)
                assertEquals(21, server.respawnRate)
                assertEquals(0, server.huntMode)
                assertEquals(600, checkNotNull(ServerCacheManager.getNpc(variantId)).hitpoints)
            }
        } finally { live.close() }
        for (name in listOf("seq.luc2_undead_demon_melee", "seq.luc2_undead_demon_spare_ribs", "seq.luc2_undead_demon_firey_balls", "seq.luc2_undead_demon_explosion_fire", "seq.luc2_undead_demon_death")) {
            val animation = checkNotNull(ServerCacheManager.getAnim(name.asRSCM()))
            assertTrue(animation.tickDuration > 0)
        }
    }
}
