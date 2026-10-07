package org.rsmod.content.skills.agility

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.atan2
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.player.events.PlayerMovementEvent
import org.rsmod.api.player.events.interact.HeldEquipEvents
import org.rsmod.api.player.events.skilling.AgilityLapCompletedEvent
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

@Singleton
class ClueAgilityScript @Inject constructor(private val xpMods: XpModifiers) : PluginScript(), PlayerDeathCleanupHook {
    override fun ScriptContext.startup() {
        for (course in ClueAgilityCourse.entries) {
            for ((index, obstacle) in course.obstacles.withIndex()) {
                onOpLoc1(obstacle.loc) { traverse(course, index, obstacle, it.loc) }
            }
        }
        onOpLoc1("loc.100_ilm_monkeybars_end") { mes("Start at the eastern end of the monkey bars.") }
        onEvent<HeldEquipEvents.WearposChange> {
            if (player.vars[COURSE] == ClueAgilityCourse.Rellekka.ordinal + 1 && !fullGraceful(player)) set(player, GRACE, 0)
        }
        onEvent<PlayerMovementEvent.CoordsMovedEvent> {
            val course = ClueAgilityCourse.entries.getOrNull(player.vars[COURSE] - 1) ?: return@onEvent
            if (!course.contains(player.coords)) cleanup(player)
        }
        // Completed obstacle state persists. A temporary monkey model does not survive a session.
        onPlayerLogout { player.transmog = null }
    }

    private suspend fun ProtectedAccess.traverse(course: ClueAgilityCourse, index: Int, obstacle: CourseObstacle, loc: BoundLocInfo) {
        if (!course.contains(coords) || !course.contains(loc.coords) || coords.level != loc.coords.level) return
        if (player.agilityLvl < course.level) { mes("You need Agility ${course.level} to use this obstacle."); return }
        if (course == ClueAgilityCourse.ApeAtoll && player.worn[3]?.id !in ninjaGreegrees) {
            mes("Wield a ninja or Kruk monkey greegree to use this course."); return
        }
        val start = coords
        val end = obstacle.landing(start)
        if (course == ClueAgilityCourse.ApeAtoll) transmog("npc.mm_transmogrification_small_ninja_monkey")
        faceSquare(end)
        var finished = false
        try {
            if (course == ClueAgilityCourse.ApeAtoll && index == 2) { anim("seq.100_ilm_monkeybar_jump_up"); delay(1) }
            anim(obstacle.animation)
            if (obstacle.slide) {
                val direction = ((atan2((end.x - start.x).toDouble(), (end.z - start.z).toDouble()) * 325.949323) + 2048).toInt() and 2047
                exactMove(start, end, 0, obstacle.ticks * 30, direction, TeleportType.Exempt)
            }
            delay(obstacle.ticks)
            if (!obstacle.slide) teleport(end, TeleportType.Exempt)
            if (coords != end) return
            if (course == ClueAgilityCourse.ApeAtoll && index == 2) { anim("seq.100_ilm_monkeybar_jump_down"); delay(1) }
            statAdvance("stat.agility", obstacle.xp * xpMods.get(player, "stat.agility"))
            advance(course, index)
            finished = true
        } finally {
            resetAnim()
            resetTransmog()
            if (!finished) {
                // A cancelled crossing must land safely, without granting obstacle or lap credit.
                teleport(end, TeleportType.Exempt)
                cleanup(player)
            }
        }
    }

    private fun ProtectedAccess.advance(course: ClueAgilityCourse, index: Int) {
        val same = player.vars[COURSE] == course.ordinal + 1
        val expected = if (same) player.vars[STEP] else 0
        if (index == 0) {
            set(player, COURSE, course.ordinal + 1); set(player, STEP, 1)
            set(player, GRACE, if (fullGraceful(player)) 1 else 0)
            return
        }
        if (!same || expected != index) { cleanup(player); return }
        if (!fullGraceful(player)) set(player, GRACE, 0)
        if (index + 1 < course.obstacles.size) { set(player, STEP, index + 1); return }
        val graceful = player.vars[GRACE] == 1
        cleanup(player)
        statAdvance("stat.agility", course.lapXp * xpMods.get(player, "stat.agility"))
        publish(AgilityLapCompletedEvent(player, course.name, graceful))
        mes("You have completed a lap of ${if (course == ClueAgilityCourse.ApeAtoll) "Ape Atoll" else "Rellekka"}.")
    }

    override fun cleanup(player: Player) { set(player, COURSE, 0); set(player, STEP, 0); set(player, GRACE, 0); player.transmog = null }

    companion object {
        const val COURSE = "varp.agility_course"
        const val STEP = "varp.agility_step"
        const val GRACE = "varp.agility_graceful_lap"
        val ninjaGreegrees by lazy { listOf("mm_monkey_greegree_for_small_ninja_monkey", "mm_monkey_greegree_for_medium_ninja_monkey", "mm2_kruk_greegree").map { "obj.$it".asRSCM() } }
        fun fullGraceful(player: Player): Boolean = listOf(0, 1, 4, 7, 9, 10).all { slot ->
            val item = player.worn[slot] ?: return@all false
            ServerCacheManager.getItem(item.id)?.name?.startsWith("Graceful ", ignoreCase = true) == true
        }
        private fun set(player: Player, variable: String, value: Int) = VarPlayerIntMapSetter.set(player, variable, value)
    }
}
