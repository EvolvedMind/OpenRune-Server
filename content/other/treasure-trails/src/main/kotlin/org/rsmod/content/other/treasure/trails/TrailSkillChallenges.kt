package org.rsmod.content.other.treasure.trails

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.player.events.interact.HeldEquipEvents
import org.rsmod.api.player.events.skilling.FarmingSeedPlantedEvent
import org.rsmod.api.player.events.skilling.LogBurnedEvent
import org.rsmod.api.player.events.skilling.PickpocketSuccessEvent
import org.rsmod.api.player.events.skilling.PrayerActivatedEvent
import org.rsmod.api.player.events.skilling.RunesCraftedEvent
import org.rsmod.api.player.events.skilling.SkillingActionCompleteEvent
import org.rsmod.api.player.events.skilling.SkillingActionContext
import org.rsmod.api.player.events.skilling.SkillingProductSource
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onEvent
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Award hooks run after successful production, never for bank withdrawals or spawned items. */
internal class TrailSkillChallenges @Inject constructor(
    private val progress: TrailProgress,
    private val targets: TrailTargets,
    private val requirements: TrailRequirements,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<FarmingSeedPlantedEvent> {
            if (seed != "obj.watermelon_seed".asRSCM()) return@onEvent
            for (active in targets.active(player)) {
                if (active.state.row != watermelonTask || active.state.phase != ASSIGNED) continue
                if (requirements.missing(player, active.clue) != null) continue
                if (progress.phase(player, active.slot, active.item, COMPLETED)) player.mes("You have completed Sherlock's challenge. Return to him with your clue.")
            }
        }
        onEvent<PickpocketSuccessEvent> {
            if (group != PickpocketSuccessEvent.Group.Elf) return@onEvent
            for (active in targets.active(player)) {
                if (active.state.row != elfTask || active.state.phase != ASSIGNED) continue
                if (requirements.missing(player, active.clue) != null) continue
                if (progress.phase(player, active.slot, active.item, COMPLETED)) player.mes("You have completed Sherlock's challenge. Return to him with your clue.")
            }
        }
        onEvent<HeldEquipEvents.WearposChange> {
            if (wearpos != Wearpos.RightHand || objType.id != "obj.dragon_scimitar".asRSCM()) return@onEvent
            // WearposChange also reports items being removed. Require the new worn state.
            if (player.worn[wearpos.slot]?.id != objType.id) return@onEvent
            for (active in targets.active(player)) {
                if (active.state.row != "dbrow.cluehelper_skillchallenge_elite_0".asRSCM() || active.state.phase != ASSIGNED) continue
                if (requirements.missing(player, active.clue) != null) continue
                if (progress.phase(player, active.slot, active.item, COMPLETED)) player.mes("You have completed Sherlock's challenge. Return to him with your clue.")
            }
        }

        onEvent<PrayerActivatedEvent> {
            if (prayer != "varbit.prayer_chivalry" || player.vars[prayer] == 0) return@onEvent
            for (active in targets.active(player)) {
                if (active.state.row != "dbrow.cluehelper_skillchallenge_elite_22".asRSCM() || active.state.phase != ASSIGNED) continue
                if (requirements.missing(player, active.clue) != null) continue
                if (progress.phase(player, active.slot, active.item, COMPLETED)) player.mes("You have completed Sherlock's challenge. Return to him with your clue.")
            }
        }

        onEvent<RunesCraftedEvent> {
            if (ourania || essenceConsumed <= 0 || baseMultiplier <= 0) return@onEvent
            val row = when {
                rune == "obj.bloodrune" && altar in setOf("loc.archeus_altar_blood", "loc.blood_altar") -> "dbrow.cluehelper_skillchallenge_master_14".asRSCM()
                rune == "obj.naturerune" -> "dbrow.cluehelper_skillchallenge_elite_2".asRSCM()
                rune == "obj.cosmicrune" && baseMultiplier >= 2 -> "dbrow.cluehelper_skillchallenge_elite_20".asRSCM()
                else -> return@onEvent
            }
            for (active in targets.active(player)) {
                if (active.state.row != row || active.state.phase != ASSIGNED) continue
                if (requirements.missing(player, active.clue) != null) continue
                if (progress.phase(player, active.slot, active.item, COMPLETED)) player.mes("You have completed Sherlock's challenge. Return to him with your clue.")
            }
        }

        onEvent<LogBurnedEvent> {
            for (active in targets.active(player)) {
                if (active.state.phase != ASSIGNED || burning[active.state.row] != log) continue
                if (requirements.missing(player, active.clue) != null) continue
                if (progress.phase(player, active.slot, active.item, COMPLETED)) player.mes("You have completed Sherlock's challenge. Return to him with your clue.")
            }
        }

        onEvent<SkillingActionCompleteEvent> {
            val product = context as? SkillingActionContext.Product ?: return@onEvent
            if (product.isBonus || product.count <= 0) return@onEvent
            for (active in targets.active(player)) {
                if (active.clue.kind != "skillchallenge" || active.state.phase != ASSIGNED) continue
                val expected = products[active.state.row] ?: TrailCharlie.products[active.state.row] ?: continue
                if (product.skill != expected.first || product.item.asRSCM() !in (alternatives[active.state.row] ?: setOf(expected.second)).map { it.asRSCM() }) continue
                if (requirements.missing(player, active.clue) != null) continue
                if (!TrailSkillOutfits.matches(player, active.state.row)) continue
                if (active.state.row == lightOrbTask && !inDorgeshBank(player.coords)) continue
                if (active.state.row == sacredEelTask && product.source != SkillingProductSource.SacredEel) continue
                if (active.state.row == gemStallTask) {
                    val source = product.source as? SkillingProductSource.ThievingStall ?: continue
                    if (source.loc != "loc.gemthiefstall" || source.coords != CoordGrid(2667, 3303, 0)) continue
                }
                if (progress.phase(player, active.slot, active.item, COMPLETED)) player.mes(if (active.state.row in TrailCharlie.products) "You have made Charlie's requested item. Take it and your clue back to him." else "You have completed Sherlock's challenge. Return to him with your clue.")
            }
        }
    }
    companion object {
        val watermelonTask get() = "dbrow.cluehelper_skillchallenge_elite_21".asRSCM()
        val elfTask get() = "dbrow.cluehelper_skillchallenge_master_13".asRSCM()
        val sacredEelTask get() = "dbrow.cluehelper_skillchallenge_master_18".asRSCM()
        val gemStallTask get() = "dbrow.cluehelper_skillchallenge_master_12".asRSCM()
        val lightOrbTask get() = "dbrow.cluehelper_skillchallenge_master_22".asRSCM()
        fun inDorgeshBank(coords: CoordGrid): Boolean = coords.level == 0 && coords.x in 2701..2707 && coords.z in 5345..5354
        const val ASSIGNED = 9
        const val COMPLETED = 10
        internal val burning by lazy {
            mapOf(
                "dbrow.cluehelper_skillchallenge_elite_18".asRSCM() to "obj.yew_logs",
                "dbrow.cluehelper_skillchallenge_master_7".asRSCM() to "obj.magic_logs",
                "dbrow.cluehelper_skillchallenge_master_8".asRSCM() to "obj.redwood_logs",
            )
        }
        internal val alternatives by lazy {
            mapOf(
                gemStallTask to setOf("obj.uncut_sapphire", "obj.uncut_emerald", "obj.uncut_ruby", "obj.uncut_diamond"),
                "dbrow.cluehelper_skillchallenge_elite_6".asRSCM() to setOf("obj.3dose2defense", "obj.4dose2defense"),
                "dbrow.cluehelper_skillchallenge_master_10".asRSCM() to (1..4).map { "obj.antivenom$it" }.toSet(),
            )
        }
        internal val products by lazy {
            mapOf(
                sacredEelTask to ("stat.cooking" to "obj.snakeboss_scale"),
                gemStallTask to ("stat.thieving" to "obj.uncut_sapphire"),
                lightOrbTask to ("stat.crafting" to "obj.dorgesh_light_bulb"),
                "dbrow.cluehelper_skillchallenge_elite_nickel".asRSCM() to ("stat.mining" to "obj.nickel_ore"),
                "dbrow.cluehelper_skillchallenge_elite_9".asRSCM() to ("stat.fletching" to "obj.yew_longbow"),
                "dbrow.cluehelper_skillchallenge_master_16".asRSCM() to ("stat.fletching" to "obj.rune_dart"),
                "dbrow.cluehelper_skillchallenge_elite_19".asRSCM() to ("stat.cooking" to "obj.swordfish"),
                "dbrow.cluehelper_skillchallenge_elite_14".asRSCM() to ("stat.smithing" to "obj.mithril_2h_sword"),
                "dbrow.cluehelper_skillchallenge_master_1".asRSCM() to ("stat.smithing" to "obj.rune_med_helm"),
                "dbrow.cluehelper_skillchallenge_elite_6".asRSCM() to ("stat.herblore" to "obj.3dose2defense"),
                "dbrow.cluehelper_skillchallenge_master_10".asRSCM() to ("stat.herblore" to "obj.antivenom4"),
                "dbrow.cluehelper_skillchallenge_master_15".asRSCM() to ("stat.herblore" to "obj.brutal_2doserangerspotion"),
                "dbrow.cluehelper_skillchallenge_elite_8".asRSCM() to ("stat.crafting" to "obj.dragonhide_body"),
                "dbrow.cluehelper_skillchallenge_elite_13".asRSCM() to ("stat.mining" to "obj.mithril_ore"),
                "dbrow.cluehelper_skillchallenge_elite_15".asRSCM() to ("stat.fishing" to "obj.raw_shark"),
                "dbrow.cluehelper_skillchallenge_elite_16".asRSCM() to ("stat.woodcutting" to "obj.yew_logs"),
                "dbrow.cluehelper_skillchallenge_master_6".asRSCM() to ("stat.crafting" to "obj.unstrung_dragonstone_amulet"),
                "dbrow.cluehelper_skillchallenge_master_11".asRSCM() to ("stat.mining" to "obj.runite_ore"),
                "dbrow.cluehelper_skillchallenge_master_20".asRSCM() to ("stat.fishing" to "obj.raw_anglerfish"),
                "dbrow.cluehelper_skillchallenge_master_21".asRSCM() to ("stat.woodcutting" to "obj.redwood_logs"),
            )
        }
    }
}
