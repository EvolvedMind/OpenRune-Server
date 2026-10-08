package org.rsmod.content.bosses.tormenteddemon

import dev.openrune.rscm.RSCM.asRSCM
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.invtx.*
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.weapons.*
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.isType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class ArclightWeapons : WeaponMap {
    override fun WeaponRepository.register(manager: WeaponAttackManager) {
        register("obj.arclight", ChargedArclight(manager))
    }
    private class ChargedArclight(private val manager: WeaponAttackManager) : MeleeWeapon {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Melee): Boolean = strike(target, attack)
        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Melee): Boolean = strike(target, attack)
        private fun ProtectedAccess.strike(target: PathingEntity, attack: CombatAttack.Melee): Boolean {
            val weapon = attack.weapon ?: return false
            if (ArclightState.charges(weapon) <= 0) {
                for (i in player.worn.objs.indices) if (player.worn[i] == weapon) player.worn[i] = InvObj("obj.arclight_inactive", vars = weapon.vars)
                manager.stopCombat(this)
                mes("Arclight is inactive. Recharge it with ancient shards.")
                return true
            }
            manager.playWeaponFx(this, attack)
            val damage = manager.rollMeleeDamage(this, target, attack, 1.0, 1.0)
            val hit = manager.queueMeleeHit(this, target, damage)
            manager.giveCombatXp(this, target, attack, damage)
            if (hit.damage > 0) {
                val remaining = ArclightState.charges(weapon) - 1
                val vars = ArclightState.pack(remaining, ArclightState.infusion(weapon) + 1)
                for (slot in player.worn.objs.indices) if (player.worn[slot] == weapon) {
                    player.worn[slot] = InvObj(if (remaining == 0) "obj.arclight_inactive" else "obj.arclight", vars = vars)
                    break
                }
                if (remaining == 100) mes("Arclight only has 100 charges remaining.")
            }
            manager.continueCombat(this, target)
            return true
        }
    }
}

internal class ArclightChargingScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeldU("obj.cata_shard", "obj.darklight") { mes("You require additional power from the catacombs altar to create Arclight.") }
        onOpLocU("loc.cata_altar", "obj.darklight") { event ->
            val item = inv[event.invSlot] ?: return@onOpLocU
            if (inv.count("obj.cata_shard") < 3) { mes("You need three ancient shards to create Arclight."); return@onOpLocU }
            if (player.invTransaction(inv) {
                val target = select(inv)
                delete(target, item.id, 1, slot = event.invSlot)
                delete(target, "obj.cata_shard".asRSCM(), 3)
                add(target, "obj.arclight".asRSCM(), 1, vars = ArclightState.pack(1000, 0), slot = event.invSlot)
            }.success) objbox("obj.arclight", "The altar imbues Darklight with the power of the ancient shards.")
        }
        for (weapon in listOf("obj.arclight", "obj.arclight_inactive")) {
            onOpHeldU("obj.cata_shard", weapon) { recharge(weapon) }
            onOpHeld3(weapon) { e ->
                mes("Arclight: ${ArclightState.charges(e.obj)} charges; ${ArclightState.infusion(e.obj) / 100}% infusion.")
            }
        }
    }
    private suspend fun ProtectedAccess.recharge(weapon: String) {
        val slot = inv.objs.indexOfFirst { it.isType(weapon) }
        if (slot < 0) return
        val item = checkNotNull(inv[slot])
        if (ArclightState.charges(item) >= ArclightState.CAPACITY) { mes("Arclight is already fully charged."); return }
        val available = inv.count("obj.cata_shard")
        val space = ArclightState.CAPACITY - ArclightState.charges(item)
        val limit = space / 1000 * 3 + (space % 1000 + 332) / 333
        val count = minOf(available, limit, countDialog("How many ancient shards would you like to use?"))
        if (count <= 0 || inv[slot] !== item) return
        val added = (count / 3 * 1000 + count % 3 * 333).coerceAtMost(ArclightState.CAPACITY - ArclightState.charges(item))
        val needed = count
        val vars = ArclightState.pack(ArclightState.charges(item) + added, ArclightState.infusion(item))
        val done = player.invTransaction(inv) {
            val target = select(inv)
            delete(target, item.id, 1, slot = slot)
            delete(target, "obj.cata_shard".asRSCM(), needed)
            add(target, "obj.arclight".asRSCM(), 1, vars = vars, slot = slot)
        }.success
        if (done) mes("You add $added charges to Arclight.")
    }
}
