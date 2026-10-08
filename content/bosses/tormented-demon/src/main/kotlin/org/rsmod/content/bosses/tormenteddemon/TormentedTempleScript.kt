package org.rsmod.content.bosses.tormenteddemon

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.*
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.entity.Player
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

internal class TormentedTempleScript @Inject constructor(
    private val teleports: PlayerTeleportValidator,
    private val areas: AreaChecker,
    private val collision: CollisionFlagMap,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeld1("obj.teleportscroll_guthixian_temple") { event ->
            if (!allowed()) return@onOpHeld1
            val denial = teleports.validate(player, TeleportType.Standard, areas)
            if (denial != null) { mes(denial); return@onOpHeld1 }
            if (!collision.isZoneValid(TELEPORT_DESTINATION)) return@onOpHeld1
            anim("seq.teleport_scroll_open")
            delay(2)
            if (inv[event.slot] !== event.obj) return@onOpHeld1
            val finalDenial = teleports.validate(player, TeleportType.Standard, areas)
            if (finalDenial != null) { mes(finalDenial); return@onOpHeld1 }
            if (player.invDel(inv, "obj.teleportscroll_guthixian_temple", 1, slot = event.slot).success) {
                telejump(TELEPORT_DESTINATION)
                indicator(player)
            }
        }
        onOpLoc1("loc.luc2_gt_temple_wall_climb1") { e ->
            anim("seq.human_climbing")
            delay(2)
            telejump(CoordGrid(e.loc.coords.x, e.loc.coords.z - 1, 1), TeleportType.Exempt)
        }
        onOpLoc1("loc.luc2_gt_temple_wall_climb2") { e ->
            anim("seq.human_climbing")
            delay(2)
            telejump(CoordGrid(e.loc.coords.x, e.loc.coords.z - 1, 2), TeleportType.Exempt)
        }
        onOpLoc1("loc.luc2_gt_climb_trigger") { e ->
            anim("seq.human_climbing_down")
            delay(2)
            telejump(CoordGrid(e.loc.coords.x, e.loc.coords.z + 1, (e.loc.coords.level - 1).coerceAtLeast(0)), TeleportType.Exempt)
        }
        onOpLoc1("loc.luc2_gt_main_skull_bottom_open") {
            if (!allowed()) return@onOpLoc1
            anim("seq.luc2_crawl_into_skull")
            delay(2)
            telejump(CAVE_ENTRANCE, TeleportType.Exempt)
            indicator(player)
        }
        onOpLoc1("loc.luc2_gt_wallkit_entrance_hole") {
            anim("seq.luc2_crawl_into_skull")
            delay(2)
            telejump(CoordGrid(4063, 4547, 2), TeleportType.Exempt)
            indicator(player)
        }
        for (creature in listOf("npc.tog_light_creature", "npc.tog_light_creature_op", "npc.tog_light_creature_noop")) {
            onOpNpcU(checkNotNull(ServerCacheManager.getNpc(creature.asRSCM())), checkNotNull(ServerCacheManager.getItem("obj.tog_sapphire_lantern_lit".asRSCM()))) { transport() }
        }
        onPlayerLogin { indicator(player) }
        onPlayerCoordsChanged { indicator(player) }
    }
    private fun ProtectedAccess.allowed(): Boolean {
        if (QuestRequirements.hasCompleted(player, "quest.whileguthixsleeps")) return true
        mes("You must complete While Guthix Sleeps to enter the Ancient Guthixian Temple.")
        return false
    }
    private suspend fun ProtectedAccess.transport() {
        if (!allowed()) return
        if (choice2("Travel to the Ancient Guthixian Temple", true, "Stay here", false)) {
            delay(2)
            telejump(ENTRANCE, TeleportType.Exempt)
        }
    }
    private fun indicator(player: Player) {
        VarPlayerIntMapSetter.set(player, "varbit.td_multiway_indicator", if (TormentedTemple.contains(player.coords)) TormentedTemple.limit(player.coords) else 0)
    }
    companion object {
        val TELEPORT_DESTINATION = CoordGrid(4061, 4464)
        val ENTRANCE = CoordGrid(4063, 4557)
        val CAVE_ENTRANCE = CoordGrid(4062, 4466)
    }
}
