package org.rsmod.content.skills.hunter

import computeSkillingSuccess
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.invtx.*
import org.rsmod.api.player.events.skilling.SkillingActionCompleteEvent
import org.rsmod.api.player.events.skilling.SkillingActionContext
import org.rsmod.api.player.events.skilling.SkillingProductSource
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hunterLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.game.entity.Npc
import org.rsmod.game.inv.isType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class BlackWarlockScript @Inject constructor(private val npcs: NpcRepository, private val xpMods: XpModifiers, private val random: GameRandom) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.butterfly_warlock") { catch(it.npc) }
        onOpHeld4("obj.butterfly_jar_warlock") {
            val original = inv[it.slot] ?: return@onOpHeld4
            if (original !== it.obj || original.vars != 0) return@onOpHeld4
            if (player.invTransaction(inv) {
                val target = select(inv); delete(target, original.id, 1, it.slot); add(target, "obj.butterfly_jar".asRSCM(), 1, slot = it.slot)
            }.success) { anim("seq.hunting_opening_butterfly_jar"); mes("You release the black warlock.") }
        }
    }
    private suspend fun ProtectedAccess.catch(npc: Npc) {
        if (player.hunterLvl < 45) { mes("You need Hunter 45 to catch a black warlock."); return }
        val net = { inv.count("obj.hunting_butterfly_net") > 0 || inv.count("obj.ii_magic_butterfly_net") > 0 || player.worn[3]?.isType("obj.hunting_butterfly_net") == true || player.worn[3]?.isType("obj.ii_magic_butterfly_net") == true }
        if (!net() || inv.count("obj.butterfly_jar") == 0) { mes("You need a butterfly net and an empty butterfly jar."); return }
        val uid = npc.uid
        val start = coords
        anim("seq.hunting_impling_catch")
        delay(2)
        resetAnim()
        if (npc.uid != uid || !npc.isVisible || coords != start || !isWithinDistance(npc, 1) || !net()) return
        if (random.randomDouble() >= computeSkillingSuccess(80, 220, player.hunterLvl)) { spam("The butterfly slips away."); return }
        if (!player.invTransaction(inv) {
            val target = select(inv); delete(target, "obj.butterfly_jar".asRSCM(), 1); add(target, "obj.butterfly_jar_warlock".asRSCM(), 1)
        }.success) return
        npcs.hide(npc, 20)
        val xp = 125.0 * xpMods.get(player, "stat.hunter")
        statAdvance("stat.hunter", xp)
        publish(SkillingActionCompleteEvent(player, SkillingActionContext.Product("stat.hunter", "obj.butterfly_jar_warlock", 1, xp, SkillingProductSource.HunterCatch(npc.type.id))))
        spam("You catch a black warlock.")
    }
}
