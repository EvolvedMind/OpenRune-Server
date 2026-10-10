package org.rsmod.content.other.npc.animations

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.NpcServerType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.IdentityHashMap
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc

@Singleton
internal class NpcAnimationSpawns @Inject constructor(private val clock: MapClock) {
    private data class Part(val type: NpcServerType, val sequence: String, val ticks: Int)
    private data class Profile(val parts: List<Part>, val active: NpcServerType)
    private data class State(
        val profile: Profile, var nextCycle: Int, var part: Int = 0,
        val movementLocked: Boolean, val ignoreCombat: Boolean,
        var expectedType: Int,
        val originalActionDelay: Int, val transitionActionDelay: Int,
    )
    private val profiles = NpcAnimationSpawnProfiles.profiles.mapKeys { it.key.asRSCM() }.mapValues { (_, p) ->
        Profile(p.parts.map { part ->
            val sequence = checkNotNull(ServerCacheManager.getAnim(part.sequence.asRSCM()))
            check(sequence.tickDuration > 0)
            Part(checkNotNull(ServerCacheManager.getNpc(part.npc.asRSCM())), part.sequence, sequence.tickDuration)
        }, checkNotNull(ServerCacheManager.getNpc(p.active.asRSCM())))
    }
    private val combatProfiles = NpcAnimationCombatProfiles.profiles.mapKeys { it.key.asRSCM() }.mapValues { (_, p) ->
        Profile(p.parts.map { part ->
            val sequence = checkNotNull(ServerCacheManager.getAnim(part.sequence.asRSCM()))
            check(sequence.tickDuration > 0)
            Part(checkNotNull(ServerCacheManager.getNpc(part.npc.asRSCM())), part.sequence, sequence.tickDuration)
        }, checkNotNull(ServerCacheManager.getNpc(p.active.asRSCM())))
    }
    private val states = IdentityHashMap<Npc, State>()

    fun enqueue(npc: Npc) {
        val profile = profiles[npc.id] ?: return
        enqueue(npc, profile)
    }

    fun engage(npc: Npc) {
        val profile = combatProfiles[npc.visType.id] ?: return
        if (npc in states || npc.hitpoints <= 0) return
        enqueue(npc, profile)
    }

    private fun enqueue(npc: Npc, profile: Profile) {
        remove(npc)
        // ignoreCombatInteractions gates incoming interactions, not NvP attack
        // execution. Hold its real attack cooldown until after the final form.
        val attackDelay = maxOf(npc.actionDelay, clock.cycle + 2 + profile.parts.sumOf { it.ticks })
        states[npc] = State(profile, clock.cycle + 1, movementLocked = npc.movementLocked,
            ignoreCombat = npc.ignoreCombatInteractions, expectedType = npc.visType.id,
            originalActionDelay = npc.actionDelay, transitionActionDelay = attackDelay)
        npc.actionDelay = attackDelay
        npc.movementLocked = true
        npc.ignoreCombatInteractions = true
    }

    fun tick() {
        val iterator = states.entries.iterator()
        while (iterator.hasNext()) {
            val (npc, state) = iterator.next()
            if (!npc.isSlotAssigned || npc.hidden || npc.hitpoints <= 0 || npc.visType.id != state.expectedType) {
                restore(npc, state)
                iterator.remove()
                continue
            }
            if (clock.cycle < state.nextCycle) continue
            if (state.part == state.profile.parts.size) {
                npc.transmog(state.profile.active, Int.MAX_VALUE)
                restore(npc, state, cancelled = false)
                iterator.remove()
                continue
            }
            val part = state.profile.parts[state.part++]
            npc.transmog(part.type, Int.MAX_VALUE)
            state.expectedType = part.type.id
            npc.anim(part.sequence)
            state.nextCycle = clock.cycle + part.ticks
        }
    }

    fun remove(npc: Npc) { states.remove(npc)?.let { restore(npc, it) } }
    fun clear() { for ((npc, state) in states) restore(npc, state); states.clear() }
    private fun restore(npc: Npc, state: State, cancelled: Boolean = true) {
        npc.movementLocked = state.movementLocked
        npc.ignoreCombatInteractions = state.ignoreCombat
        if (cancelled && npc.actionDelay == state.transitionActionDelay) {
            npc.actionDelay = state.originalActionDelay
        }
    }
}
