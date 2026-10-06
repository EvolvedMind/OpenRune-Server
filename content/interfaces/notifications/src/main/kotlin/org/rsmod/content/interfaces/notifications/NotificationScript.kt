package org.rsmod.content.interfaces.notifications

import jakarta.inject.Inject
import dev.or2.central.account.Rights
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.output.PlayerNotifications
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onCommand
import org.rsmod.game.entity.PlayerList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class NotificationScript @Inject constructor(private val players: PlayerList) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.LateCycle> { for (player in players) PlayerNotifications.pulse(player) }
        onPlayerLogout { PlayerNotifications.clear(player) }
        onEvent<GameLifecycle.Shutdown> { for (player in players) PlayerNotifications.clear(player) }
        onCommand("testnotify") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Test three sequential popups without changing account progress"
            cheat {
                PlayerNotifications.enqueue(player, "Collection Log", "Test notification 1/3")
                PlayerNotifications.enqueue(player, "Combat Achievement", "Test notification 2/3")
                PlayerNotifications.enqueue(player, "Quest", "Test notification 3/3")
            }
        }
    }
}
