package org.rsmod.content.other.npc.animations

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onNpcHit
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class NpcAnimationSpawnScript @Inject constructor(private val spawns: NpcAnimationSpawns) : PluginScript() {
    override fun ScriptContext.startup() {
        for (symbol in NpcAnimationCombatProfiles.profiles.keys) {
            onNpcHit(checkNotNull(ServerCacheManager.getNpc(symbol.asRSCM()))) { spawns.engage(npc) }
        }
        onEvent<NpcStateEvents.Create> { spawns.enqueue(npc) }
        onEvent<NpcStateEvents.Respawn> { spawns.enqueue(npc) }
        onEvent<NpcStateEvents.Delete> { spawns.remove(npc) }
        onEvent<GameLifecycle.LateCycle> { spawns.tick() }
        onEvent<GameLifecycle.Shutdown> { spawns.clear() }
    }
}
