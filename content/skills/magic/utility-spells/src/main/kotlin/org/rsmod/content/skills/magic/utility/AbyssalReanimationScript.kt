package org.rsmod.content.skills.magic.utility

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.npc.owner.isSpawnOwnedBy
import org.rsmod.api.player.events.skilling.ReanimatedAbyssalKilledEvent
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.player.stat.slayerLvl
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.player.ui.IfOverlayButtonT
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/** Inventory Master Reanimation, adapted from upstream PR 272 (5c553607). */
@Singleton
class AbyssalReanimationScript @Inject constructor(
    private val bus: EventBus,
    private val launcher: ProtectedAccessLauncher,
    private val spells: MagicSpellRegistry,
    private val runes: MagicRuneManager,
    private val npcs: NpcRepository,
    private val collision: CollisionFlagMap,
    private val xpMods: XpModifiers,
) : PluginScript(), NpcAttackValidateHook, NpcDeathKillHook, PlayerDeathCleanupHook, PlayerPostTickHook {
    private val owned = HashMap<Player, Npc>()

    override fun ScriptContext.startup() {
        val key = EventBus.composeLongKey("component.magic_spellbook:reanimation_master".asRSCM(), "component.inventory:items".asRSCM())
        onEvent<IfOverlayButtonT>(key) {
            val target = targetObj ?: return@onEvent
            if (target.id !in heads || player.isDelayed || player.isAccessProtected) return@onEvent
            player.clearPendingAction(bus)
            launcher.launch(player) {
                val original = inv[targetSlot] ?: return@launch
                if (original.id != target.id || original.vars != 0 || original.count != 1) return@launch
                if (!inArea("area.dark_altar", coords)) { mes("Reanimate the head near the Dark Altar in Arceuus."); return@launch }
                if (player.slayerLvl < 85) { mes("You need Slayer 85 to reanimate an abyssal head."); return@launch }
                if (owned[player]?.let { it.hitpoints > 0 } == true) { mes("Finish your last reanimation first."); return@launch }
                cleanup(player)
                val spawn = adjacent(coords) ?: return@launch
                val spell = spells.getUtilitySpell(checkNotNull(ServerCacheManager.getItem("obj.magictraining_guardianstatue".asRSCM())))
                if (!runes.canCastSpell(player, spell)) return@launch
                anim("seq.arceuus_necromancy_playeranim")
                spotanim("spotanim.arceuus_necromancy_playerspot")
                delay(5)
                if (inv[targetSlot] !== original || !inArea("area.dark_altar", coords) || !walkable(spawn)) return@launch
                val creature = Npc("npc.arceuus_reanimated_abyssal", spawn)
                if (runCatching { npcs.add(creature, 300) }.isFailure) { mes("The creature cannot be reanimated here."); return@launch }
                creature.assignSpawnOwner(player, mapClock)
                if (!produceSpell(player, runes, spell, target.id, targetSlot, null)) { npcs.del(creature, Int.MAX_VALUE); return@launch }
                owned[player] = creature
                creature.spotanim("spotanim.arceuus_necromancy_spawning")
                statAdvance("stat.magic", spell.castXp * xpMods.get(player, "stat.magic"))
                actionDelay = mapClock + 3
            }
        }
        onPlayerLogout { cleanup(player) }
    }

    override fun ScriptContext.shutdown() { for (player in owned.keys.toList()) cleanup(player) }

    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult =
        if (npc.isType("npc.arceuus_reanimated_abyssal") && !npc.isSpawnOwnedBy(player)) NpcAttackValidateResult.Deny("That is another player's reanimated creature.") else NpcAttackValidateResult.Pass

    override fun onKill(context: NpcDeathKillContext) {
        val owner = owned.entries.firstOrNull { it.value === context.npc }?.key ?: return
        owned.remove(owner)
        if (owner !== context.hero || !context.npc.isSpawnOwnedBy(context.hero)) return
        context.hero.statAdvance("stat.prayer", 1300.0 * xpMods.get(context.hero, "stat.prayer"))
        bus.publish(ReanimatedAbyssalKilledEvent(context.hero))
    }

    override fun onPostTick(player: Player) {
        val creature = owned[player] ?: return
        if (creature.lifecycleDelCycle <= player.currentMapClock || creature.coords.level != player.coords.level ||
            kotlin.math.max(kotlin.math.abs(creature.coords.x - player.coords.x), kotlin.math.abs(creature.coords.z - player.coords.z)) > 20) cleanup(player)
    }

    override fun cleanup(player: Player) {
        val npc = owned.remove(player) ?: return
        if (npc.slotId > 0) runCatching { npcs.del(npc, Int.MAX_VALUE) }
    }

    private fun adjacent(coords: CoordGrid): CoordGrid? = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1).map { (x, z) -> coords.translate(x, z) }.firstOrNull(::walkable)
    private fun walkable(coords: CoordGrid): Boolean = collision.isZoneValid(coords) && collision[coords.x, coords.z, coords.level] and (CollisionFlag.LOC or CollisionFlag.BLOCK_WALK or CollisionFlag.GROUND_DECOR) == 0

    companion object { val heads by lazy { setOf("obj.arceuus_corpse_abyssal".asRSCM(), "obj.arceuus_corpse_abyssal_initial".asRSCM()) } }
}
