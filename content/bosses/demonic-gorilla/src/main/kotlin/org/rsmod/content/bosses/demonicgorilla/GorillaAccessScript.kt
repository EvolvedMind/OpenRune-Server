package org.rsmod.content.bosses.demonicgorilla

import dev.openrune.rscm.RSCM.asRSCM
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.*
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class GorillaAccessScript @Inject constructor(private val locs: LocRepository, private val access: ProtectedAccessLauncher) : PluginScript() {
    private val ownedLocs by lazy {
        listOf(
            LocInfo(2, ENTRANCE, LocEntity(HOLE.asRSCM(), 10, 0)),
            LocInfo(2, SIGN_COORD, LocEntity(SIGN.asRSCM(), 10, 2)),
            LocInfo(2, ROPE_COORD, LocEntity(ROPE.asRSCM(), 10, 0)),
        )
    }
    override fun ScriptContext.startup() {
        onGameStartup {
            for (loc in ownedLocs) check(locs.add(loc, Int.MAX_VALUE)) { "Could not create gorilla access object ${loc.id}" }
        }
        for (symbol in listOf(HOLE, "loc.mm2_cavern_entrance")) {
            onOpLoc1(symbol) {
                arriveDelay()
                anim("seq.human_reachforladder")
                delay(1)
                if (player.pendingLogout || player.hitpoints == 0) return@onOpLoc1
                telejump(CAVERN, TeleportType.Exempt)
            }
        }
        for (symbol in listOf(ROPE, "loc.mm2_cave_boss_exit")) {
            onOpLoc1(symbol) {
                arriveDelay()
                anim("seq.human_reachforladder")
                delay(1)
                if (player.pendingLogout || player.hitpoints == 0) return@onOpLoc1
                telejump(LOBBY, TeleportType.Exempt)
                vars["varp.gorilla_entry_return"] = 0
            }
        }
        onOpLoc1(SIGN) { mes("Danger! Demonic and tortured gorillas lurk below. Make sure you are prepared.") }
        onCommand("testgorillas") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Teleport to the gorilla cavern entrance shown in the test screenshot"
            cheat { access.launch(player) { telejump(LOBBY, TeleportType.Exempt) } }
        }
    }
    override fun ScriptContext.shutdown() { for (loc in ownedLocs) locs.del(loc, Int.MAX_VALUE) }
    companion object {
        // Keep the installed custom ID; its cavern-arch appearance becomes the requested rope.
        const val ROPE = "loc.gorilla_cavern_access"
        const val HOLE = "loc.gorilla_cavern_hole"
        const val SIGN = "loc.gorilla_cavern_danger_sign"
        val LOBBY = CoordGrid(2428, 3521)
        val ENTRANCE = CoordGrid(2428, 3522)
        val SIGN_COORD = CoordGrid(2429, 3521)
        val ROPE_COORD = CoordGrid(2108, 5651)
        val CAVERN = CoordGrid(2108, 5654)
    }
}
