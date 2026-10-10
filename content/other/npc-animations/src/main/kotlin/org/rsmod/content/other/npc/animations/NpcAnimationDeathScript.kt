package org.rsmod.content.other.npc.animations

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.death.NpcDeathAnimation
import org.rsmod.api.script.onNpcQueue
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class NpcAnimationDeathScript @Inject constructor(private val deaths: NpcDeath) : PluginScript() {
    override fun ScriptContext.startup() {
        for ((symbol, sequences) in NpcAnimationDeathProfiles.sequences) {
            val type = checkNotNull(ServerCacheManager.getNpc(symbol.asRSCM()))
            for (sequence in sequences) {
                check(checkNotNull(ServerCacheManager.getAnim(sequence.asRSCM())).tickDuration > 0)
            }
            val forms = NpcAnimationDeathProfiles.forms[symbol]
            val parts = sequences.mapIndexed { i, sequence ->
                NpcDeathAnimation(sequence, forms?.get(i)?.let { checkNotNull(ServerCacheManager.getNpc(it.asRSCM())) })
            }
            onNpcQueue(type, "queue.death") { deaths.deathWithAnimationParts(this, parts) }
        }
        // A lethal first hit never enters the living transition. Put the native
        // active body in place before its death; keep identity, drops and respawn.
        for ((symbol, profile) in NpcAnimationCombatProfiles.profiles) {
            val type = checkNotNull(ServerCacheManager.getNpc(symbol.asRSCM()))
            val active = checkNotNull(ServerCacheManager.getNpc(profile.active.asRSCM()))
            onNpcQueue(type, "queue.death") {
                npc.transmog(active, Int.MAX_VALUE)
                deaths.deathWithAnimations(this, profile.death)
            }
        }
    }
}
