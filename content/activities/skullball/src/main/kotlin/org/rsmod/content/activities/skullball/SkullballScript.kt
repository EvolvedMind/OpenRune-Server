package org.rsmod.content.activities.skullball

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.player.events.skilling.SkullballGoalScoredEvent
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.*
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.module.PluginModule
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.LineValidator
import org.rsmod.routefinder.collision.CollisionFlagMap

class SkullballModule : PluginModule() {
    override fun bind() {
        addSetBinding<PlayerPostTickHook>(SkullballScript::class.java)
        addSetBinding<PlayerDeathCleanupHook>(SkullballScript::class.java)
    }
}

/** Native Tap/Kick/Shoot move an owned ball through the ten mapped goals and final hole. */
@Singleton
class SkullballScript @Inject constructor(
    private val npcs: NpcRepository,
    private val bus: EventBus,
    collision: CollisionFlagMap,
    private val xpMods: XpModifiers,
) : PluginScript(), PlayerPostTickHook, PlayerDeathCleanupHook {
    private class Game(val ball: Npc, val start: Int, var timedFrom: Int = -1, var goal: Int = 0, var last: CoordGrid = ball.coords)
    private val games = HashMap<Player, Game>()
    private val lines = LineValidator(collision)

    override fun ScriptContext.startup() {
        onOpNpc1("npc.werewolf_skullballboss") {
            if (!eligible(player)) { mes("You need Agility 25 and a worn Ring of Charos to play."); return@onOpNpc1 }
            cleanup(player)
            val ball = Npc("npc.waa_skullball", START)
            if (runCatching { npcs.add(ball, 2000) }.isFailure) return@onOpNpc1
            ball.assignSpawnOwner(player, mapClock)
            ball.noneMode()
            games[player] = Game(ball, mapClock)
            mes("Tap moves the ball 1 tile, Kick 5 and Shoot 10. Stand behind the ball to aim.")
            showGoal(player)
        }
        onOpNpc1("npc.waa_skullball") { kick(it.npc, 1) }
        onOpNpc3("npc.waa_skullball") { kick(it.npc, 5) }
        onOpNpc4("npc.waa_skullball") { kick(it.npc, 10) }
        onOpNpc5("npc.waa_skullball") { if (games[player]?.ball === it.npc) showGoal(player) }
        onOpLoc1("loc.waa_trapdoor") {
            if (eligible(player)) { teleport(CoordGrid(3549, 9864, 0)); mes("Welcome to the werewolf training grounds.") } else mes("Wear a Ring of Charos and reach Agility 25 to enter.")
        }
        onOpLoc1("loc.waa_trapdoor_open") { if (eligible(player)) teleport(CoordGrid(3549, 9864, 0)) }
        onOpLoc1("loc.waa_ladder") { cleanup(player); teleport(CoordGrid(3544, 3462, 0)) }
        onPlayerLogout { cleanup(player) }
    }
    override fun ScriptContext.shutdown() { for (player in games.keys.toList()) cleanup(player) }

    private fun ProtectedAccess.kick(ball: Npc, power: Int) {
        val game = games[player]
        if (game?.ball !== ball) { mes("That skullball belongs to another game."); return }
        if (!eligible(player) || !isWithinDistance(ball, 1) || ball.routeDestination.isNotEmpty()) return
        val dx = (ball.coords.x - coords.x).coerceIn(-1, 1)
        val dz = (ball.coords.z - coords.z).coerceIn(-1, 1)
        if (dx == 0 && dz == 0) { mes("Stand beside the skullball to aim."); return }
        val path = ArrayList<CoordGrid>()
        var tile = ball.coords
        repeat(power) {
            val next = tile.translate(dx, dz)
            if (inCourse(next) && lines.hasLineOfWalk(tile.level, tile.x, tile.z, next.x, next.z)) { path += next; tile = next }
        }
        if (path.isEmpty()) return
        anim("seq.kick_skull_ball")
        ball.walk(path)
    }

    override fun onPostTick(player: Player) {
        val game = games[player] ?: return
        if (!inCourse(player.coords) || !eligible(player) || player.currentMapClock - game.start >= 2000) { cleanup(player); return }
        val now = game.ball.coords
        val goal = goals.getOrNull(game.goal) ?: return
        if (now != game.last && (now == goal || crossed(game.last, now, goal))) {
            if (game.goal == 0) game.timedFrom = player.currentMapClock
            game.goal++
            bus.publish(SkullballGoalScoredEvent(player, game.goal))
            player.mes("Goal ${game.goal}/11 scored!")
            if (game.goal == goals.size) {
                val ticks = player.currentMapClock - game.timedFrom
                val xp = (750.0 - (ticks - 400).coerceAtLeast(0) * 0.9).coerceAtLeast(0.0)
                player.statAdvance("stat.agility", xp * xpMods.get(player, "stat.agility"))
                player.mes("You finish Skullball and earn ${xp.toInt()} Agility experience.")
                cleanup(player); return
            }
            showGoal(player)
        }
        game.last = now
    }

    private fun showGoal(player: Player) {
        val game = games[player] ?: return
        val goal = goals.getOrNull(game.goal) ?: return
        player.mes("Next goal: ${game.goal + 1}/11 at ${goal.x}, ${goal.z}.")
    }
    override fun cleanup(player: Player) {
        val game = games.remove(player) ?: return
        game.ball.abortRoute()
        if (game.ball.slotId > 0) runCatching { npcs.del(game.ball, Int.MAX_VALUE) }
    }
    private fun crossed(from: CoordGrid, to: CoordGrid, goal: CoordGrid): Boolean =
        from.level == goal.level && to.level == goal.level &&
            goal.x in minOf(from.x, to.x)..maxOf(from.x, to.x) && goal.z in minOf(from.z, to.z)..maxOf(from.z, to.z) &&
            (goal.x - from.x) * (to.z - from.z) == (goal.z - from.z) * (to.x - from.x)

    companion object {
        val START = CoordGrid(3555, 9865, 0)
        val goals = listOf(3555 to 9870, 3556 to 9883, 3558 to 9891, 3557 to 9900, 3558 to 9906, 3563 to 9911,
            3575 to 9905, 3574 to 9888, 3575 to 9878, 3568 to 9864, 3557 to 9860).map { (x, z) -> CoordGrid(x, z, 0) }
        fun inCourse(coords: CoordGrid) = coords.level == 0 && coords.x in 3540..3580 && coords.z in 9858..9917
        fun eligible(player: Player) = player.agilityLvl >= 25 && player.worn[12]?.id in setOf("obj.ring_of_charos".asRSCM(), "obj.ring_of_charos_unlocked".asRSCM())
    }
}
