package org.rsmod.content.other.commands

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

private const val INTERFACE = "interface.commands_menu"
private const val COMP_TITLE = "component.commands_menu:title"
private const val COMP_STATUS = "component.commands_menu:status"
private const val COMP_CLOSE = "component.commands_menu:close"
private const val COMP_PREV = "component.commands_menu:previous"
private const val COMP_NEXT = "component.commands_menu:next"
private val COMMAND_ROWS =
    arrayOf(
        "component.commands_menu:row_0",
        "component.commands_menu:row_1",
        "component.commands_menu:row_2",
        "component.commands_menu:row_3",
        "component.commands_menu:row_4",
        "component.commands_menu:row_5",
        "component.commands_menu:row_6",
        "component.commands_menu:row_7",
        "component.commands_menu:row_8",
        "component.commands_menu:row_9",
        "component.commands_menu:row_10",
        "component.commands_menu:row_11"
    )

/**
 * Native client-side ::commands browser.
 *
 * The interface itself lives in the OpenRune commands plugin-pack as commands_menu.if3 and inherits
 * the native skill-guide frame. Cache builds publish stable RSCM names for all components.
 */
class NativeCommandsInterface
@Inject
constructor(
    private val protectedAccess: ProtectedAccessLauncher,
    private val cheatCommandMap: CheatCommandMap,
) : PluginScript() {
    private val commandPages = mutableMapOf<String, Int>()

    private fun visibleCommands(player: Player): List<Pair<String, String>> =
        cheatCommandMap.commands.entries
            .asSequence()
            .filter { it.key != "commands" }
            .filter { (_, handler) ->
                val required = handler.requiredRights
                required == null || player.modLevel.isAtLeast(required)
            }
            .map { it.key to (it.value.desc ?: "") }
            .sortedBy { it.first }
            .toList()

    private fun ProtectedAccess.openCommands() {
        val commands = visibleCommands(player)
        val pageSize = COMMAND_ROWS.size
        val pageCount = maxOf(1, (commands.size + pageSize - 1) / pageSize)
        val requestedPage = commandPages[player.username] ?: 0
        val page = requestedPage.coerceIn(0, pageCount - 1)
        commandPages[player.username] = page
        val start = page * pageSize

        ifOpenMainModal(INTERFACE)
        ifSetText(COMP_TITLE, "Commands")
        ifSetText(
            COMP_STATUS,
            commands.size.toString() +
                " commands - page " +
                (page + 1) +
                "/" +
                pageCount +
                " - " +
                player.modLevel.name,
        )

        COMMAND_ROWS.forEachIndexed { rowIndex, component ->
            val entry = commands.getOrNull(start + rowIndex)
            val text =
                if (entry == null) {
                    ""
                } else if (entry.second.isBlank()) {
                    "::" + entry.first
                } else {
                    "::" + entry.first + "  " + entry.second
                }
            ifSetText(component, text)
        }
    }

    override fun ScriptContext.startup() {
        onCommand("commands") {
            desc = "Open native commands interface"
            cheat {
                commandPages[player.username] = 0
                protectedAccess.launch(player) { openCommands() }
            }
        }

        onIfClose(INTERFACE) {
            commandPages.remove(player.username)
        }

        onIfModalButton(COMP_CLOSE) {
            commandPages.remove(player.username)
            ifClose()
        }

        onIfModalButton(COMP_PREV) {
            val current = commandPages[player.username] ?: 0
            commandPages[player.username] = (current - 1).coerceAtLeast(0)
            openCommands()
        }

        onIfModalButton(COMP_NEXT) {
            val pageSize = COMMAND_ROWS.size
            val pageCount = maxOf(1, (visibleCommands(player).size + pageSize - 1) / pageSize)
            val current = commandPages[player.username] ?: 0
            commandPages[player.username] = (current + 1).coerceAtMost(pageCount - 1)
            openCommands()
        }

        onIfModalButton(COMMAND_ROWS[0]) {
            val entries = visibleCommands(player)
            val page = commandPages[player.username] ?: 0
            val commandName = entries.getOrNull(page * COMMAND_ROWS.size + 0)?.first
            if (commandName != null) {
                cheatCommandMap.execute(player, commandName, emptyList())
            }
        }

        onIfModalButton(COMMAND_ROWS[1]) {
            val entries = visibleCommands(player)
            val page = commandPages[player.username] ?: 0
            val commandName = entries.getOrNull(page * COMMAND_ROWS.size + 1)?.first
            if (commandName != null) {
                cheatCommandMap.execute(player, commandName, emptyList())
            }
        }

        onIfModalButton(COMMAND_ROWS[2]) {
            val entries = visibleCommands(player)
            val page = commandPages[player.username] ?: 0
            val commandName = entries.getOrNull(page * COMMAND_ROWS.size + 2)?.first
            if (commandName != null) {
                cheatCommandMap.execute(player, commandName, emptyList())
            }
        }

        onIfModalButton(COMMAND_ROWS[3]) {
            val entries = visibleCommands(player)
            val page = commandPages[player.username] ?: 0
            val commandName = entries.getOrNull(page * COMMAND_ROWS.size + 3)?.first
            if (commandName != null) {
                cheatCommandMap.execute(player, commandName, emptyList())
            }
        }

        onIfModalButton(COMMAND_ROWS[4]) {
            val entries = visibleCommands(player)
            val page = commandPages[player.username] ?: 0
            val commandName = entries.getOrNull(page * COMMAND_ROWS.size + 4)?.first
            if (commandName != null) {
                cheatCommandMap.execute(player, commandName, emptyList())
            }
        }

        onIfModalButton(COMMAND_ROWS[5]) {
            val entries = visibleCommands(player)
            val page = commandPages[player.username] ?: 0
            val commandName = entries.getOrNull(page * COMMAND_ROWS.size + 5)?.first
            if (commandName != null) {
                cheatCommandMap.execute(player, commandName, emptyList())
            }
        }

        onIfModalButton(COMMAND_ROWS[6]) {
            val entries = visibleCommands(player)
            val page = commandPages[player.username] ?: 0
            val commandName = entries.getOrNull(page * COMMAND_ROWS.size + 6)?.first
            if (commandName != null) {
                cheatCommandMap.execute(player, commandName, emptyList())
            }
        }

        onIfModalButton(COMMAND_ROWS[7]) {
            val entries = visibleCommands(player)
            val page = commandPages[player.username] ?: 0
            val commandName = entries.getOrNull(page * COMMAND_ROWS.size + 7)?.first
            if (commandName != null) {
                cheatCommandMap.execute(player, commandName, emptyList())
            }
        }

        onIfModalButton(COMMAND_ROWS[8]) {
            val entries = visibleCommands(player)
            val page = commandPages[player.username] ?: 0
            val commandName = entries.getOrNull(page * COMMAND_ROWS.size + 8)?.first
            if (commandName != null) {
                cheatCommandMap.execute(player, commandName, emptyList())
            }
        }

        onIfModalButton(COMMAND_ROWS[9]) {
            val entries = visibleCommands(player)
            val page = commandPages[player.username] ?: 0
            val commandName = entries.getOrNull(page * COMMAND_ROWS.size + 9)?.first
            if (commandName != null) {
                cheatCommandMap.execute(player, commandName, emptyList())
            }
        }

        onIfModalButton(COMMAND_ROWS[10]) {
            val entries = visibleCommands(player)
            val page = commandPages[player.username] ?: 0
            val commandName = entries.getOrNull(page * COMMAND_ROWS.size + 10)?.first
            if (commandName != null) {
                cheatCommandMap.execute(player, commandName, emptyList())
            }
        }

        onIfModalButton(COMMAND_ROWS[11]) {
            val entries = visibleCommands(player)
            val page = commandPages[player.username] ?: 0
            val commandName = entries.getOrNull(page * COMMAND_ROWS.size + 11)?.first
            if (commandName != null) {
                cheatCommandMap.execute(player, commandName, emptyList())
            }
        }
    }
}
