package org.rsmod.content.bosses.doom

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.invtx.*
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invClear
import org.rsmod.api.invtx.invTransfer
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.content.interfaces.collectionlog.CollectionLog
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.inv.Inventory
import org.rsmod.game.type.uncert

internal var Player.lootClaimed by boolVarp("varp.dom_loot_claimed")

@Singleton
internal class DoomLoot
@Inject
constructor(
    private val delves: DoomDelves,
    private val prices: MarketPrices,
    private val rewards: DoomRewards,
    private val instances: InstanceManager,
    private val playerList: PlayerList,
    private val stats: DoomStats,
) {
    /** Returns true when this kill rolled a unique. */
    fun roll(access: StandardNpcAccess): Boolean {
        val npc = access.npc
        val player = killer(access)
        if (player == null) {
            println("[DoomLoot] roll aborted: no killer for npc=${npc.id}")
            return false
        }
        return rollFor(player, npc, recordStats = true)
    }

    fun rollFor(player: Player, npc: Npc, recordStats: Boolean): Boolean {
        val level = delves.currentLevel(player)
        val rolled = rewards.roll(player, npc, level)
        val result = player.invTransaction(earned(player)) {
            val into = select(earned(player))
            for (item in rolled) add(into, item.id, item.count, item.vars, strict = true)
        }
        check(result.success) { "Doom reward escrow could not accept a complete roll" }
        if (recordStats) stats.complete(player, level)
        val uniqueIds = UNIQUES.map { it.asRSCM(RSCMType.OBJ) }.toSet()
        return rolled.any { it.id in uniqueIds }
    }

    fun hasUnique(player: Player): Boolean {
        val uniques = UNIQUES.map { it.asRSCM(RSCMType.OBJ) }.toSet()
        return earned(player).objs.filterNotNull().any { it.id in uniques }
    }

    fun stash(player: Player): Boolean {
        val source = earned(player)
        val rewards = source.objs.filterNotNull().toList()
        if (rewards.isEmpty()) return true
        val result = player.invTransaction(source, claimed(player)) {
            val from = select(source)
            val into = select(claimed(player))
            for ((slot, item) in source.objs.withIndex()) {
                if (item == null) continue
                delete(from, item.id, item.count, slot, strict = true)
                add(into, item.id, item.count, item.vars, strict = true)
            }
        }
        if (!result.success) {
            player.mes("Collect your previous Doom rewards from the chest to make room.")
            return false
        }
        for (obj in rewards) {
            val type = ServerCacheManager.getItem(obj.id) ?: continue
            CollectionLog.grant(player, uncert(type).id, obj.count)
        }
        return true
    }

    fun ProtectedAccess.openChest() {
        if (!stash(player)) return
        player.lootClaimed = true
        openEndLevel()
    }

    fun killer(access: StandardNpcAccess): Player? {
        access.findHero(playerList)?.let { return it }
        access.topDamager(playerList)?.let { return it }
        val session = instances.instanceForNpc(access.npc)?.let(instances::sessionForId) ?: return null
        return playerList.firstOrNull { it.uuid in session.occupants }
    }

    fun earned(player: Player): Inventory = player.invMap.getOrPut(EARNED_INV)

    fun claimed(player: Player): Inventory = player.invMap.getOrPut(CLAIMED_INV)

    fun add(player: Player, obj: String, count: Int): Boolean =
        player.invAdd(earned(player), obj, count).success

    fun reset(player: Player) {
        player.invClear(earned(player))
        player.lootClaimed = false
    }

    fun ProtectedAccess.openEndLevel() {
        invTransmit(earned(player))
        invTransmit(claimed(player))
        ifOpenMainModal(INTERFACE)
        runClientScript(INIT_SCRIPT, delves.currentLevel(player) - 1, if (player.lootClaimed) 1 else 0, 0)
        ifSetEvents("$COMPONENT:btn_claim", 0..1, IfEvent.Op1)
        ifSetEvents("$COMPONENT:btn_descend", 0..1, IfEvent.Op1)
        ifSetEvents("$COMPONENT:btn_leave", 0..1, IfEvent.Op1)
        ifSetEvents("$COMPONENT:btn_inv_all", 0..1, IfEvent.Op1)
        ifSetEvents("$COMPONENT:btn_bank_all", 0..1, IfEvent.Op1)
        ifSetEvents(
            "$COMPONENT:loot_contents",
            claimed(player).indices,
            IfEvent.Op1,
            IfEvent.Op2,
            IfEvent.Op3,
            IfEvent.Op4,
            IfEvent.Op5,
            IfEvent.Op10,
        )
        updateValue()
    }

    fun ProtectedAccess.closeEndLevel() {
        invStopTransmit(earned(player))
        invStopTransmit(claimed(player))
        ifClose()
    }

    suspend fun ProtectedAccess.claim() {
        if (player.lootClaimed) return
        val confirmed =
            confirmOverlay(
                "$COMPONENT:dialogs",
                "Are you sure you want to claim your loot?",
                "Claiming your loot early will <col=ffff00>forfeit your run</col>, not allowing you to proceed any further.",
                "Cancel",
                "Confirm",
            )
        if (!confirmed) return
        if (!stash(player)) return
        player.lootClaimed = true
        runClientScript(CLAIMED_SCRIPT, 1)
        ifClose()
        openEndLevel()
    }

    fun ProtectedAccess.take(slot: Int, count: Int) {
        if (!player.lootClaimed) return
        val pile = claimed(player)
        val obj = pile[slot] ?: return
        player.invTransfer(pile, slot, minOf(count, obj.count), inv, strict = false)
        updateValue()
    }

    fun ProtectedAccess.takeAll(into: Inventory) {
        if (!player.lootClaimed) return
        val pile = claimed(player)
        for (slot in pile.objs.indices) {
            val obj = pile[slot] ?: continue
            val uncert = into == bank
            player.invTransfer(pile, slot, obj.count, into, strict = false, uncert = uncert)
        }
        updateValue()
    }

    fun ProtectedAccess.examine(slot: Int) {
        val pile = if (player.lootClaimed) claimed(player) else earned(player)
        objExamine(pile, slot)
    }

    private fun ProtectedAccess.updateValue() {
        val value = total(earned(player)) + total(claimed(player))
        ifSetText("$COMPONENT:loot_value", "Value: ${"%,d".format(value)} GP")
    }

    private fun ProtectedAccess.total(inv: Inventory): Long =
        inv.objs.filterNotNull().sumOf { obj ->
            val type = ServerCacheManager.getItem(obj.id) ?: return@sumOf 0L
            (prices[uncert(type)] ?: 0).toLong() * obj.count
        }

    private companion object {
        private const val INTERFACE = "interface.dom_end_level_ui"
        private const val COMPONENT = "component.dom_end_level_ui"
        private const val EARNED_INV = "inv.dom_lootpile_during"
        private const val CLAIMED_INV = "inv.dom_lootpile"
        private const val INIT_SCRIPT = 7927
        private const val CLAIMED_SCRIPT = 7928
        private val UNIQUES =
            setOf("obj.avernic_treads", "obj.eye_of_ayak_uncharged", "obj.mokhaiotl_cloth", "obj.dompet")
    }
}
