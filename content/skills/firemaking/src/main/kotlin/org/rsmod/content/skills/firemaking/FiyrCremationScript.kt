package org.rsmod.content.skills.firemaking

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.events.skilling.ShadeCrematedEvent
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Native automatic pyre building: incomplete inputs are never left in transient world state. */
class FiyrCremationScript @Inject constructor(private val locs: LocRepository, private val world: WorldRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.temple_pyre") { cremate(it.loc) }
        for (item in listOf("obj.shade_bones5", "obj.magic_logs_pyre", "obj.redwood_logs_pyre", "obj.tinderbox")) {
            onOpLocU("loc.temple_pyre", item) { cremate(it.loc) }
        }
    }
    private suspend fun ProtectedAccess.cremate(loc: BoundLocInfo) {
        arriveDelay(); faceLoc(loc)
        if (loc.coords.level != 0 || loc.coords.x !in 3450..3520 || loc.coords.z !in 3255..3310) return
        val logs = if (stat("stat.firemaking") >= 90 && inv.contains("obj.redwood_logs_pyre")) "obj.redwood_logs_pyre" else "obj.magic_logs_pyre"
        if (stat("stat.firemaking") < 80 || !inv.contains(logs) || !inv.contains("obj.shade_bones5") || !inv.contains("obj.tinderbox")) {
            mes("You need level 80 Firemaking, magic (or redwood) pyre logs, Fiyr remains and a tinderbox."); return
        }
        val start = coords
        anim("seq.human_createfire"); delay(3)
        if (coords != start || locs.findExact(loc.coords, ServerCacheManager.getObject(loc.id)!!) == null || !inv.contains("obj.tinderbox") || stat("stat.firemaking") < if (logs == "obj.redwood_logs_pyre") 90 else 80) return
        val roll = random.of(1000)
        val reward = when { roll < 210 -> "obj.coins"; roll < 844 -> silver[random.of(silver.size)]; else -> gold[random.of(gold.size)] }
        val amount = if (reward == "obj.coins") random.of(2000, 4000) else 1
        if (!player.invTransaction(inv) {
            val target = select(inv)
            delete(target, logs.asRSCM(), 1); delete(target, "obj.shade_bones5".asRSCM(), 1); add(target, reward.asRSCM(), amount)
        }.success) { mes("You don't have enough inventory space for the cremation reward."); return }
        val redwood = logs == "obj.redwood_logs_pyre"
        locs.change(loc, if (redwood) "loc.temple_pyre_bones_redwood" else "loc.temple_pyre_bones_magic", 8)
        locAnim(world, loc, "seq.temple_pyre_fire")
        statAdvance("stat.firemaking", if (redwood) 499.5 else 404.5)
        val xp = if (redwood) 100.5 else 100.0
        statAdvance("stat.prayer", xp)
        publish(ShadeCrematedEvent(player, "obj.shade_bones5".asRSCM(), logs.asRSCM(), loc.coords))
        mes("You cremate the Fiyr remains and receive your reward.")
    }
    companion object {
        val silver = listOf("bloodred", "brown", "crimson", "black", "purple").map { "obj.shadekey_silver_$it" }
        val gold = listOf("bloodred", "brown", "crimson", "black", "purple").map { "obj.shadekey_gold_$it" }
    }
}
