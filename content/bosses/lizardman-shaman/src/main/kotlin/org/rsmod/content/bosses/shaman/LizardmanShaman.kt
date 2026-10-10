package org.rsmod.content.bosses.shaman

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.*
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.ProjectileConfig
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.mechanics.toxins.impl.PlayerPoison
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitType
import org.rsmod.game.interact.InteractionOp
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.flag.CollisionFlag

internal class LizardmanShaman @Inject constructor(private val deps: BossDeps, private val ai: AiPlayerInteractions) : PluginScript() {
    private var running = false
    private val jobs = mutableSetOf<Job>()
    internal val spec: BossSpec = boss(*TYPES.toTypedArray()) {
        stats(attackRate = 6, aggressionRadius = 8)
        val melee = ability("melee") {
            anim("seq.shay_lizard_warrior_attack_melee")
            hit { damage(Accuracy(Roll(0..31), meleeAttackType = MeleeAttackType.Crush)); type(Melee); delay = 1 }
        }
        val ranged = ability("ranged") {
            anim("seq.shay_lizard_warrior_attack_ranged")
            projectile {
                spotanim = "spotanim.lizardman_spit"
                config = ProjectileConfig(startHeight = 55, endHeight = 30, startDelay = 30, travelTime = 70)
                hit { damage(Accuracy(Roll(0..21))); type(Ranged) }
            }
        }
        val acid = ability("acid") {
            anim("seq.shay_lizard_warrior_attack_ranged")
            include(external("shaman.acid"))
        }
        val jump = ability("jump") {
            attackDelay = 8
            anim("seq.shayzien_lizard_boss_jump")
            include(external("shaman.jump"))
        }
        val summon = ability("summon") {
            anim("seq.shayzien_lizard_boss_minion_summon")
            include(external("shaman.summon"))
        }
        phase("combat") {
            weightedSelectorRandom(noRepeatBias = 0.0) {
                +random(melee, weight = 4, requires = Condition.WithinMeleeRange)
                +random(ranged, weight = 4)
                +random(acid, weight = 2)
                +random(jump, weight = 1, requires = Condition.Custom { npc, player ->
                    player != null && canLand(npc, landingTile(player))
                })
                +random(summon, weight = 1, cooldown = 18)
            }
        }
    }

    override fun ScriptContext.startup() {
        running = true
        BossCombat.register(this, spec, deps)
        deps.extensionRegistry.register("shaman.acid") { _, npc, player, _ -> acid(npc, player) }
        deps.extensionRegistry.register("shaman.jump") { _, npc, player, _ -> jump(npc, player) }
        deps.extensionRegistry.register("shaman.summon") { _, npc, player, _ -> summon(npc, player) }
        val ids = TYPES.map { it.asRSCM() }.toSet()
        onEvent<NpcStateEvents.Create> { if (npc.type.id in ids) npc.apRangeOverride = 8 }
        onEvent<NpcStateEvents.Respawn> { if (npc.type.id in ids) reset(npc) }
        onEvent<NpcStateEvents.Delete> { if (npc.type.id in ids) reset(npc) }
    }

    override fun ScriptContext.shutdown() {
        running = false
        jobs.toList().forEach(::cancel)
        listOf("shaman.acid", "shaman.jump", "shaman.summon").forEach(deps.extensionRegistry::unregister)
    }

    private class Job(val npc: Npc, val encounter: BossEncounter, val target: PlayerUid,
                      val jumping: Boolean = false, val minions: MutableList<Npc> = mutableListOf())

    private fun begin(npc: Npc, player: Player, jumping: Boolean = false): Job =
        Job(npc, deps.encounter(npc), player.uid, jumping).also { jobs += it }

    private fun live(job: Job): Boolean {
        val player = job.target.resolve(deps.playerList)
        return running && job in jobs && job.npc.isSlotAssigned && job.npc.hitpoints > 0 &&
            deps.encounterRegistry.isActive(job.encounter) && player != null && player.hitpoints > 0 &&
            player.coords.level == job.npc.coords.level && player.coords.chebyshevDistance(job.npc.coords) <= 16
    }

    private fun cancel(job: Job) {
        if (!jobs.remove(job)) return
        if (job.jumping) {
            job.npc.movementLocked = false
            job.npc.resetAnim()
        }
        job.minions.forEach { if (it.isSlotAssigned) deps.npcRepo.del(it, Int.MAX_VALUE) }
    }

    private fun reset(npc: Npc) {
        jobs.filter { it.npc === npc }.toList().forEach(::cancel)
        npc.movementLocked = false
        npc.apRangeOverride = 8
    }

    private fun after(job: Job, ticks: Int, action: () -> Unit) {
        if (ticks <= 0) { if (live(job)) action() else cancel(job); return }
        deps.worldQueues.add(1) {
            if (!live(job)) cancel(job) else after(job, ticks - 1, action)
        }
    }

