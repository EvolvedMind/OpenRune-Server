package org.rsmod.content.bosses.tormenteddemon.pack

import dev.openrune.cache.tools.tasks.CacheTask
import dev.openrune.definition.codec.NPCCodec
import dev.openrune.filesystem.Cache
import dev.openrune.pack.PluginPack
import dev.openrune.rscm.RSCM.asRSCM
import io.netty.buffer.Unpooled

class TormentedDemonPluginPack : PluginPack() {
    override fun extraTasks(): List<CacheTask> = listOf(TormentedDemonModels())
}

private class TormentedDemonModels : CacheTask() {
    override fun init(cache: Cache) {
        val codec = NPCCodec(revision)
        for ((base, variant, model) in listOf(
            Triple("npc.tormented_demon_1", "npc.tormented_demon_defenceless_1", 55475),
            Triple("npc.tormented_demon_2", "npc.tormented_demon_defenceless_2", 55474),
        )) {
            val original = codec.loadData(base.asRSCM(), checkNotNull(cache.data(2, 9, base.asRSCM())))
            val id = variant.asRSCM()
            val definition = original.copy(id = id, models = listOf(model))
            val buffer = Unpooled.buffer()
            try {
                with(codec) { buffer.encode(definition) }
                val data = ByteArray(buffer.readableBytes())
                buffer.readBytes(data)
                cache.write(2, 9, id, data)
            } finally { buffer.release() }
        }
    }
}
