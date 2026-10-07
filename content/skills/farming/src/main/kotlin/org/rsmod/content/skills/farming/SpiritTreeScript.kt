package org.rsmod.content.skills.farming

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.invtx.*
import org.rsmod.api.player.events.skilling.OwnedSpiritTreeTravelEvent
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.farmingLvl
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.*
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.module.PluginModule
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SpiritTreeModule : PluginModule() {
    override fun bind() { addSetBinding<PlayerPostTickHook>(SpiritTreeScript::class.java) }
}

/** Owned outdoor spirit patches and their travel route; timestamps persist through logout. */
@Singleton
class SpiritTreeScript @Inject constructor(private val clock: FarmingClock, private val xpMods: XpModifiers) : PluginScript(), PlayerPostTickHook {
    data class Patch(val key: String, val loc: String, val coords: CoordGrid) {
        val state = "varp.spirit_tree_${key}_state"
        val planted = "varp.spirit_tree_${key}_clock"
        val destination = coords.translate(4, 1)
    }
    private val transmit by lazy { patches.associateWith { patch ->
        val type = checkNotNull(ServerCacheManager.getObject(patch.loc.asRSCM()))
        RSCM.getReverseMapping(RSCMType.VARBIT, type.multiVarBit)
    } }

    override fun ScriptContext.startup() {
        for (patch in patches) {
            onOpLoc1(patch.loc) { if (it.loc.coords == patch.coords) interact(patch) }
            onOpLoc3(patch.loc) { if (it.loc.coords == patch.coords) { refresh(player); mes(description(player, patch)) } }
            onOpLoc5(patch.loc) { if (it.loc.coords == patch.coords) remove(patch) }
            onOpLocU(patch.loc) {
                if (it.loc.coords != patch.coords || inv[it.invSlot]?.id != it.objType.id) return@onOpLocU
                when (it.objType.internalName) {
                    SAPLING -> plant(patch, it.invSlot)
                    "obj.spade" -> remove(patch)
                    "obj.rake" -> interact(patch)
                }
            }
        }
        // Native public tree Travel operations form the departure points for owned destinations.
        onOpNpc1("npc.treevillage_small_spirittree") { travel() }
        onOpNpc1("npc.treevillage_spirittree") { travel() }
        for (type in ServerCacheManager.getNpcs().values.filter { it.name.equals("Spirit tree", true) }) {
            val op = (1..5).firstOrNull { type.actions.getOpOrNull(it - 1).equals("Travel", true) } ?: continue
            val symbol = RSCM.getReverseMapping(RSCMType.NPC, type.id)
            when (op) {
                1 -> onOpNpc1(symbol) { travel() }
                2 -> onOpNpc2(symbol) { travel() }
                3 -> onOpNpc3(symbol) { travel() }
                4 -> onOpNpc4(symbol) { travel() }
                5 -> onOpNpc5(symbol) { travel() }
            }
        }
        for (type in ServerCacheManager.getObjects().values.filter { it.name.equals("Spirit Tree", true) && it.id !in (8338..8383) }) {
            val op = (1..5).firstOrNull { type.actions.getOpOrNull(it - 1).equals("Travel", true) } ?: continue
            val symbol = RSCM.getReverseMapping(RSCMType.LOC, type.id)
            when (op) {
                1 -> onOpLoc1(symbol) { travel() }
                2 -> onOpLoc2(symbol) { travel() }
                3 -> onOpLoc3(symbol) { travel() }
                4 -> onOpLoc4(symbol) { travel() }
                5 -> onOpLoc5(symbol) { travel() }
            }
        }
        onOpHeldU("obj.spirit_tree_seed", "obj.plantpot_compost") {
            if (inv[it.firstSlot]?.id != it.first.id || inv[it.secondSlot]?.id != it.second.id || inv.count("obj.trowel") == 0) return@onOpHeldU
            if (player.farmingLvl < 83) { mes("You need Farming 83 to plant a spirit seed."); return@onOpHeldU }
            if (player.invTransaction(inv) {
                val target = select(inv); delete(target, it.first.id, 1, it.firstSlot); delete(target, it.second.id, 1, it.secondSlot); add(target, SEEDLING.asRSCM(), 1)
            }.success) mes("Water the seedling, then let it grow into a spirit sapling.")
        }
        for ((charges, can) in WATERING_CANS.withIndex()) if (charges > 0) onOpHeldU(can, SEEDLING) {
            if (inv[it.firstSlot]?.id != it.first.id || inv[it.secondSlot]?.id != it.second.id) return@onOpHeldU
            if (player.invTransaction(inv) {
                val target = select(inv); delete(target, it.first.id, 1, it.firstSlot); delete(target, it.second.id, 1, it.secondSlot)
                add(target, WATERING_CANS[charges - 1].asRSCM(), 1); add(target, WATERED.asRSCM(), 1, clock.minute())
            }.success) { anim(ANIM_WATER); mes("You water the seedling. It will become a sapling in five minutes.") }
        }
        onPlayerLogin { refresh(player) }
    }

