package org.rsmod.content.skills.hunter

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.invtx.*
import org.rsmod.api.player.events.skilling.SkillingActionCompleteEvent
import org.rsmod.api.player.events.skilling.SkillingActionContext
import org.rsmod.api.player.events.skilling.SkillingProductSource
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hunterLvl
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.*
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Scoped trap loop, adapted from PR 229 with ownership and permanent material escrow. */
@Singleton
class ClueTrapHunting @Inject constructor(
    private val locs: LocRepository,
    private val npcs: NpcRepository,
    private val random: GameRandom,
    private val xpMods: XpModifiers,
) : PluginScript(), PlayerPostTickHook, PlayerDeathCleanupHook {
    enum class Kind(val level: Int, val npc: String, val active: String, val failed: String, val full: String, val xp: Double, val materials: Map<String, String>) {
        RedChinchompa(63, "npc.hunting_chinchompa_big", "loc.hunting_boxtrap_empty", "loc.hunting_boxtrap_failed", "loc.hunting_boxtrap_full_chinchompa_big", 265.0, mapOf("obj.hunting_box_trap" to "varp.hunter_escrow_box")),
        Tecu(79, "npc.salamander_mountain", "loc.hunting_sapling_set_mountain", "loc.hunting_sapling_failed_mountain", "loc.hunting_sapling_full_mountain", 344.0, mapOf("obj.rope" to "varp.hunter_escrow_rope", "obj.net" to "varp.hunter_escrow_net")),
    }
    private data class Trap(val owner: Player, val kind: Kind, var loc: LocInfo, var next: Int, val expires: Int, var caught: Boolean = false, var broken: Boolean = false)
    private val traps = HashMap<CoordGrid, Trap>()
    private val refunds by lazy { Kind.entries.flatMap { it.materials.entries }.associate { it.value to it.key } }

    override fun ScriptContext.startup() {
        onOpHeld1("obj.hunting_box_trap") { lay(Kind.RedChinchompa, null, it.slot) }
        onOpLoc1("loc.hunting_sapling_up_mountain") { lay(Kind.Tecu, it.loc, null) }
        onOpLocU("loc.hunting_sapling_up_mountain") { if (it.objType.internalName in Kind.Tecu.materials && inv[it.invSlot]?.id == it.objType.id) lay(Kind.Tecu, it.loc, null) }
        for (kind in Kind.entries) {
            onOpLoc1(kind.active) { collect(it.loc, false) }
            onOpLoc2(kind.active) { mes("The trap is waiting for a creature.") }
            onOpLoc1(kind.failed) { collect(it.loc, false) }
            onOpLoc2(kind.failed) { collect(it.loc, true) }
            onOpLoc1(kind.full) { collect(it.loc, false) }
            onOpLoc2(kind.full) { collect(it.loc, true) }
        }
        onPlayerLogout { cleanup(player) }
        onPlayerLogin { refund(player) }
    }

    override fun ScriptContext.shutdown() { for (owner in traps.values.map { it.owner }.distinct()) cleanup(owner) }

    private suspend fun ProtectedAccess.lay(kind: Kind, tree: BoundLocInfo?, slot: Int?) {
        if (player.hunterLvl < kind.level) { mes("You need Hunter ${kind.level} for this trap."); return }
        val tile = tree?.coords ?: coords
        val before = slot?.let { inv[it] }
        val placedOrOwed = player.vars["varp.hunter_escrow_box"] + player.vars["varp.hunter_escrow_net"]
        if (placedOrOwed >= (1 + player.hunterLvl / 20).coerceAtMost(5)) { mes("Collect your existing traps or make room for returned materials before setting more."); return }
        if (tile in traps || (tree == null && locs.findExact(tile, LocShape.CentrepieceStraight) != null)) { mes("You cannot set a trap here."); return }
        if (kind.materials.keys.any { inv.count(it) == 0 }) { mes("You need ${if (kind == Kind.Tecu) "a rope and a small fishing net" else "a box trap"}."); return }
        val start = coords
        anim(if (tree == null) "seq.human_laytrap" else "seq.hunting_setting_sapling_trap")
        delay(2)
        resetAnim()
        if (coords != start || tile in traps || (slot != null && inv[slot] !== before)) return
        if (tree != null && locs.findExact(tile, ServerCacheManager.getObject(tree.id)!!) == null) return
        val info = LocInfo(tree?.layer ?: 2, tile, LocEntity(kind.active.asRSCM(), tree?.shape?.id ?: 10, tree?.angle?.id ?: 0))
        val trap = Trap(player, kind, info, mapClock + 5, mapClock + 200)
        if (!locs.add(info, 200) { if (traps[tile] === trap) traps.remove(tile) }) return
        if (!player.invTransaction(inv) { val target = select(inv); for (item in kind.materials.keys) delete(target, item.asRSCM(), 1, slot.takeIf { kind == Kind.RedChinchompa }) }.success) {
            locs.del(info, Int.MAX_VALUE); return
        }
        for (variable in kind.materials.values) VarPlayerIntMapSetter.set(player, variable, player.vars[variable] + 1)
        traps[tile] = trap
        spam("You set the trap.")
    }

    private suspend fun ProtectedAccess.collect(bound: BoundLocInfo, reset: Boolean) {
        val trap = traps[bound.coords] ?: return
        if (trap.owner !== player) { mes("That trap belongs to someone else."); return }
        if (bound.id != trap.loc.id || locs.findExact(bound.coords, ServerCacheManager.getObject(bound.id)!!) == null) return
        val caught = trap.caught
        anim(if (trap.kind == Kind.Tecu) "seq.human_hunting_dismantle_net" else "seq.hunting_setting_trap_small")
        delay(2)
        resetAnim()
        if (traps[bound.coords] !== trap || bound.id != trap.loc.id || !isWithinDistance(bound, 1) || locs.findExact(bound.coords, ServerCacheManager.getObject(bound.id)!!) == null) return
        val output = if (!caught) null else if (trap.kind == Kind.RedChinchompa) "obj.chinchompa_big_captured" else if (random.of(1, 1000) == 1) "obj.mountain_salamander" else "obj.immature_mountain_salamander"
        val result = player.invTransaction(inv) {
            val target = select(inv)
            if (!reset) for (item in trap.kind.materials.keys) add(target, item.asRSCM(), 1)
            if (output != null) add(target, output.asRSCM(), 1)
        }
        if (!result.success) { mes("Make room to collect the trap and its catch."); return }
        if (reset) {
            trap.caught = false; trap.broken = false; trap.next = mapClock + 5
            if (!replace(trap, trap.kind.active)) cleanup(player)
        } else {
            traps.remove(bound.coords)
            locs.del(trap.loc, Int.MAX_VALUE)
            for (variable in trap.kind.materials.values) VarPlayerIntMapSetter.set(player, variable, (player.vars[variable] - 1).coerceAtLeast(0))
        }
        if (output != null) {
            val xp = trap.kind.xp * xpMods.get(player, "stat.hunter")
            statAdvance("stat.hunter", xp)
            publish(SkillingActionCompleteEvent(player, SkillingActionContext.Product("stat.hunter", output, 1, xp, SkillingProductSource.HunterCatch(trap.kind.npc.asRSCM()))))
            spam("You catch a ${if (trap.kind == Kind.Tecu) "tecu salamander" else "red chinchompa"}.")
        }
    }

    override fun onPostTick(player: Player) {
        val own = traps.values.filter { it.owner === player }
        for (trap in own) {
            val clock = player.currentMapClock
            if (clock >= trap.expires || player.coords.level != trap.loc.coords.level || distance(player.coords, trap.loc.coords) > 64) {
                traps.remove(trap.loc.coords); locs.del(trap.loc, Int.MAX_VALUE); continue
            }
            if (trap.caught || trap.broken || clock < trap.next) continue
            trap.next = clock + 5
            val npc = npcs.findAll(ZoneKey.from(trap.loc.coords), 1).firstOrNull { it.isType(trap.kind.npc) && it.isVisible && distance(it.coords, trap.loc.coords) <= 1 } ?: continue
            val chance = (0.35 + (player.hunterLvl - trap.kind.level) * 0.012).coerceIn(0.35, 0.9)
            val caught = random.randomDouble() < chance
            if (!replace(trap, if (caught) trap.kind.full else trap.kind.failed)) continue
            trap.caught = caught; trap.broken = !caught
            if (caught) npcs.hide(npc, 20)
        }
        if (player.currentMapClock % 10 == 0 && traps.values.none { it.owner === player }) refund(player)
    }

    private fun replace(trap: Trap, symbol: String): Boolean {
        val next = LocInfo(trap.loc.layer, trap.loc.coords, LocEntity(symbol.asRSCM(), trap.loc.shape.id, trap.loc.angle.id))
        if (!locs.add(next, (trap.expires - trap.owner.currentMapClock).coerceAtLeast(1)) { if (traps[next.coords] === trap) traps.remove(next.coords) }) return false
        trap.loc = next
        return true
    }

    override fun cleanup(player: Player) {
        for (trap in traps.values.filter { it.owner === player }) { traps.remove(trap.loc.coords); locs.del(trap.loc, Int.MAX_VALUE) }
        refund(player)
    }

    private fun refund(player: Player) {
        for ((variable, item) in refunds) {
            val count = player.vars[variable].coerceAtLeast(0)
            if (count == 0) continue
            // Escrow survives a crash/logout/full inventory, and is cleared only after restitution.
            if (player.invTransaction(player.inv) { add(select(player.inv), item.asRSCM(), count) }.success) VarPlayerIntMapSetter.set(player, variable, 0)
        }
    }

    private fun distance(a: CoordGrid, b: CoordGrid) = maxOf(kotlin.math.abs(a.x - b.x), kotlin.math.abs(a.z - b.z))
}
