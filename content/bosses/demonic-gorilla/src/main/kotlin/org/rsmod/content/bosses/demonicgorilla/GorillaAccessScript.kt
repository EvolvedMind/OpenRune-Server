package org.rsmod.content.bosses.demonicgorilla

import dev.openrune.rscm.RSCM.asRSCM
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.*
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class GorillaAccessScript @Inject constructor(private val locs: LocRepository, private val access: ProtectedAccessLauncher) : PluginScript() {
    private val entrance by lazy { LocInfo(2, ENTRANCE, LocEntity(ACCESS.asRSCM(), 10, 0)) }
    override fun ScriptContext.startup() {
        onGameStartup { check(locs.add(entrance, Int.MAX_VALUE)) { "Could not create the gorilla cavern entrance" } }
        for (symbol in listOf(ACCESS, "loc.mm2_cavern_entrance")) {
            onOpLoc1(symbol) {
                vars["varp.gorilla_entry_return"] = coords.packed
                telejump(CAVERN, TeleportType.Exempt)
            }
        }
        onOpLoc1("loc.mm2_cave_boss_exit") {
            val returnCoord = vars["varp.gorilla_entry_return"]
            telejump(if (returnCoord == 0) LOBBY else CoordGrid(returnCoord), TeleportType.Exempt)
            vars["varp.gorilla_entry_return"] = 0
        }
        onCommand("testgorillas") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Teleport to the gorilla cavern entrance shown in the test screenshot"
            cheat { access.launch(player) { telejump(LOBBY, TeleportType.Exempt) } }
        }
    }
    override fun ScriptContext.shutdown() { locs.del(entrance, Int.MAX_VALUE) }
    companion object {
        const val ACCESS = "loc.gorilla_cavern_access"
        val LOBBY = CoordGrid(2108, 5654)
        val ENTRANCE = CoordGrid(2106, 5652)
        val CAVERN = CoordGrid(2076, 5646)
    }
}
