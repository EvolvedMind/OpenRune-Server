package org.rsmod.content.bosses.tormenteddemon

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcDeathDropContext
import org.rsmod.api.death.NpcDeathDropHook
import org.rsmod.api.player.hook.PlayerObjTakeValidateHook
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.*
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.*
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.obj.Obj
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Ground consumables remain player-owned. Persistent native inventories store coords, uses and online ticks. */
@Singleton
internal class TormentedSmouldering @Inject constructor(private val repo: ObjRepository, private val random: GameRandom) : NpcDeathDropHook, PlayerPostTickHook {
    private val ground = mutableMapOf<Player, MutableMap<Int, Obj>>()
    private val dead = mutableSetOf<Player>()
    private val hearts = mutableSetOf<Player>()
    override fun tryConsume(context: NpcDeathDropContext): Boolean {
        if (context.dropType.id !in types) return false
        val player = context.hero
        val items = player.invMap.getOrPut(ITEMS)
        val meta = player.invMap.getOrPut(META)
        val slot = items.objs.indexOfFirst { it == null }
        if (slot < 0) return false
        items[slot] = InvObj(context.dropType, vars = context.dropCoords.packed)
        meta[slot] = InvObj(context.dropType, vars = 1000 or ((if (context.dropType.id == "obj.smouldering_pile_of_flesh".asRSCM()) 4 else 1) shl 10))
        spawn(player, slot)
        return true
    }
    private fun spawn(player: Player, slot: Int) {
        val item = player.invMap[ITEMS]?.get(slot) ?: return
        val obj = repo.add(item.id.let { checkNotNull(dev.openrune.ServerCacheManager.getItem(it)) }, CoordGrid(item.vars), Int.MAX_VALUE, player, reveal = Int.MAX_VALUE)
        ground.getOrPut(player) { mutableMapOf() }[slot] = obj
    }
    fun login(player: Player) {
        if (ground.containsKey(player)) return
        val inv = player.invMap[ITEMS]
        if (inv != null) for (slot in inv.objs.indices) if (inv[slot] != null) spawn(player, slot)
        if (player.vars[HEART] > 0) maintainHeart(player)
        syncBuffs(player)
    }
    fun logout(player: Player) {
        ground.remove(player)?.values?.forEach { repo.del(it, Int.MAX_VALUE) }
        dead.remove(player)
        hearts.remove(player)
        StatBoostDecayPrevention.removeSource(player, HEART_SOURCE)
    }
    fun shutdown() { (ground.keys + hearts + dead).toList().forEach(::logout) }
    fun consume(player: Player, obj: Obj) {
        val slot = ground[player]?.entries?.firstOrNull { it.value === obj }?.key ?: return
        if (repo.findAll(obj.coords).none { it === obj }) return
        val item = player.invMap[ITEMS]?.get(slot) ?: return
        val meta = player.invMap[META]?.get(slot) ?: return
        if (item.id != obj.type) return
        if (obj.type == "obj.smouldering_pile_of_flesh".asRSCM()) {
            val heal = (player.baseHitpointsLvl * 18 / 99).coerceAtLeast(1)
            val cap = player.baseHitpointsLvl + heal
            if (player.hitpoints >= cap) { player.mes("You are too full to eat from the flesh."); return }
            player.statAdd("stat.hitpoints", minOf(heal, cap - player.hitpoints), 0)
            val uses = (meta.vars ushr 10) - 1
            if (uses > 0) player.invMap[META]!![slot] = meta.copy(vars = (meta.vars and 1023) or (uses shl 10))
            else remove(player, slot)
            player.mes("Even as the flesh burns your throat, you feel your wounds begin to mend.")
        } else {
            remove(player, slot)
            val heart = obj.type == "obj.smouldering_heart".asRSCM()
            VarPlayerIntMapSetter.set(player, if (heart) HEART else GLAND, if (heart) 200 else 80)
            if (heart) { boost(player); maintainHeart(player); player.anim("seq.human_cast_selfimbue"); player.spotanim("spotanim.imbued_heart_impact") }
            syncBuffs(player)
            player.mes(if (heart) "You crush the heart. Dark energy swirls around you..." else "You crush the gland, releasing its unholy blessing.")
        }
    }
    private fun remove(player: Player, slot: Int) {
        ground[player]?.remove(slot)?.let { repo.del(it, Int.MAX_VALUE) }
        player.invMap[ITEMS]?.set(slot, null)
        player.invMap[META]?.set(slot, null)
    }
    override fun onPostTick(player: Player) {
        if ((player.pendingTeleport || player.pendingTelejump) && player.vars[GLAND] > 0) { VarPlayerIntMapSetter.set(player, GLAND, 0); syncBuffs(player) }
        val entries = ground[player]
        if (entries != null) for (slot in entries.keys.toList()) {
            val meta = player.invMap[META]?.get(slot) ?: continue
            val remaining = (meta.vars and 1023) - 1
            if (remaining <= 0) remove(player, slot)
            else player.invMap[META]!![slot] = meta.copy(vars = (meta.vars and 1023.inv()) or remaining)
        }
        val heart = player.vars[HEART]
        if (player.hitpoints == 0 && heart > 0) dead.add(player)
        else if (dead.remove(player) && heart > 0) boost(player)
        if (heart > 0) {
            maintainHeart(player)
            VarPlayerIntMapSetter.set(player, HEART, heart - 1)
            if (heart == 1) { StatBoostDecayPrevention.removeSource(player, HEART_SOURCE); hearts.remove(player); dead.remove(player) }
        }
        val gland = player.vars[GLAND]
        if (gland > 0) {
            if (gland % 4 == 0) player.statHeal("stat.prayer", random.of(10, 15), 0)
            VarPlayerIntMapSetter.set(player, GLAND, gland - 1)
        }
        if (heart > 0 || gland > 0) syncBuffs(player)
    }
    fun moved(player: Player, previous: CoordGrid) {
        if (previous.level != player.coords.level || kotlin.math.abs(previous.x - player.coords.x) > 8 || kotlin.math.abs(previous.z - player.coords.z) > 8) {
            VarPlayerIntMapSetter.set(player, GLAND, 0)
            syncBuffs(player)
        }
    }
    private fun boost(player: Player) {
        for (stat in listOf("stat.attack", "stat.strength", "stat.defence")) player.statBoost(stat, 6, 15)
        player.statBoost("stat.ranged", 5, 10)
        player.statBoost("stat.magic", 2, 10)
    }
    private fun maintainHeart(player: Player) {
        hearts.add(player)
        StatBoostDecayPrevention.addDecayOnly(player, HEART_STATS, HEART_SOURCE)
    }
    private fun syncBuffs(player: Player) {
        // Native buff structures use units of 25 and 4 game ticks, respectively.
        VarPlayerIntMapSetter.set(player, "varbit.wgs_smouldering_heart_timer", (player.vars[HEART] + 24) / 25)
        VarPlayerIntMapSetter.set(player, "varbit.wgs_smouldering_gland_timer", (player.vars[GLAND] + 3) / 4)
    }
    companion object {
        const val ITEMS = "inv.td_smouldering_items"
        const val META = "inv.td_smouldering_meta"
        const val HEART = "varp.td_heart_ticks"
        const val GLAND = "varp.td_gland_ticks"
        private const val HEART_SOURCE = "consumable.td_smouldering_heart"
        private val HEART_STATS = listOf("stat.attack", "stat.strength", "stat.defence", "stat.ranged", "stat.magic")
        val types: Set<Int> by lazy { setOf("obj.smouldering_heart".asRSCM(), "obj.smouldering_gland".asRSCM(), "obj.smouldering_pile_of_flesh".asRSCM()) }
    }
}

internal class TormentedSmoulderingScript @Inject constructor(private val effects: TormentedSmouldering) : PluginScript() {
    override fun ScriptContext.startup() {
        for (name in listOf("obj.smouldering_heart", "obj.smouldering_gland", "obj.smouldering_pile_of_flesh")) {
            onOpObj3(checkNotNull(dev.openrune.ServerCacheManager.getItem(name.asRSCM()))) { e -> effects.consume(player, e.obj) }
        }
        onPlayerLogin { effects.login(player) }
        onPlayerLogout { effects.logout(player) }
        onPlayerCoordsChanged { effects.moved(player, lastKnownCoords) }
    }
    override fun ScriptContext.shutdown() { effects.shutdown() }
}
internal class SmoulderingTakeHook : PlayerObjTakeValidateHook {
    override fun validateTake(player: Player, obj: Obj, objType: dev.openrune.types.ItemServerType): String? =
        if (obj.type in TormentedSmouldering.types) "Use the smouldering item on the ground; it cannot be picked up or telegrabbed." else null
}
