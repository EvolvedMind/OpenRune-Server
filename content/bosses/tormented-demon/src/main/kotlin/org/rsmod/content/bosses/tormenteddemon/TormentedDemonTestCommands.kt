package org.rsmod.content.bosses.tormenteddemon

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onCommand
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class TormentedDemonTestCommands @Inject constructor(private val access: ProtectedAccessLauncher) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("testtd") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Teleport to the Tormented Demon test area in the Ancient Guthixian Temple"
            cheat { access.launch(player) { telejump(CoordGrid(4072, 4422), TeleportType.Exempt) } }
        }
    }
}
