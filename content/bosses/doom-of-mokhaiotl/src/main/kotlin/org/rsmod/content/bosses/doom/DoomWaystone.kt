package org.rsmod.content.bosses.doom

import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.script.onOpHeld1
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

internal class DoomWaystone
@Inject
constructor(
    private val teleports: PlayerTeleportValidator,
    private val areas: AreaChecker,
    private val collision: CollisionFlagMap,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeld1("obj.dom_teleport_item") { event ->
            if (!canChannel()) return@onOpHeld1
            val origin = coords
            anim("seq.human_castteleport")
            spotanim("spotanim.teleport_casting", height = 92)
            soundSynth("synth.teleport_all")
            delay(4)
            if (coords != origin || event.inventory[event.slot] !== event.obj || !canChannel()) {
                return@onOpHeld1
            }
            val consumption = player.invDel(
                event.inventory, "obj.dom_teleport_item", 1, slot = event.slot,
                autoCommit = false, ignoreVirtualStorage = true,
            )
            if (!consumption.success) return@onOpHeld1
            telejump(DoomArena.LOBBY)
            if (coords != DoomArena.LOBBY) return@onOpHeld1
            consumption.commitAll()
            anim("seq.human_castteleport_reverse")
        }
    }

    private fun ProtectedAccess.canChannel(): Boolean {
        if (player.pendingLogout || player.loggingOut || player.clientDisconnected.get() ||
            player.forceDisconnect || player.hitpoints == 0) return false
        val denial = teleports.validate(player, TeleportType.Standard, areas)
        if (denial != null) {
            mes(denial)
            return false
        }
        if (!collision.isZoneValid(DoomArena.LOBBY)) {
            mes("That teleport destination is unavailable.")
            return false
        }
        return true
    }
}
