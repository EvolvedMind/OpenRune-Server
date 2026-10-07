package org.rsmod.content.skills.firemaking

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.events.skilling.LampRepairedEvent
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onGameStartup
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DorgeshLampScript @Inject constructor(private val locs: LocRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        onGameStartup { lamps.forEach(::breakLamp) }
        for (lamp in listOf("loc.dorgesh_lamp_stand_light_off", "loc.dorgesh_lamp_stand_light_close_off")) {
            onOpLoc1(lamp) { repair(it.loc) }
            onOpLocU(lamp, "obj.dorgesh_light_bulb") { repair(it.loc) }
        }
    }
    private suspend fun ProtectedAccess.repair(loc: BoundLocInfo) {
        arriveDelay(); faceLoc(loc)
        if (stat("stat.firemaking") < 52) { mes("You need level 52 Firemaking to repair this lamp."); return }
        if (!inv.contains("obj.dorgesh_light_bulb")) { mes("You need a light orb to repair the lamp."); return }
        val start = coords
        anim("seq.dorgesh_light_change_human"); delay(3)
        if (coords != start || stat("stat.firemaking") < 52 || locs.findExact(loc.coords, ServerCacheManager.getObject(loc.id)!!) == null) return
        if (!player.invTransaction(inv) { delete(select(inv), "obj.dorgesh_light_bulb".asRSCM(), 1) }.success) return
        locs.add(loc.coords, "loc.dorgesh_lamp_stand_light_on", 600, loc.angle, loc.shape, onDespawn = { breakLamp(loc.coords) })
        statAdvance("stat.firemaking", 1000.0)
        publish(LampRepairedEvent(player, loc.coords))
        mes("You fit a new light orb and repair the lamp.")
    }
    private fun breakLamp(coords: CoordGrid) {
        val base = ServerCacheManager.getObject("loc.dorgesh_lamp_stand_light".asRSCM())!!
        val loc = locs.findExact(coords, base) ?: return
        locs.change(loc, ServerCacheManager.getObject("loc.dorgesh_lamp_stand_light_off".asRSCM())!!, Int.MAX_VALUE)
    }
    companion object {
        val lamps = listOf(CoordGrid(2699, 5294, 1), CoordGrid(2699, 5300, 1), CoordGrid(2711, 5278, 1), CoordGrid(2712, 5270, 1),
            CoordGrid(2741, 5287, 1), CoordGrid(2742, 5294, 1), CoordGrid(2744, 5282, 1), CoordGrid(2695, 5289, 2), CoordGrid(2699, 5294, 2), CoordGrid(2742, 5261, 2))
    }
}
