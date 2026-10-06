package org.rsmod.content.bosses.doom

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onCommand
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DoomSimCommand @Inject constructor(
    private val loot: DoomTestLoot,
    private val access: ProtectedAccessLauncher,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("testdoom") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Teleport to the Doom of Mokhaiotl pre-lair"
            cheat { access.launch(player) { telejump(DoomArena.LOBBY, TeleportType.Exempt) } }
        }
        onCommand("doomsim") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Doom ground loot samples: count [delve]"
            cheat {
                val count = args.firstOrNull()?.toIntOrNull()
                val level = if (args.size == 1) 8 else args.getOrNull(1)?.toIntOrNull()
                if (args.size !in 1..2 || count == null || count !in 1..1000 || level == null || level !in 1..1000)
                    player.mes("Use ::doomsim count [delve], both 1-1000. Example: ::doomsim 100 8")
                else { loot.generate(player, count, level); player.mes("Generated Doom loot x$count at delve $level.") }
            }
        }
    }
}
