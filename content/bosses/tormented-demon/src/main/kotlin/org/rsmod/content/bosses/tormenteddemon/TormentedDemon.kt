package org.rsmod.content.bosses.tormenteddemon

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.runtime.encounter
import org.rsmod.api.bosses.runtime.lob
import org.rsmod.api.bosses.runtime.suppressAttacks
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.ProjectileConfig
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.combat.commons.magic.MagicSpellChecks
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.combat.commons.types.AttackType
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.vars.setActiveMoveSpeed
import org.rsmod.api.player.vars.varMoveSpeed
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.route.walkTo
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.flag.CollisionFlag

class TormentedDemon
@Inject
constructor(deps: BossDeps, private val routeFactory: RouteFactory, private val attackTypes: AttackTypes) : BossPluginScript(deps) {

    private val demonTypeIds: Set<Int> by lazy {
        setOf("npc.tormented_demon_1".asRSCM(RSCMType.NPC), "npc.tormented_demon_2".asRSCM(RSCMType.NPC))
    }

    private val defencelessTypeByType: Map<Int, String> by lazy {
        mapOf(
            "npc.tormented_demon_1".asRSCM(RSCMType.NPC) to "npc.tormented_demon_defenceless_1",
            "npc.tormented_demon_2".asRSCM(RSCMType.NPC) to "npc.tormented_demon_defenceless_2",
        )
    }

    private val fights = mutableMapOf<NpcUid, TdFight>()
    private var monitoring = false
    private var running = true

    private fun fightFor(npc: Npc): TdFight = fights.getOrPut(npc.uid) { TdFight(npc) }

    private fun markFightStarted(fight: TdFight) {
        if (fight.defencelessCycleStart < 0) fight.defencelessCycleStart = deps.mapClock.cycle
    }

    override fun ScriptContext.startup() {
        BossCombat.register(this, spec, deps,
            onHit = { if (npc.hitpoints <= 0) { restoreAppearance(npc); fights.remove(npc.uid) } },
            onModifyHit = { onDemonHit(npc, hit) },
            onCombatTick = { target -> combatTick(npc, target) })

        for (typeId in demonTypeIds) {
            onEvent<NpcStateEvents.Spawn>(typeId) {
                npc.vars["varn.td_shield_up"] = 1
                updateStyleImmunity(npc, null)
                initializeOverheadPrayer(npc)
            }
        }
        onEvent<NpcStateEvents.Respawn> {
            if (npc.id in demonTypeIds) {
                fights.remove(npc.uid)
                npc.vars["varn.td_shield_up"] = 1
                npc.vars["varn.guaranteed_hit"] = 0
                restoreAppearance(npc)
                updateStyleImmunity(npc, null)
                initializeOverheadPrayer(npc)
            }
        }
        onEvent<NpcStateEvents.Delete> {
            if (npc.id in demonTypeIds) fights.remove(npc.uid)
        }

        deps.extensionRegistry.register("td.post_attack") { _, npc, _, _ -> postAttack(npc) }
        deps.extensionRegistry.register("td.fire_bomb") { _, npc, target, _ -> fireBomb(npc, target) }
    }

    override fun ScriptContext.shutdown() {
        running = false
        for (fight in fights.values.toList()) resetFight(fight.npc)
        fights.clear()
        deps.extensionRegistry.unregister("td.post_attack")
        deps.extensionRegistry.unregister("td.fire_bomb")
    }

    internal fun combatTick(npc: Npc, target: Player) {
        val fight = fightFor(npc)
        markFightStarted(fight)
        fight.target = target
        if (!fight.styleInitialized) {
            fight.styleInitialized = true
            val style = STYLE_PHASES[deps.random.of(STYLE_PHASES.size)]
            deps.encounter(npc).transitionTo(style, deps.mapClock.cycle)
            applyStyleRange(npc, style)
        }
        updateDefenceless(npc, fight)
        startMonitoring()
    }

    private fun startMonitoring() {
        if (monitoring || !running) return
        monitoring = true
        deps.worldQueues.add(1) {
            monitoring = false
            if (!running) return@add
            for (fight in fights.values.toList()) {
                val target = fight.target ?: continue
                if (!fight.npc.isSlotAssigned || fight.npc.hitpoints <= 0) {
                    fights.remove(fight.npc.uid)
                } else if (!target.isValidTarget() || target.coords.level != fight.npc.coords.level ||
                    !fight.npc.isWithinDistance(target, AGGRO_RANGE + 22)) {
                    resetFight(fight.npc)
                } else {
                    updateDefenceless(fight.npc, fight)
                }
            }
            if (fights.values.any { it.target != null }) startMonitoring()
        }
    }

    @OptIn(InternalApi::class)
    internal fun resetFight(npc: Npc) {
        fights.remove(npc.uid)
        deps.encounterRegistry.remove(npc)?.interrupt(deps.mapClock.cycle)
        npc.vars["varn.guaranteed_hit"] = 0
        npc.vars["varn.td_shield_up"] = 1
        npc.apRangeOverride = null
        restoreAppearance(npc)
        npc.infoProtocol?.setSpotanim(-1, 0, 0, DEFENCELESS_SPOT_SLOT)
        npc.infoProtocol?.setSpotanim(-1, 0, 0, SHIELD_SPOT_SLOT)
        npc.copyCurrentStats(npc.type)
        npc.clearHeroPoints()
        updateStyleImmunity(npc, null)
        initializeOverheadPrayer(npc)
    }

    override val spec =
        boss("npc.tormented_demon_1", "npc.tormented_demon_2", "npc.tormented_demon_defenceless_1", "npc.tormented_demon_defenceless_2") {
            stats(
                attackRate = TormentedDemonMechanics.SOLO_ATTACK_RATE,
                aggressionRadius = AGGRO_RANGE,
            )

            val melee =
                ability("melee") {
                    anim("seq.luc2_undead_demon_melee")
                    spotanim("spotanim.luc2_undead_demon_melee_spot")
                    hit {
                        damage(0..MELEE_MAX_HIT).roll()
                        type(Melee)
                    }
                    include(external("td.post_attack"))
                }

            val ranged =
                ability("ranged") {
                    anim("seq.luc2_undead_demon_spare_ribs")
                    projectile(
                        spotanim = "spotanim.luc2_rib_bone_shard_projectile",
                        config =
                            ProjectileConfig(
                                startHeight = 400,
                                endHeight = 120,
                                angle = 10,
                                progress = 200,
                            ),
                        hit = Effect.Hit(damage = Roll(0..RANGED_MAX_HIT), type = Ranged),
                    )
                    include(external("td.post_attack"))
                }

            val magic =
                ability("magic") {
                    anim("seq.luc2_undead_demon_firey_balls")
                    projectile(
                        spotanim = "spotanim.luc2_undead_demon_fireball_proj",
                        config =
                            ProjectileConfig(
                                startHeight = 344,
                                endHeight = 120,
                                angle = 0,
                                progress = 160,
                            ),
                        hit = Effect.Hit(damage = Roll(0..MAGIC_MAX_HIT), type = Magic),
                    )
                    include(external("td.post_attack"))
                }

            val fireBomb =
                ability("fire_bomb") {
                    anim("seq.luc2_undead_demon_explosion_fire")
                    include(external("td.fire_bomb"))
                }

            phase(PHASE_MELEE) {
                forceEvery(FIRE_BOMB_PERIOD, fireBomb)
                weightedSelectorRandom {
                    +random(melee, weight = 1, requires = WithinMeleeRange)
                    +random(ranged, weight = 1, requires = Condition.Not(WithinMeleeRange))
                    +random(magic, weight = 1, requires = Condition.Not(WithinMeleeRange))
                }
            }
            phase(PHASE_RANGED) {
                forceEvery(FIRE_BOMB_PERIOD, fireBomb)
                weightedSelectorRandom { +random(ranged, weight = 1) }
            }
            phase(PHASE_MAGIC) {
                forceEvery(FIRE_BOMB_PERIOD, fireBomb)
                weightedSelectorRandom { +random(magic, weight = 1) }
            }
        }

    internal fun fireBomb(npc: Npc, target: Player) {
        if (!target.isValidTarget()) return
        val fight = fightFor(npc)
        markFightStarted(fight)

        val primaryTile = target.coords
        val secondaryTile = randomAdjacentWalkableTile(primaryTile)
        val targetUid = target.uid

        CombatEffects.freeze(target, FIRE_BOMB_BIND_TICKS)
        target.spotanim("spotanim.entangle_impact", height = 124)
        val restoreSpeed = target.varMoveSpeed
        target.setActiveMoveSpeed(MoveSpeed.Walk)
        deps.worldQueues.add(FIRE_BOMB_BIND_TICKS) {
            if (target.uid == targetUid && target.isValidTarget() && target.varMoveSpeed == MoveSpeed.Walk) target.setActiveMoveSpeed(restoreSpeed)
        }

        for (landingTile in listOfNotNull(primaryTile, secondaryTile).distinct()) {
            deps.worldRepo.spotanimMap(
                SpotanimType(TELEGRAPH_SHADOW),
                landingTile,
                delay = FIRE_BOMB_TELEGRAPH_DELAY,
            )
            deps.lob(
                npc = npc,
                targetTile = landingTile,
                targetUid = targetUid,
                spotanim = PROJ_FIRE_BOMB,
                startHeight = 344,
                endHeight = 0,
                delay = FIRE_BOMB_PROJ_DELAY,
                travel = FIRE_BOMB_PROJ_TRAVEL,
                curve = FIRE_BOMB_PROJ_ANGLE,
                landTicks = FIRE_BOMB_LAND_TICKS,
                landGfx = SPOT_EXPLOSION,
                progress = FIRE_BOMB_PROJ_PROGRESS,
            ) { player ->
                if (running && npc.isSlotAssigned && npc.hitpoints > 0 &&
                    fights[npc.uid] === fight && player.isValidTarget() && player.coords == landingTile) {
                    val damage = FIRE_BOMB_MIN_DAMAGE + deps.random.of(FIRE_BOMB_DAMAGE_SPREAD)
                    player.finishNpcHit(npc, 1, HitType.Typeless, damage, deps.playerHitModifier)
                }
            }
        }

        fight.defenceless = false
        fight.defencelessCycleStart = deps.mapClock.cycle
        restoreAppearance(npc)
        npc.infoProtocol?.setSpotanim(-1, 0, 0, DEFENCELESS_SPOT_SLOT)
        dropShield(npc, fight)

        val encounter = deps.encounter(npc)
        val otherStyles = STYLE_PHASES.filter { it != encounter.currentPhaseName }
        val nextStyle = otherStyles[deps.random.of(otherStyles.size)]
        encounter.transitionTo(nextStyle, deps.mapClock.cycle)
        applyStyleRange(npc, nextStyle)
        if (nextStyle != PHASE_MELEE) {
            retreatFromMelee(npc, target)
        }
    }

    private fun applyStyleRange(npc: Npc, style: String) {
        npc.apRangeOverride = if (style == PHASE_MELEE) null else RANGED_MAGIC_AP_RANGE
    }

    private fun retreatFromMelee(npc: Npc, target: Player) {
        if (!npc.isWithinDistance(target, MELEE_RANGE_TILES)) return
        val dx = retreatOffset(npc.coords.x - target.coords.x)
        val dz = retreatOffset(npc.coords.z - target.coords.z)
        val dest = npc.coords.translate(dx * RETREAT_DISTANCE, dz * RETREAT_DISTANCE)
        npc.walkTo(routeFactory, dest)
    }

    private fun retreatOffset(delta: Int): Int =
        when {
            delta > 0 -> 1
            delta < 0 -> -1
            else -> if (deps.random.of(2) == 0) -1 else 1
        }

    private fun randomAdjacentWalkableTile(center: CoordGrid): CoordGrid? {
        val candidates =
            ADJACENT_OFFSETS.map { (dx, dz) -> center.translate(dx, dz) }.filter(::isWalkable)
        if (candidates.isEmpty()) return null
        return candidates[deps.random.of(candidates.size)]
    }

    private fun isWalkable(coord: CoordGrid): Boolean {
        val flags = deps.collision[coord.x, coord.z, coord.level]
        return flags and (CollisionFlag.BLOCK_WALK or CollisionFlag.LOC) == 0
    }

    private fun postAttack(npc: Npc) {
        val fight = fightFor(npc)
        markFightStarted(fight)
        updateDefenceless(npc, fight)
    }

    private fun dropShield(npc: Npc, fight: TdFight) {
        npc.vars["varn.td_shield_up"] = 0
        npc.spotanim("spotanim.luc2_undead_demon_explosion_fire_spot", slot = SHIELD_SPOT_SLOT)
        updateStyleImmunity(npc, null)
        updateGuaranteedHit(npc, fight)
    }

    private fun updateGuaranteedHit(npc: Npc, fight: TdFight) {
        val guaranteed = fight.defenceless || npc.vars["varn.td_shield_up"] == 0
        npc.vars["varn.guaranteed_hit"] = if (guaranteed) 1 else 0
    }

    private fun initializeOverheadPrayer(npc: Npc) {
        val style = HitType.Melee
        fightFor(npc).overheadStyle = style
        npc.vars["varn.td_overhead_style"] = overheadStyleCode(style)
        updateStyleImmunity(npc, null)
        val index = headIconIndex(style)
        if (index != null) {
            npc.setHeadIcon(HEADICON_SLOT, HEADICON_GRAPHIC, index)
        } else {
            npc.clearHeadIcon(HEADICON_SLOT)
        }
    }

    private fun updateStyleImmunity(npc: Npc, activeStyle: HitType?) {
        npc.vars["varn.immune_melee"] = if (activeStyle == HitType.Melee) 1 else 0
        npc.vars["varn.immune_ranged"] = if (activeStyle == HitType.Ranged) 1 else 0
        npc.vars["varn.immune_magic"] = if (activeStyle == HitType.Magic) 1 else 0
    }

    private fun updateDefenceless(npc: Npc, fight: TdFight) {
        if (fight.defenceless || fight.defencelessCycleStart < 0) return
        if (deps.mapClock.cycle - fight.defencelessCycleStart >= DEFENCELESS_DELAY_TICKS) {
            fight.defenceless = true
            updateGuaranteedHit(npc, fight)
            npc.spotanim("spotanim.luc2_undead_accuracy_debuff", slot = DEFENCELESS_SPOT_SLOT)
            defencelessTypeByType[npc.id]?.let { type ->
                npc.transmog(checkNotNull(ServerCacheManager.getNpc(type.asRSCM())), Int.MAX_VALUE)
            }
        }
    }

    private fun restoreAppearance(npc: Npc) {
        if (npc.transmog != null) npc.resetTransmog()
    }

    internal fun onDemonHit(npc: Npc, hit: HitBuilder) {
        if (!hit.isFromPlayer) return
        val fight = fightFor(npc)
        markFightStarted(fight)
        val style = hit.type
        val shieldWasUp = npc.vars["varn.td_shield_up"] == 1
        val attacker = hit.sourceSlot?.let { deps.playerList[it] }
        if (attacker != null && attacker.uid.packed == hit.sourceUid) {
            fight.target = attacker
            startMonitoring()
        }

        val blockedByOverhead = fight.overheadStyle == style
        hit.impactEffects.beforeImpact { damage ->
            if (running && fights[npc.uid] === fight) damage else 0
        }
        if (shieldWasUp) {
            hit.damage = if (blockedByOverhead) 0 else if (bypassesShield(hit)) hit.damage else hit.damage * 4 / 5
        } else {
            val rate = attacker?.let { (it.actionDelay - deps.mapClock.cycle).coerceAtLeast(0) } ?: 0
            val eligible = hasSlowWeaponBonus(hit, attacker)
            if (blockedByOverhead) hit.damage = if (eligible) minOf(hit.damage, (rate * rate - 16).coerceAtLeast(0) / 3) else 0
            if (eligible && rate > 4 && attacker != null) {
                val demons = fights.values.count { it.target?.uid == attacker.uid && it.npc.hitpoints > 0 }.coerceIn(1, 3)
                attacker.actionDelay = deps.mapClock.cycle + 5 - demons
            }
        }

        hit.impactEffects.add { damage ->
            if (running && fights[npc.uid] === fight && damage > 0) {
                fight.damageSinceSwap += damage
                if (TormentedDemonMechanics.shouldSwapPrayer(fight.damageSinceSwap)) {
                    fight.overheadStyle = style
                    fight.damageSinceSwap = 0
                    npc.vars["varn.td_overhead_style"] = overheadStyleCode(style)
                    headIconIndex(style)?.let { npc.setHeadIcon(HEADICON_SLOT, HEADICON_GRAPHIC, it) }
                    deps.suppressAttacks(npc, PRAYER_STALL_TICKS)
                }
            }
        }

        if (!fight.firstHitTaken) {
            fight.firstHitTaken = true
            dropShield(npc, fight)
        } else if (!shieldWasUp) {
            npc.vars["varn.td_shield_up"] = 1
            npc.spotanim("spotanim.luc2_undead_demon_shield_restore_spot", slot = SHIELD_SPOT_SLOT)
            updateGuaranteedHit(npc, fight)
        } else {
            npc.spotanim("spotanim.luc2_undead_demon_shield_spot", slot = SHIELD_SPOT_SLOT)
        }
    }

    private fun bypassesShield(hit: HitBuilder): Boolean {
        val weapon = hit.righthandType()
        if (weapon?.isAnyType("obj.darklight", "obj.arclight", "obj.emberlight", "obj.scorching_bow",
                "obj.abyssal_whip", "obj.abyssal_whip_lava", "obj.abyssal_whip_ice", "obj.abyssal_tentacle",
                "obj.abyssal_bludgeon", "obj.abyssal_dagger", "obj.abyssal_dagger_p",
                "obj.abyssal_dagger_p+", "obj.abyssal_dagger_p++") == true) return true
        return hit.secondaryType()?.let(MagicSpellChecks::isDemonbaneSpell) == true
    }

    private fun hasSlowWeaponBonus(hit: HitBuilder, attacker: Player?): Boolean {
        if (hit.type == HitType.Magic) return hit.secondaryType() != null
        if (attacker == null) return false
        val type = attackTypes.resolve(hit.righthandType(), attacker.vars["varp.com_mode"])
        return if (hit.type == HitType.Melee) type == AttackType.Crush else type == AttackType.Heavy
    }

    private fun headIconIndex(style: HitType): Int? =
        when (style) {
            HitType.Melee -> 0
            HitType.Ranged -> 1
            HitType.Magic -> 2
            else -> null
        }

    private fun overheadStyleCode(style: HitType): Int =
        when (style) {
            HitType.Melee -> 1
            HitType.Ranged -> 2
            HitType.Magic -> 3
            else -> 0
        }

    private class TdFight(val npc: Npc) {
        var target: Player? = null
        var styleInitialized = false
        var firstHitTaken: Boolean = false
        var overheadStyle: HitType? = null
        var lastStyleHit: HitType? = null
        var damageSinceSwap: Int = 0
        var defencelessCycleStart: Int = -1
        var defenceless: Boolean = false
    }

    private companion object {
        private const val PHASE_MELEE = "style_melee"
        private const val PHASE_RANGED = "style_ranged"
        private const val PHASE_MAGIC = "style_magic"
        private val STYLE_PHASES = listOf(PHASE_MELEE, PHASE_RANGED, PHASE_MAGIC)
        private const val RANGED_MAGIC_AP_RANGE = 7
        private const val MELEE_RANGE_TILES = 1
        private const val RETREAT_DISTANCE = 3

        private const val AGGRO_RANGE = 8

        private const val PRAYER_STALL_TICKS = 6
        private const val DEFENCELESS_DELAY_TICKS = 30

        private const val HEADICON_SLOT = 0
        private const val HEADICON_GRAPHIC = 440

        private const val SHIELD_SPOT_SLOT = 4
        private const val DEFENCELESS_SPOT_SLOT = 5

        private const val FIRE_BOMB_PERIOD = 60
        private const val FIRE_BOMB_BIND_TICKS = 2

        private const val FIRE_BOMB_LAND_TICKS = 4
        private const val FIRE_BOMB_MIN_DAMAGE = 40
        private const val FIRE_BOMB_DAMAGE_SPREAD = 6

        private const val FIRE_BOMB_PROJ_DELAY = 45
        private const val FIRE_BOMB_PROJ_TRAVEL = 70
        private const val FIRE_BOMB_PROJ_ANGLE = 30
        private const val FIRE_BOMB_PROJ_PROGRESS = 128

        private const val MELEE_MAX_HIT = 31
        private const val RANGED_MAX_HIT = 31
        private const val MAGIC_MAX_HIT = 31

        private const val PROJ_FIRE_BOMB = 2855
        private const val SPOT_EXPLOSION = 2856
        private const val TELEGRAPH_SHADOW = 1446
        private const val FIRE_BOMB_TELEGRAPH_DELAY = 30

        private val ADJACENT_OFFSETS =
            listOf(-1 to -1, 0 to -1, 1 to -1, -1 to 0, 1 to 0, -1 to 1, 0 to 1, 1 to 1)
    }
}

internal object TormentedDemonMechanics {
    const val SOLO_ATTACK_RATE: Int = 6
    const val PRAYER_SWAP_DAMAGE: Int = 150

    fun shouldSwapPrayer(damageSinceSwap: Int): Boolean = damageSinceSwap >= PRAYER_SWAP_DAMAGE
}