    private fun acid(npc: Npc, player: Player) {
        val job = begin(npc, player)
        val tile = player.coords
        deps.bossProjectile("spotanim.lizardshaman_spit_acid".asRSCM(), npc.coords.translate(1, 1), tile,
            startHeight = 55, endHeight = 0, delay = 30, travel = 60, curve = 15)
        after(job, 3) {
            deps.worldRepo.spotanimMap(SpotanimType("spotanim.lizardshaman_acid_splash".asRSCM()), tile)
            for (victim in deps.playerList) {
                if (victim.hitpoints > 0 && victim.coords.level == tile.level && victim.coords.chebyshevDistance(tile) <= 1) {
                    val damage = ShayzienProtection.acidDamage(victim, deps.random.of(25, 30))
                    victim.finishNpcHit(npc, 1, HitType.Typeless, damage, deps.playerHitModifier)
                    PlayerPoison.tryPoison(victim, 10)
                }
            }
            cancel(job)
        }
    }

    private fun jump(npc: Npc, player: Player) {
        val dest = landingTile(player)
        if (!canLand(npc, dest)) return
        val job = begin(npc, player, jumping = true)
        npc.movementLocked = true
        npc.abortRoute()
        after(job, 5) {
            if (canLand(npc, dest)) {
                npc.teleport(deps.collision, dest)
                job.target.resolve(deps.playerList)?.let { ai.interactAp(npc, it, InteractionOp.Op2) }
                npc.resetAnim()
                npc.anim("seq.shayzien_lizard_boss_land")
                val center = dest.translate(1, 1)
                for (victim in deps.playerList) {
                    val hit = if (temple(npc)) victim.coords.x in center.x..center.x + 1 &&
                        victim.coords.z in center.z..center.z + 1 else victim.coords.chebyshevDistance(center) <= 1
                    if (victim.hitpoints > 0 && victim.coords.level == center.level && hit) {
                        victim.finishNpcHit(npc, 1, HitType.Typeless, deps.random.of(20, 25), deps.playerHitModifier)
                    }
                }
            }
            after(job, 2) { cancel(job) }
        }
    }

    private fun summon(npc: Npc, player: Player) {
        val job = begin(npc, player)
        val offsets = listOf(-1 to 0, 1 to 0, 0 to 1)
        for ((x, z) in offsets) {
            val tile = player.coords.translate(x, z)
            if (blocked(tile)) continue
            val spawn = Npc(checkNotNull(ServerCacheManager.getNpc("npc.zeah_lizardshaman_spawn".asRSCM())), tile)
            deps.npcRepo.add(spawn, 12)
            spawn.mode = null
            spawn.ignoreCombatInteractions = true
            job.minions += spawn
        }
        fun follow(remaining: Int) {
            after(job, 1) {
                val target = job.target.resolve(deps.playerList) ?: return@after cancel(job)
                job.minions.filter { it.isSlotAssigned }.forEach { it.walk(target.coords) }
                if (remaining > 1) follow(remaining - 1) else {
                    for (spawn in job.minions.filter { it.isSlotAssigned }) {
                        deps.worldRepo.spotanimMap(SpotanimType("spotanim.lizardshaman_spawn_explode".asRSCM()), spawn.coords)
                        for (victim in deps.playerList) {
                            if (victim.hitpoints > 0 && victim.coords.level == spawn.coords.level &&
                                victim.coords.chebyshevDistance(spawn.coords) <= if (temple(npc)) 1 else 2) {
                                victim.finishNpcHit(npc, 1, HitType.Typeless, deps.random.of(8, 10), deps.playerHitModifier)
                            }
                        }
                    }
                    cancel(job)
                }
            }
        }
        follow(8)
    }

    private fun blocked(tile: CoordGrid): Boolean =
        deps.collision[tile.x, tile.z, tile.level] and
            (CollisionFlag.LOC or CollisionFlag.BLOCK_WALK or CollisionFlag.GROUND_DECOR) != 0

    private fun canLand(npc: Npc, tile: CoordGrid): Boolean =
        tile.level == npc.coords.level && (0..2).all { x -> (0..2).all { z -> !blocked(tile.translate(x, z)) } }

    private fun landingTile(player: Player): CoordGrid = player.coords.translate(-1, -1)
    private fun temple(npc: Npc): Boolean = npc.type.id == "npc.molch_lizardshaman_1".asRSCM()

    internal companion object {
        val TYPES = listOf("npc.zeah_lizardshaman_1", "npc.zeah_lizardshaman_2",
            "npc.lizardman_cave_shaman_1", "npc.lizardman_cave_shaman_2", "npc.molch_lizardshaman_1")
    }
}
