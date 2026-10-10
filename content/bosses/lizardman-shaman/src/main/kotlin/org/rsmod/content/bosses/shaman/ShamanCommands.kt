package org.rsmod.content.bosses.shaman

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onCommand
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class ShamanCommands @Inject constructor(private val access: ProtectedAccessLauncher) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("test") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Teleport to the Lizardman Shaman canyon with ::test shamans"
            cheat {
                if (args.singleOrNull()?.lowercase() !in setOf("shaman", "shamans")) {
                    player.mes("Use ::test shamans")
                } else access.launch(player) { telejump(CoordGrid(1451, 3696, 0), TeleportType.Exempt) }
            }
        }
    }
}
