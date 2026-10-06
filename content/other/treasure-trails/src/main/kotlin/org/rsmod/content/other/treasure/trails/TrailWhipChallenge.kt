package org.rsmod.content.other.treasure.trails

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.player.events.interact.HeldEquipEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class TrailWhipChallenge @Inject constructor(
    private val progress: TrailProgress,
    private val targets: TrailTargets,
    private val requirements: TrailRequirements,
    private val areas: AreaChecker,
    private val npcs: NpcRepository,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<HeldEquipEvents.WearposChange> {
            if (wearpos != Wearpos.RightHand || objType.id !in weapons) return@onEvent
            if (player.worn[wearpos.slot]?.id != objType.id) return@onEvent
            if (!areas.inArea("area.slayer_tower", player.coords)) return@onEvent
            if (npcs.findAll(ZoneKey.from(player.coords), 2).none { nearbyDemon(player, it) }) return@onEvent
            for (active in targets.active(player)) {
                if (active.state.row != row || active.state.phase != TrailSkillChallenges.ASSIGNED) continue
                if (requirements.missing(player, active.clue) != null) continue
                if (progress.phase(player, active.slot, active.item, TrailSkillChallenges.COMPLETED))
                    player.mes("You have completed Sherlock's challenge. Return to him with your clue.")
            }
        }
    }
    companion object {
        val row get() = "dbrow.cluehelper_skillchallenge_master_0".asRSCM()
        val weapons by lazy { listOf("abyssal_whip", "abyssal_whip_ice", "abyssal_whip_lava", "league_3_whip", "abyssal_tentacle", "league_3_whip_tentacle").map { "obj.$it".asRSCM() }.toSet() }
        fun nearbyDemon(player: Player, npc: Npc): Boolean =
            npc.type.id == "npc.slayer_abyssal".asRSCM() && npc.coords.level == player.coords.level &&
                npc.coords.chebyshevDistance(player.coords) <= 10
    }
}
