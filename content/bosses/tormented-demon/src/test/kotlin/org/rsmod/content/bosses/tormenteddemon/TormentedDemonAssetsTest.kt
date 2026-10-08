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
                assertEquals(1, server.param(org.rsmod.api.config.refs.params.cannon_immunity))
                assertEquals(1065, server.param(org.rsmod.api.config.refs.BaseParams.slayer_experience))
                assertEquals(29, server.param(org.rsmod.api.config.refs.BaseParams.slayer_task_id))
                assertEquals(1, server.param(org.rsmod.api.config.refs.params.elemental_weakness_type))
                assertEquals(30, server.param(org.rsmod.api.config.refs.params.elemental_weakness_percent))
                assertEquals(600, checkNotNull(ServerCacheManager.getNpc(variantId)).hitpoints)
            }
        } finally { live.close() }
        for (name in listOf("seq.luc2_undead_demon_melee", "seq.luc2_undead_demon_spare_ribs", "seq.luc2_undead_demon_firey_balls", "seq.luc2_undead_demon_explosion_fire", "seq.luc2_undead_demon_death")) {
            val animation = checkNotNull(ServerCacheManager.getAnim(name.asRSCM()))
            assertTrue(animation.tickDuration > 0)
        }
    }

    @Test fun `rebuilt native map contains twenty six unique Tormented Demon spawns`() {
        val cache = ServerCacheManager.init(240)
        try {
            val ids = setOf("npc.tormented_demon_1".asRSCM(), "npc.tormented_demon_2".asRSCM())
            val positions = mutableListOf<org.rsmod.map.CoordGrid>()
            for (x in 63..64) for (z in 68..69) {
                val data = cache.data(dev.openrune.cache.MAPS, (x shl 8) or z, 5) ?: continue
                val packed = dev.openrune.map.npc.MapNpcListDecoder.decode(dev.openrune.map.util.InlineByteBuf(data))
                for (entry in packed.packedSpawns) {
                    val npc = dev.openrune.map.npc.MapNpcDefinition(entry)
                    if (npc.id in ids) positions += org.rsmod.map.CoordGrid(x * 64 + npc.localX, z * 64 + npc.localZ, npc.level)
                }
            }
            assertEquals(26, positions.size)
            assertEquals(26, positions.distinct().size)
            assertEquals(mapOf(1 to 10, 2 to 10, 3 to 6), positions.groupingBy(TormentedTemple::limit).eachCount())
        } finally { cache.close() }
    }
}