    override fun onPostTick(player: Player) { if (player.currentMapClock % 50 == 0) refresh(player) }

    fun refresh(player: Player) {
        for (patch in patches) {
            if (player.coords.level != 0 || player.coords.chebyshevDistance(patch.coords) > 32) continue
            val planted = player.vars[patch.planted]
            val state = player.vars[patch.state]
            val value = if (planted == 0) (state and 3) else if (state and CHECKED != 0) 20 else {
                val elapsed = (clock.minute() - planted).coerceAtLeast(0)
                if (elapsed >= GROWTH_MINUTES) 43 else 8 + (elapsed / 320).coerceAtMost(11)
            }
            VarPlayerIntMapSetter.set(player, transmit.getValue(patch), value)
        }
        for ((slot, item) in player.inv.objs.withIndex()) {
            if (item == null || item.id != WATERED.asRSCM()) continue
            if (item.vars == 0) {
                if (player.inv[slot] === item) player.invTransaction(player.inv) { val target = select(player.inv); delete(target, item.id, 1, slot); add(target, item.id, 1, clock.minute(), slot) }
            } else if (clock.minute() - item.vars >= 5 && player.inv[slot] === item) {
                player.invTransaction(player.inv) { val target = select(player.inv); delete(target, item.id, 1, slot); add(target, SAPLING.asRSCM(), 1, slot = slot) }
            }
        }
    }

    private suspend fun ProtectedAccess.interact(patch: Patch) {
        refresh(player)
        val planted = player.vars[patch.planted]
        val state = player.vars[patch.state]
        if (planted == 0) {
            if (state and 3 == 3) { mes("The patch is empty. Use a spirit sapling on it."); return }
            if (inv.count("obj.rake") == 0) { mes("You need a rake."); return }
            val start = coords
            var cleared = state and 3
            while (cleared < 3) {
                anim(ANIM_RAKE); delay(3)
                if (coords != start || player.vars[patch.state] != cleared || inv.count("obj.rake") == 0) return
                if (!player.invTransaction(inv) { add(select(inv), "obj.weeds".asRSCM(), 1) }.success) return
                cleared++
                VarPlayerIntMapSetter.set(player, patch.state, cleared)
                statAdvance("stat.farming", 4.0 * xpMods.get(player, "stat.farming")); refresh(player)
            }
            resetAnim(); return
        }
        if (clock.minute() - planted < GROWTH_MINUTES) { mes(description(player, patch)); return }
        if (state and CHECKED == 0) {
            VarPlayerIntMapSetter.set(player, patch.state, state or CHECKED)
            statAdvance("stat.farming", 19301.8 * xpMods.get(player, "stat.farming"))
            refresh(player); mes("Your spirit tree is healthy. You can now travel using it."); return
        }
        travel()
    }

