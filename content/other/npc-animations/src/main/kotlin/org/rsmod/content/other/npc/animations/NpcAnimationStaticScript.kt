package org.rsmod.content.other.npc.animations

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.config.refs.params
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.script.onNpcQueue
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class NpcAnimationStaticScript @Inject constructor(private val deaths: NpcDeath) : PluginScript() {
    override fun ScriptContext.startup() {
        for (symbol in NpcAnimationStaticProfiles.targets) {
            val type = checkNotNull(ServerCacheManager.getNpc(symbol.asRSCM()))
            onAiOpPlayer2(type) { resetMode() }
            onAiApPlayer2(type) { resetMode() }
            onNpcQueue(type, "queue.death") {
                if (type.paramOrNull(params.death_anim) != null) deaths.deathWithDrops(this)
                else deaths.deathWithAnimations(this, emptyList())
            }
        }
    }
}
