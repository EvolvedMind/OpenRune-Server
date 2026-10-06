package org.rsmod.content.other.treasure.trails

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.IdentityHashMap
import kotlin.random.Random
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.*
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

@Singleton
internal class TrailPuzzleScript @Inject constructor(private val progress: TrailProgress) : PluginScript() {
    private data class OpenPuzzle(val slot: Int, val owner: Int, val light: Boolean, val image: Int)
    private val open = IdentityHashMap<Player, OpenPuzzle>()
    override fun ScriptContext.startup() {
        onIfModalButton("component.trail_slidepuzzle:pieces") { move(it.comsub) }
        for (button in 1..8) onIfModalButton("component.light_puzzle:button$button") { toggle(button - 1) }
        onIfClose("interface.trail_slidepuzzle") { open.remove(player) }
        onIfClose("interface.light_puzzle") { open.remove(player) }
        onPlayerLogout { open.remove(player) }
        for (type in TrailPuzzleItems.types) onOpHeld1(type) {
            val box = it.inventory[it.slot] ?: return@onOpHeld1
            val match = player.inv.objs.mapIndexedNotNull { slot, item ->
                val state = item?.let(progress::state) ?: return@mapIndexedNotNull null
                if (TrailPuzzleItems.owner(state) != box.vars) return@mapIndexedNotNull null
                ActiveTrail(slot, item, state, progress.catalog.clues.getValue(state.row))
            }.firstOrNull()
            if (match == null) { mes("You need the matching clue scroll to use this puzzle box."); return@onOpHeld1 }
            if (match.state.phase == 2) { mes("This puzzle is solved. Return it to the person who gave it to you."); return@onOpHeld1 }
            show(this, match, box.id == "obj.light_puzzle_box".asRSCM())
        }
    }
    fun show(access: ProtectedAccess, active: ActiveTrail, light: Boolean) = with(access) {
        var item = player.inv[active.slot] ?: return@with
        if (item !== active.item) return@with
        if (!TrailPuzzleItems.give(player, active, light)) { mes("You need an inventory space for the puzzle box."); return@with }
        item = player.inv[active.slot] ?: return@with
        val state = progress.state(item) ?: return@with
        if (state.phase !in listOf(1, 6)) return@with
        val image = if (active.clue.tier == TrailTier.MASTER) 4 + state.row % 3 else 1 + state.row % 3
        val board = player.invMap.getOrPut("inv.trail_puzzleinv")
        if (board.objs.filterNotNull().firstOrNull()?.vars != state.encode()) {
            for (i in 0 until board.size) board[i] = null
            if (light) {
                board[0] = InvObj("obj.trail_light_unlit", 1, state.encode())
                board[1] = InvObj("obj.trail_light_unlit", Random.nextInt(1, 256) + 1)
            } else {
                val pieces = pieces(image)
                TrailPuzzles.scramble(Random).forEachIndexed { slot, tile ->
                    if (tile != 24) board[slot] = InvObj(checkNotNull(ServerCacheManager.getItem(pieces[tile])), 1, state.encode())
                }
            }
        }
        if (light) {
            VarPlayerIntMapSetter.set(player, "varp.trail_lights", TrailPuzzles.lightState(state.encode(), board[1]!!.count - 1))
            ifOpenMain("interface.light_puzzle")
        } else {
            VarPlayerIntMapSetter.set(player, "varp.if1", image)
            invTransmit(board)
            ifOpenMain("interface.trail_slidepuzzle")
            ifSetEvents("component.trail_slidepuzzle:pieces", 0..24, IfEvent.Op1)
        }
        open[player] = OpenPuzzle(active.slot, state.encode(), light, image)
    }
    private fun pieces(image: Int): List<Int> {
        val tables = checkNotNull(ServerCacheManager.getEnum("enum.trail_puzzle_images".asRSCM()))
        val table = (tables.values[image] as Number).toInt()
        val entries = checkNotNull(ServerCacheManager.getEnum(table)).values
        return (0..23).map { (entries[it] as Number).toInt() }
    }
    private fun ProtectedAccess.current(): OpenPuzzle? {
        val session = open[player] ?: return null
        val item = player.inv[session.slot] ?: return null
        if (progress.state(item)?.encode() != session.owner) return null
        if (TrailPuzzleItems.owned(player, progress.state(item)!!) == null) return null
        return session
    }
    private fun ProtectedAccess.move(slot: Int) {
        val session = current() ?: return
        if (session.light || slot !in 0..24) return
        val board = player.invMap.getOrPut("inv.trail_puzzleinv")
        val ids = pieces(session.image)
        val tiles = IntArray(25) { i -> board[i]?.let { ids.indexOf(it.id) } ?: 24 }
        if (tiles.any { it < 0 } || !TrailPuzzles.slide(tiles, slot)) return
        for (i in 0..24) board[i] = if (tiles[i] == 24) null else InvObj(checkNotNull(ServerCacheManager.getItem(ids[tiles[i]])), 1, session.owner)
        invTransmit(board)
        if (TrailPuzzles.solved(tiles)) solved(session)
    }
    private fun ProtectedAccess.toggle(button: Int) {
        val session = current() ?: return
        if (!session.light || button !in 0..7) return
        val board = player.invMap.getOrPut("inv.trail_puzzleinv")
        val switches = (board[1]!!.count - 1) xor (1 shl button)
        board[1] = board[1]!!.copy(count = switches + 1)
        VarPlayerIntMapSetter.set(player, "varp.trail_lights", TrailPuzzles.lightState(session.owner, switches))
        if (switches == 0) solved(session)
    }
    private fun ProtectedAccess.solved(session: OpenPuzzle) {
        val item = player.inv[session.slot] ?: return
        if (progress.phase(player, session.slot, item, 2)) {
            val board = player.invMap.getOrPut("inv.trail_puzzleinv")
            val solvedOwner = progress.state(player.inv[session.slot]!!)!!.encode()
            for (slot in board.indices) board[slot]?.let { board[slot] = it.copy(vars = solvedOwner) }
            mes("You have solved the puzzle. Return to the person who gave it to you.")
            open.remove(player)
        }
    }
}
