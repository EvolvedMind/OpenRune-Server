package org.rsmod.content.bosses.tormenteddemon

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.invtx.*
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onCommand
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class TormentedDemonTestCommands @Inject constructor(private val access: ProtectedAccessLauncher) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("testtd") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "TD test: entrance, 1/2/3 demons, kit or crafting items"
            cheat {
                if (args.size > 1) { player.mes("::testtd [entrance|1|2|3|kit|items]"); return@cheat }
                when (args.firstOrNull()?.lowercase()) {
                    "kit", "items" -> {
                        val items = if (args[0].lowercase() == "items") crafting else combat
                        val added = player.invTransaction(player.inv) {
                            val inv = select(player.inv)
                            items.forEach { add(inv, it.id, it.count, it.vars, strict = true) }
                        }.success
                        player.mes(if (added) "TD test items added. Read Duradel's notes before crafting synapse weapons." else "Make room in your inventory. Nothing was added or removed.")
                    }
                    null, "2" -> access.launch(player) { telejump(CoordGrid(4072, 4422), TeleportType.Exempt) }
                    "entrance" -> access.launch(player) { telejump(TormentedTempleScript.ENTRANCE, TeleportType.Exempt) }
                    "1" -> access.launch(player) { telejump(CoordGrid(4136, 4376), TeleportType.Exempt) }
                    "3" -> access.launch(player) { telejump(CoordGrid(4045, 4390), TeleportType.Exempt) }
                    else -> player.mes("::testtd [entrance|1|2|3|kit|items] | ::testloot td [1-1000]")
                }
            }
        }
    }
    private val combat get() = listOf(
        InvObj("obj.emberlight"), InvObj("obj.scorching_bow"), InvObj("obj.purging_staff"),
        InvObj("obj.barrows_dharok_weapon"), InvObj("obj.heavy_ballista"), InvObj("obj.dragon_arrow", 1000),
        InvObj("obj.dragon_javelin", 1000), InvObj("obj.airrune", 5000), InvObj("obj.waterrune", 5000),
        InvObj("obj.firerune", 5000), InvObj("obj.earthrune", 5000), InvObj("obj.soulrune", 1000),
        InvObj("obj.wrathrune", 1000), InvObj("obj.mantaray", 8), InvObj("obj.4doseprayerrestore", 4),
    )
    private val crafting get() = listOf(
        InvObj("obj.duradels_notes_on_demon_slaying"), InvObj("obj.hammer"), InvObj("obj.tormented_synapse", 3),
        InvObj("obj.arclight", vars = ArclightState.pack(7000, 3000)), InvObj("obj.iron_bar"), InvObj("obj.battlestaff"),
        InvObj("obj.unstrung_magic_longbow"), InvObj("obj.bone_claw", 2), InvObj("obj.cata_shard", 3),
        InvObj("obj.teleportscroll_guthixian_temple", 3), InvObj("obj.tog_sapphire_lantern_lit"),
    )
}
