package org.rsmod.content.other.treasure.trails

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.events.interact.ContextualNpcOp
import org.rsmod.api.player.interact.ContextualInteractions
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.*
import org.rsmod.game.hit.HitType
import org.rsmod.game.interact.InteractionOp
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class TrailHotColdScript @Inject constructor(
    private val progress: TrailProgress,
    private val targets: TrailTargets,
    private val contextual: ContextualInteractions,
    private val random: GameRandom,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (tier in listOf(TrailTier.BEGINNER, TrailTier.MASTER)) {
            val device = "obj.${tier.key}_device"
            onOpHeld1(device) {
                val active = targets.active(player).firstOrNull { clue -> clue.clue.kind == "hotcold" && clue.clue.tier == tier }
                if (active == null) { mes("The device is inactive."); return@onOpHeld1 }
                val target = active.clue.targets.map(progress.catalog::fields).firstOrNull { fields -> fields.table == "cluehelper_target_coord" } ?: return@onOpHeld1
                val coords = CoordGrid(target.int("coord"))
                val distance = player.coords.chebyshevDistance(coords)
                val original = it.inventory[it.slot] ?: return@onOpHeld1
                val previous = if (original.vars == 0) null else CoordGrid(original.vars).chebyshevDistance(coords)
                val comparison = when {
                    previous == null -> ""
                    distance < previous -> ", and warmer than last time"
                    distance > previous -> ", and colder than last time"
                    else -> ", and the same temperature as last time"
                }
                if (progress.replace(player, it.inventory, it.slot, original, original.copy(vars = player.coords.packed))) {
                    mes("The device is ${temperature(distance, tier)}$comparison.")
                    if (tier == TrailTier.MASTER) takeInstantHit(HitType.Typeless, random.of(3, 8))
                }
            }
        }
        contextual.npc("trail-device") { player, npc, op ->
            val tier = when (npc.visType.id) {
                "npc.makinghistory_jorral".asRSCM(), "npc.trail_watson".asRSCM(), "npc.trail_watson_pre_talk".asRSCM(), "npc.trail_watson_post_talk".asRSCM() -> TrailTier.MASTER
                "npc.reldo_normal".asRSCM(), "npc.reldo".asRSCM(), "npc.reldo_withbook".asRSCM() -> TrailTier.BEGINNER
                else -> null
            }
            if (tier != null && op == InteractionOp.Op1 && targets.active(player).any { it.clue.kind == "hotcold" && it.clue.tier == tier }) ContextualNpcOp(npc, KEY) else null
        }
        onProtectedEvent<ContextualNpcOp>(KEY) {
            val tier = if (it.npc.visType.id in listOf("npc.reldo_normal", "npc.reldo", "npc.reldo_withbook").map { name -> name.asRSCM() }) TrailTier.BEGINNER else TrailTier.MASTER
            val item = "obj.${tier.key}_device"
            if (player.inv.count(item) > 0) mes("Use your strange device to feel how close you are to the treasure.")
            else if (player.invAdd(player.inv, item, 1).success) mes("You receive a strange device. Feel it as you travel to locate the treasure.")
            else mes("Make an inventory space for the strange device.")
        }
    }
    override fun ScriptContext.shutdown() { contextual.unregister("trail-device") }
    companion object {
        private const val KEY = 0x484f54434f4c44L
        fun temperature(distance: Int, tier: TrailTier): String = when {
            distance >= 500 -> "ice cold"
            distance >= 200 -> "very cold"
            distance >= 150 -> "cold"
            distance >= 100 -> "warm"
            distance >= 70 -> "hot"
            distance >= 30 -> "very hot"
            distance > if (tier == TrailTier.BEGINNER) 3 else 4 -> "incredibly hot"
            else -> "visibly shaking"
        }
    }
}
