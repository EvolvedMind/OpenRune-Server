package org.rsmod.content.skills.fishing.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.invtx.*
import org.rsmod.api.player.events.skilling.SkillingActionCompleteEvent
import org.rsmod.api.player.events.skilling.SkillingActionContext
import org.rsmod.api.player.events.skilling.SkillingProductSource
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.fishingLvl
import org.rsmod.api.player.stat.hunterLvl
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onApNpc1
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.game.entity.Npc
import org.rsmod.game.inv.isType
import org.rsmod.game.proj.ProjAnim
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class AerialFishingScript @Inject constructor(private val xpMods: XpModifiers, private val world: WorldRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(ANGLER) { giveBird() }
        onOpNpc4(ANGLER) { giveBird() }
        onApNpc1(SPOT) { if (isWithinApRange(it.npc, 10)) catch(it.npc) }
        onOpNpc1(SPOT) { catch(it.npc) }
        for (fish in Fish.entries) onOpHeldU("obj.knife", fish.item) {
            if (inv[it.firstSlot]?.id != it.first.id || inv[it.secondSlot]?.id != it.second.id) return@onOpHeldU
            val result = player.invTransaction(inv) {
                val target = select(inv); delete(target, fish.item.asRSCM(), 1, it.secondSlot); add(target, CHUNKS.asRSCM(), 1)
            }
            if (result.success) { statAdvance("stat.cooking", fish.cookingXp * xpMods.get(player, "stat.cooking")) }
        }
    }

    private fun ProtectedAccess.giveBird() {
        if (!atMolch(coords.x, coords.z)) return
        if (inv.count(GLOVE) > 0 || player.worn.objs.filterNotNull().any { it.isType(GLOVE) }) {
            mes("You already have a cormorant. Wear the glove and feed it king worms or fish chunks."); return
        }
        if (player.fishingLvl < 43 || player.hunterLvl < 35) { mes("You need Fishing 43 and Hunter 35 to handle a cormorant."); return }
        if (!player.invTransaction(inv) { add(select(inv), GLOVE.asRSCM(), 1) }.success) { mes("Make room for a cormorant's glove."); return }
        mes("Wear the glove, then send the cormorant to a fishing spot. It eats king worms or fish chunks.")
    }

    private suspend fun ProtectedAccess.catch(npc: Npc) {
        if (!atMolch(coords.x, coords.z) || !atMolch(npc.coords.x, npc.coords.z) || !isWithinDistance(npc, 10)) return
        if (player.worn.objs.filterNotNull().none { it.isType(GLOVE) }) { mes("Wear a cormorant's glove with a bird first."); return }
        val available = Fish.entries.filter { player.fishingLvl >= it.fishing && player.hunterLvl >= it.hunter }
        if (available.isEmpty()) { mes("You need Fishing 43 and Hunter 35."); return }
        val bait = if (inv.count(CHUNKS) > 0) CHUNKS else if (inv.count("obj.king_worm") > 0) "obj.king_worm" else { mes("Your cormorant needs king worms or fish chunks."); return }
        val uid = npc.uid
        val start = coords
        val spot = npc.coords
        // These sequences animate the bird model. Applying them to the player corrupts the pose.
        spotanim("spotanim.aerial_fishing_launch", height = 20)
        world.projAnim(ProjAnim.fromPlayerToNpc(player, npc, "spotanim.aerial_fishing_travel".asRSCM(), "projanim.magic_spell")
            .copy(startHeight = 20, endHeight = 0, startTime = 0, endTime = 90, angle = 0, progress = 0))
        delay(3)
        spotanimMap(world, "spotanim.aerial_fishing_splash", spot)
        world.projAnim(ProjAnim.fromNpcToPlayer(npc, player, "spotanim.aerial_fishing_travel".asRSCM(), checkNotNull(ServerCacheManager.getProjectile("projanim.magic_spell".asRSCM())))
            .copy(startHeight = 0, endHeight = 20, startTime = 0, endTime = 90, angle = 0, progress = 0))
        delay(3)
        if (npc.uid != uid || !npc.isVisible || npc.coords != spot || coords != start || inv.count(bait) == 0 || player.worn.objs.filterNotNull().none { it.isType(GLOVE) }) return
        val fish = available[random.of(0, available.lastIndex)]
        val consumeBait = random.of(1, 5) == 1
        val result = player.invTransaction(inv) {
            val target = select(inv)
            if (consumeBait) delete(target, bait.asRSCM(), 1)
            add(target, fish.item.asRSCM(), 1)
        }
        if (!result.success) { mes("You need room for the catch and bait for your cormorant."); return }
        val fishingXp = fish.fishingXp * xpMods.get(player, "stat.fishing")
        statAdvance("stat.fishing", fishingXp)
        statAdvance("stat.hunter", fish.hunterXp * xpMods.get(player, "stat.hunter"))
        publish(SkillingActionCompleteEvent(player, SkillingActionContext.Product("stat.fishing", fish.item, 1, fishingXp, SkillingProductSource.AerialFishing)))
        spam("Your cormorant catches a ${fish.displayName}.")
    }

    enum class Fish(val item: String, val displayName: String, val fishing: Int, val hunter: Int, val fishingXp: Double, val hunterXp: Double, val cookingXp: Double) {
        Bluegill("obj.aerial_fishing_bluegill", "bluegill", 43, 35, 11.5, 16.5, 3.0),
        Tench("obj.aerial_fishing_common_tench", "common tench", 56, 51, 40.0, 45.0, 10.0),
        Eel("obj.aerial_fishing_mottled_eel", "mottled eel", 73, 68, 65.0, 90.0, 20.0),
        Siren("obj.aerial_fishing_greater_siren", "greater siren", 91, 87, 100.0, 130.0, 25.0),
    }
    companion object {
        const val ANGLER = "npc.fishing_npc_angler"
        const val SPOT = "npc.fishing_spot_aerial"
        const val GLOVE = "obj.aerial_fishing_gloves_bird"
        const val CHUNKS = "obj.fish_chunks"
        fun atMolch(x: Int, z: Int) = x in 1320..1415 && z in 3590..3710
    }
}
