package org.rsmod.api.player.output

import org.rsmod.api.attr.AttributeKey
import org.rsmod.game.entity.Player

/** Reusable transient FIFO. Only the game thread enqueues, pulses and clears it. */
public object PlayerNotifications {
    private val key: AttributeKey<State> = AttributeKey(temp = true)

    // Rev-240 scripts 3346/3347/3348: expand, 210 client cycles of display, collapse.
    // 350 cycles at 50 Hz = 7 s. Fourteen game ticks leaves a margin before the next popup.
    private const val DISPLAY_TICKS = 14
    private const val DEFAULT_COLOUR = 0xff981f

    public fun enqueue(player: Player, title: String, text: String, colour: Int = DEFAULT_COLOUR) {
        if (invalid(player)) { clear(player); return }
        val state = player.attr[key] ?: State().also { player.attr[key] = it }
        state.pending.addLast(Notification(title, text, colour))
    }

    /** Pulse once per game tick after the player's gameframe has been opened. */
    public fun pulse(player: Player) {
        if (invalid(player)) { clear(player); return }
        val state = player.attr[key] ?: return
        if (state.remaining > 0 && --state.remaining > 0) return
        // Explicitly finish the old timer before drawing the next notification.
        if (state.displaying) player.runClientScript(3348, 6, 0, 0)
        val next = state.pending.removeFirstOrNull()
        if (next == null) { clear(player); return }
        player.runClientScript(3343, next.title, next.text, next.colour)
        state.displaying = true
        state.remaining = DISPLAY_TICKS
    }

    public fun clear(player: Player) { player.attr.remove(key) }

    private fun invalid(player: Player): Boolean =
        player.loggingOut || player.pendingLogout || player.forceDisconnect ||
            player.pendingShutdown || player.clientDisconnected.get()

    private data class Notification(val title: String, val text: String, val colour: Int)
    private class State {
        val pending = ArrayDeque<Notification>()
        var remaining = 0
        var displaying = false
    }
}