    private suspend fun ProtectedAccess.plant(patch: Patch, slot: Int) {
        if (player.farmingLvl < 83) { mes("You need Farming 83 to plant a spirit tree."); return }
        val limit = if (player.farmingLvl >= 99) 5 else if (player.farmingLvl >= 91) 2 else 1
        if (patches.count { player.vars[it.planted] != 0 } >= limit) { mes("You can plant $limit spirit tree${if (limit == 1) "" else "s"} at your level."); return }
        if (player.vars[patch.planted] != 0 || player.vars[patch.state] and 3 != 3) { mes("Rake the empty patch first."); return }
        if (inv.count("obj.spade") == 0) { mes("You need a spade to plant a sapling."); return }
        val original = inv[slot] ?: return
        if (original.id != SAPLING.asRSCM()) return
        val start = coords
        anim(ANIM_PLANT); delay(3); resetAnim()
        if (inv[slot] !== original || coords != start || player.vars[patch.planted] != 0 || inv.count("obj.spade") == 0) return
        if (!player.invTransaction(inv) { val target = select(inv); delete(target, original.id, 1, slot); add(target, "obj.plantpot_empty".asRSCM(), 1) }.success) return
        VarPlayerIntMapSetter.set(player, patch.planted, clock.minute())
        VarPlayerIntMapSetter.set(player, patch.state, 3)
        statAdvance("stat.farming", 199.5 * xpMods.get(player, "stat.farming")); refresh(player)
        mes("You plant the spirit sapling. It grows while you are offline too.")
    }

    private suspend fun ProtectedAccess.remove(patch: Patch) {
        if (inv.count("obj.spade") == 0 || player.vars[patch.planted] == 0) { mes("You need a spade and a planted tree."); return }
        if (menu("Remove your spirit tree?", "Keep the tree", "Remove the tree") != 1) return
        if (coords.chebyshevDistance(patch.coords) > 4 || inv.count("obj.spade") == 0) return
        VarPlayerIntMapSetter.set(player, patch.planted, 0)
        VarPlayerIntMapSetter.set(player, patch.state, 3); refresh(player)
        mes("You clear the spirit tree patch.")
    }

    private suspend fun ProtectedAccess.travel() {
        val ready = patches.filter { player.vars[it.planted] > 0 && player.vars[it.state] and CHECKED != 0 }
        if (ready.isEmpty()) { mes("Plant a spirit tree and check its health before travelling to it."); return }
        val selected = ready.getOrNull(menu("Spirit trees", true, ready.map { it.key.replace('_', ' ').replaceFirstChar(Char::uppercase) })) ?: return
        if (player.vars[selected.planted] <= 0 || player.vars[selected.state] and CHECKED == 0) return
        teleport(selected.destination)
        if (coords == selected.destination) publish(OwnedSpiritTreeTravelEvent(player, selected.destination))
    }

    private fun description(player: Player, patch: Patch): String {
        if (player.vars[patch.planted] == 0) return "This spirit tree patch is ${if (player.vars[patch.state] and 3 == 3) "empty" else "covered in weeds"}."
        val remaining = (GROWTH_MINUTES - (clock.minute() - player.vars[patch.planted])).coerceAtLeast(0)
        return if (remaining > 0) "Your spirit tree is growing. Approximately ${remaining / 60}h ${remaining % 60}m remaining." else "Your spirit tree is fully grown."
    }

    companion object {
        const val GROWTH_MINUTES = 3520
        const val CHECKED = 4
        const val SAPLING = "obj.plantpot_spirit_tree_sapling"
        const val SEEDLING = "obj.plantpot_spirit_tree_seed"
        const val WATERED = "obj.plantpot_spirit_tree_seed_watered"
        val patches = listOf(
            Patch("port_sarim", "loc.farming_spirit_tree_patch_1", CoordGrid(3059, 3257, 0)),
            Patch("etceteria", "loc.farming_spirit_tree_patch_2", CoordGrid(2612, 3857, 0)),
            Patch("brimhaven", "loc.farming_spirit_tree_patch_3", CoordGrid(2801, 3202, 0)),
            Patch("hosidius", "loc.farming_spirit_tree_patch_4", CoordGrid(1692, 3541, 0)),
            Patch("farming_guild", "loc.farming_spirit_tree_patch_5", CoordGrid(1252, 3749, 0)),
        )
    }
}
