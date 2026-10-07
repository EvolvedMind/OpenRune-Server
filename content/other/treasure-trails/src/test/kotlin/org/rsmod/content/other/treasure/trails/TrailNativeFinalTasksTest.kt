package org.rsmod.content.other.treasure.trails

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import kotlin.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.death.*
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.music.MusicRepository
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.processor.NpcHitProcessor
import org.rsmod.api.npc.respawn.BossRespawnTimers
import org.rsmod.api.player.events.interact.*
import org.rsmod.api.player.events.skilling.*
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.*
import org.rsmod.api.player.protect.*
import org.rsmod.api.player.ui.IfOverlayButtonT
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.script.onEvent
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.spells.runes.combo.ComboRuneRepository
import org.rsmod.api.spells.runes.compact.CompactRuneRepository
import org.rsmod.api.spells.runes.fake.FakeRuneRepository
import org.rsmod.api.spells.runes.staves.StaffSubstituteRepository
import org.rsmod.api.spells.runes.subs.RuneSubstituteRepository
import org.rsmod.api.spells.runes.unlimited.UnlimitedRuneRepository
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.activities.skullball.SkullballScript
import org.rsmod.content.skills.agility.ClueAgilityCourse
import org.rsmod.content.skills.agility.ClueAgilityScript
import org.rsmod.content.skills.farming.FarmingClock
import org.rsmod.content.skills.farming.SpiritTreeScript
import org.rsmod.content.skills.fishing.scripts.AerialFishingScript
import org.rsmod.content.skills.hunter.BlackWarlockScript
import org.rsmod.content.skills.hunter.ClueTrapHunting
import org.rsmod.content.skills.magic.utility.AbyssalReanimationScript
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.*
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.*
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.ui.Component
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

private fun Inventory.count(id: Int): Int = objs.filterNotNull().filter { it.id == id }.sumOf { it.count }

@ResourceLock("ServerCacheManager")
@OptIn(InternalApi::class)
class TrailNativeFinalTasksTest {
    @Test fun `native Ape Atoll obstacles earn one complete lap before Sherlock can reward the clue`() {
        val f = Fixture(elite(5)); f.player.worn[3] = InvObj("obj.mm_monkey_greegree_for_small_ninja_monkey")
        f.register(f.agility)
        f.lap(ClueAgilityCourse.ApeAtoll)
        assertEquals(580, f.player.statMap.getXP("stat.agility")); f.completed(); f.sherlock()
        assertNull(f.player.transmog)
    }

    @Test fun `all seven native Rellekka obstacles require graceful throughout the lap`() {
        val f = Fixture(master(9)); f.graceful(); f.register(f.agility)
        f.lap(ClueAgilityCourse.Rellekka)
        assertEquals(1000, f.player.statMap.getXP("stat.agility")); f.completed(); f.sherlock()
    }

    @Test fun `starting at the final obstacle cannot grant a lap or clue credit`() {
        for (course in ClueAgilityCourse.entries) {
            val f = Fixture(if (course == ClueAgilityCourse.ApeAtoll) elite(5) else master(9)); f.graceful()
            if (course == ClueAgilityCourse.ApeAtoll) f.player.worn[3] = InvObj("obj.mm2_kruk_greegree")
            f.register(f.agility); f.obstacle(course, course.obstacles.lastIndex)
            assertEquals(9, f.state().phase); assertTrue(f.events.isEmpty())
            assertEquals(0, f.player.statMap.getXP("stat.agility"))
        }
    }

    @Test fun `removing and later restoring graceful invalidates the current lap`() {
        val f = Fixture(master(9)); f.graceful(); f.register(f.agility)
        for (i in ClueAgilityCourse.Rellekka.obstacles.indices) {
            if (i == 1) f.player.worn[9] = null
            if (i == 2) f.player.worn[9] = InvObj("obj.graceful_gloves")
            f.obstacle(ClueAgilityCourse.Rellekka, i)
        }
        assertEquals(9, f.state().phase); assertEquals(1, f.events.size)
        assertFalse((f.events.single() as AgilityLapCompletedEvent).fullGraceful)
    }

    @Test fun `low agility and a non-ninja greegree cannot start Ape Atoll`() {
        for (low in listOf(true, false)) {
            val f = Fixture(elite(5)); f.register(f.agility)
            if (low) { f.player.worn[3] = InvObj("obj.mm2_kruk_greegree"); f.player.statMap.setCurrentLevel("stat.agility", 47) } else f.player.worn[3] = InvObj("obj.mm_monkey_greegree_for_normal_monkey")
            f.obstacle(ClueAgilityCourse.ApeAtoll, 0)
            assertEquals(0, f.player.vars[ClueAgilityScript.STEP]); assertEquals(9, f.state().phase)
        }
    }

    @Test fun `native aerial Catch consumes bait only with the committed eel and awards both skills`() {
        val f = Fixture(elite(3)); f.aerialGear(); f.register(f.aerial)
        val npc = f.npc(AerialFishingScript.SPOT, CoordGrid(1361, 3622, 0)); f.player.coords = CoordGrid(1367, 3622, 0)
        f.npcOp(npc)
        assertEquals(1, f.player.inv.count("obj.aerial_fishing_mottled_eel")); assertEquals(0, f.player.inv.count(AerialFishingScript.CHUNKS))
        assertEquals(65, f.player.statMap.getXP("stat.fishing")); assertEquals(90, f.player.statMap.getXP("stat.hunter"))
        f.completed(); f.sherlock()
    }

    @Test fun `full inventory and a disappeared aerial spot leave bait XP and clue untouched`() {
        for (full in listOf(true, false)) {
            val f = Fixture(elite(3)); f.aerialGear(); f.register(f.aerial)
            val npc = f.npc(AerialFishingScript.SPOT, CoordGrid(1361, 3622, 0)); f.player.coords = CoordGrid(1367, 3622, 0)
            // More than one bait remains, so consuming a stack does not free a slot.
            f.player.inv[1] = InvObj(AerialFishingScript.CHUNKS, 2)
            if (full) f.fill(2)
            val before = f.player.inv.objs.toList()
            val pending = f.begin { f.npcAction(npc) }
            if (!full) npc.coords = npc.coords.translate(1, 0)
            f.finish(pending)
            assertEquals(before, f.player.inv.objs.toList()); assertEquals(9, f.state().phase); assertTrue(f.events.isEmpty())
            assertEquals(0, f.player.statMap.getXP("stat.fishing"))
        }
    }

    @Test fun `native butterfly Catch fills a jar and Release returns an empty jar without repeating XP`() {
        val f = Fixture(elite(11)); f.player.inv[1] = InvObj("obj.hunting_butterfly_net"); f.player.inv[2] = InvObj("obj.butterfly_jar")
        f.register(f.warlock); val npc = f.npc("npc.butterfly_warlock", CoordGrid(1233, 3745, 0)); f.player.coords = npc.coords.translate(1, 0)
        f.npcOp(npc); f.completed()
        assertEquals(125, f.player.statMap.getXP("stat.hunter")); assertEquals(1, f.player.inv.count("obj.butterfly_jar_warlock"))
        val slot = f.player.inv.objs.indexOfFirst { it?.id == "obj.butterfly_jar_warlock".asRSCM() }
        val item = f.player.inv[slot]!!
        f.run { f.bus.publish(f.access, HeldObjEvents.Op4(slot, item, ServerCacheManager.getItem(item.id)!!, f.player.inv)); Unit }
        assertEquals(1, f.player.inv.count("obj.butterfly_jar")); assertEquals(125, f.player.statMap.getXP("stat.hunter")); f.sherlock()
    }

    @Test fun `black warlocks refuse missing equipment low level and a hidden target`() {
        for (failure in 0..2) {
            val f = Fixture(elite(11)); f.register(f.warlock)
            f.player.inv[1] = InvObj("obj.hunting_butterfly_net"); f.player.inv[2] = InvObj("obj.butterfly_jar")
            if (failure == 0) f.player.inv[1] = null
            if (failure == 1) f.player.statMap.setCurrentLevel("stat.hunter", 44)
            val npc = f.npc("npc.butterfly_warlock", CoordGrid(1233, 3745, 0)); f.player.coords = npc.coords.translate(1, 0)
            if (failure == 2) npc.hidden = true
            f.npcOp(npc); assertEquals(9, f.state().phase); assertEquals(1, f.player.inv.count("obj.butterfly_jar")); assertTrue(f.events.isEmpty())
        }
    }

    @Test fun `real red chinchompa and tecu traps award the catch when checked by their owner`() {
        for (kind in ClueTrapHunting.Kind.entries) {
            val f = Fixture(if (kind == ClueTrapHunting.Kind.Tecu) master(26) else elite(12)); f.register(f.traps)
            val tile = if (kind == ClueTrapHunting.Kind.Tecu) CoordGrid(1470, 3087, 0) else CoordGrid(1316, 3168, 0)
            f.npc(kind.npc, tile.translate(1, 0)); f.lay(kind, tile)
            f.tick(5); f.traps.onPostTick(f.player)
            f.locOp(f.bound(f.worldLocs.getValue(tile)), InteractionOp.Op1)
            f.completed(); f.sherlock()
            for (variable in kind.materials.values) assertEquals(0, f.player.vars[variable])
            for (item in kind.materials.keys) assertEquals(1, f.player.inv.count(item))
            assertEquals(kind.xp.toInt(), f.player.statMap.getXP("stat.hunter"))
        }
    }

    @Test fun `foreign trap checks and a full owner inventory cannot consume or duplicate a catch`() {
        val f = Fixture(elite(12)); f.register(f.traps); val tile = CoordGrid(1316, 3168, 0)
        f.npc(ClueTrapHunting.Kind.RedChinchompa.npc, tile.translate(1, 0)); f.lay(ClueTrapHunting.Kind.RedChinchompa, tile)
        f.tick(5); f.traps.onPostTick(f.player)
        val loc = f.bound(f.worldLocs.getValue(tile)); val other = Fixture(elite(12), 2)
        other.player.coords = tile
        other.run { f.bus.publish(other.access, f.locOps.opTrigger(other.player, loc, InteractionOp.Op1)!!); Unit }
        assertEquals(9, other.state().phase); assertEquals(0, other.player.inv.count("obj.chinchompa_big_captured"))
        f.fill(1); val before = f.player.inv.objs.toList(); f.locOp(loc, InteractionOp.Op1)
        assertEquals(before, f.player.inv.objs.toList()); assertEquals(9, f.state().phase)
        f.player.inv[1] = null; f.locOp(loc, InteractionOp.Op2)
        assertEquals(1, f.player.inv.count("obj.chinchompa_big_captured")); f.completed()
        assertEquals(1, f.player.vars["varp.hunter_escrow_box"])
        f.player.inv[2] = null; f.traps.cleanup(f.player); assertEquals(1, f.player.inv.count("obj.hunting_box_trap"))
    }

    @Test fun `trap cleanup retains material escrow through full inventory and restores it once after space is made`() {
        val f = Fixture(elite(12)); f.register(f.traps); f.lay(ClueTrapHunting.Kind.RedChinchompa, CoordGrid(1316, 3168, 0)); f.fill(1)
        f.traps.cleanup(f.player); assertTrue(f.worldLocs.isEmpty()); assertEquals(1, f.player.vars["varp.hunter_escrow_box"])
        f.player.inv[1] = null; f.tick(10 - f.player.currentMapClock % 10); f.traps.onPostTick(f.player)
        assertEquals(1, f.player.inv.count("obj.hunting_box_trap")); assertEquals(0, f.player.vars["varp.hunter_escrow_box"])
        f.tick(10); f.traps.onPostTick(f.player); assertEquals(1, f.player.inv.count("obj.hunting_box_trap")); assertEquals(9, f.state().phase)
    }

    @Test fun `Skullball Tap Kick and Shoot produce native ball routes and actual goals complete the clue`() {
        val f = Fixture(elite(4)); f.player.worn[12] = InvObj("obj.ring_of_charos"); f.register(f.skullball)
        val boss = f.npc("npc.werewolf_skullballboss", CoordGrid(3549, 9867, 0)); f.player.coords = boss.coords
        f.npcOp(boss)
        val ball = f.spawned.single { it.isType("npc.waa_skullball") }
        f.player.coords = ball.coords.translate(0, -1); f.npcOp(ball, InteractionOp.Op3)
        assertEquals(CoordGrid(3555, 9870, 0), ball.routeDestination.peekLast())
        // Consume the waypoints emitted by the native kick producer as the movement engine does.
        while (ball.routeDestination.isNotEmpty()) { ball.coords = checkNotNull(ball.routeDestination.pollFirst()); f.tick(1); f.skullball.onPostTick(f.player) }
        f.completed(); f.sherlock()
        assertEquals(1, f.events.filterIsInstance<SkullballGoalScoredEvent>().size)
        f.skullball.onPostTick(f.player); assertEquals(1, f.events.filterIsInstance<SkullballGoalScoredEvent>().size)
        f.skullball.cleanup(f.player); verify(f.npcs).del(ball, Int.MAX_VALUE)
    }

    @Test fun `native Master Reanimation consumes the head and actual rune requirements before an owned credited kill`() {
        val f = Fixture(master(23)); f.register(f.reanimation); f.reanimationInputs(); f.castHead()
        val npc = f.spawned.single { it.isType("npc.arceuus_reanimated_abyssal") }
        for (item in listOf("obj.arceuus_corpse_abyssal", "obj.naturerune", "obj.soulrune", "obj.bloodrune")) assertEquals(0, f.player.inv.count(item))
        assertEquals(9, f.state().phase)
        assertTrue(f.reanimation.validate(Fixture(master(23), 2).player, npc) is NpcAttackValidateResult.Deny)
        npc.recordDamage(f.player, 20); npc.hitpoints = 0
        val death = NpcDeath(f.npcs, f.players, mock(ObjRepository::class.java), emptySet(), setOf(f.reanimation), mock(BossRespawnTimers::class.java))
        val access = StandardNpcAccess(npc, GameCoroutine("reanimated-death"), StandardNpcAccessContext({ DefaultGameRandom(42) }, { mock(NpcHitModifier::class.java) }, { mock(NpcHitProcessor::class.java) }))
        death.spawnDrops(access, rewards = NpcDeathRewards(includeRemains = false))
        assertEquals(1300, f.player.statMap.getXP("stat.prayer")); f.completed(); f.sherlock()
    }

    @Test fun `wrong spellbook missing runes low Slayer and stale head cannot spawn or consume a reanimation`() {
        for (failure in 0..3) {
            val f = Fixture(master(23)); f.register(f.reanimation); f.reanimationInputs()
            when (failure) { 0 -> VarPlayerIntMapSetter.set(f.player, "varbit.spellbook", 0); 1 -> f.player.inv[4] = null; 2 -> f.player.statMap.setCurrentLevel("stat.slayer", 84) }
            val before = f.player.inv.objs.toList()
            f.castHead(if (failure == 3) { { f.player.inv[1] = InvObj("obj.abyssal_whip") } } else null)
            assertTrue(f.spawned.isEmpty()); assertEquals(9, f.state().phase)
            if (failure != 3) assertEquals(before, f.player.inv.objs.toList())
        }
    }

    @Test fun `spirit seed pot water sapling planting offline growth health check and own destination form a real clue route`() {
        val f = Fixture(master(2)); f.register(f.spirit)
        f.player.inv[1] = InvObj("obj.spirit_tree_seed"); f.player.inv[2] = InvObj("obj.plantpot_compost"); f.player.inv[3] = InvObj("obj.trowel"); f.player.inv[4] = InvObj("obj.watering_can_8")
        f.itemOnItem(1, 2)
        val seedling = f.player.inv.objs.indexOfFirst { it?.id == SpiritTreeScript.SEEDLING.asRSCM() }
        val can = f.player.inv.objs.indexOfFirst { it?.id == "obj.watering_can_8".asRSCM() }
        f.itemOnItem(can, seedling); assertEquals(1, f.player.inv.count(SpiritTreeScript.WATERED))
        f.clock.now += 5; f.spirit.refresh(f.player); assertEquals(1, f.player.inv.count(SpiritTreeScript.SAPLING))
        f.player.inv[10] = InvObj("obj.rake"); f.player.inv[11] = InvObj("obj.spade")
        val patch = SpiritTreeScript.patches.first(); val loc = f.loc(patch.loc, patch.coords)
        f.locOp(loc, InteractionOp.Op1)
        val slot = f.player.inv.objs.indexOfFirst { it?.id == SpiritTreeScript.SAPLING.asRSCM() }
        f.useLoc(loc, slot); assertEquals(f.clock.now, f.player.vars[patch.planted]); assertEquals(9, f.state().phase)
        f.clock.now += SpiritTreeScript.GROWTH_MINUTES; f.locOp(loc, InteractionOp.Op1)
        assertEquals(3 or SpiritTreeScript.CHECKED, f.player.vars[patch.state]); assertEquals(9, f.state().phase)
        f.allocate(patch.destination)
        val tree = f.npc("npc.treevillage_spirittree", CoordGrid(2542, 3168, 0)); f.player.coords = tree.coords
        val pending = f.begin { f.npcAction(tree) }
        f.coroutine.resumeWith(ResumePauseButtonInput("component.chatmenu:options", 0)); f.finish(pending)
        assertEquals(patch.destination, f.player.coords); f.completed(); f.sherlock()
    }

    @Test fun `empty spirit patches and low Farming never grant an owned destination`() {
        val f = Fixture(master(2)); f.register(f.spirit)
        val patch = SpiritTreeScript.patches.first(); val loc = f.loc(patch.loc, patch.coords)
        f.player.inv[1] = InvObj(SpiritTreeScript.SAPLING); f.player.inv[2] = InvObj("obj.spade")
        VarPlayerIntMapSetter.set(f.player, patch.state, 3); f.player.statMap.setCurrentLevel("stat.farming", 82)
        f.useLoc(loc, 1); assertEquals(1, f.player.inv.count(SpiritTreeScript.SAPLING)); assertEquals(0, f.player.vars[patch.planted])
        f.player.statMap.setCurrentLevel("stat.farming", 99)
        f.npcOp(f.npc("npc.treevillage_spirittree", CoordGrid(2542, 3168, 0)))
        assertEquals(9, f.state().phase); assertTrue(f.events.isEmpty())
    }

    @Test fun `three real cryptic visits give owned parts whose single atomic assembly yields the final casket`() {
        val f = Fixture(TrailTornParts.ROW, phase = 0); f.register(f.parts)
        val initial = f.state()
        for (index in 0..2) f.partVisit(index)
        assertEquals(7, f.state().phase); assertEquals(0, f.state().completed)
        for (part in TrailTornParts.parts) assertEquals(1, f.player.inv.objs.filterNotNull().count { it.id == part.asRSCM() && it.vars == TrailPuzzleItems.owner(initial) })
        f.player.inv[0] = f.player.inv[0]!!.copy() // Saved item-local state still owns its parts.
        val slot = f.player.inv.objs.indexOfFirst { it?.id == TrailTornParts.parts.first().asRSCM() }
        f.held1(slot)
        assertEquals(1, f.player.inv.count(TrailTier.MASTER.casket)); for (part in TrailTornParts.parts) assertEquals(0, f.player.inv.count(part))
    }

    @Test fun `torn part duplicate visits full inventory and unrelated parts preserve the incomplete trail`() {
        val f = Fixture(TrailTornParts.ROW, phase = 0); f.register(f.parts)
        f.fill(1); val before = f.player.inv.objs.toList(); f.partVisit(0)
        assertEquals(before, f.player.inv.objs.toList()); assertEquals(0, f.state().phase)
        for (slot in 1..4) f.player.inv[slot] = null
        f.partVisit(0); val partial = f.player.inv.objs.toList(); f.partVisit(0); assertEquals(partial, f.player.inv.objs.toList())
        val owned = f.player.inv.objs.indexOfFirst { it?.id == TrailTornParts.parts.first().asRSCM() }
        f.held1(owned); assertEquals(1, f.state().phase); assertEquals(0, f.player.inv.count(TrailTier.MASTER.casket))
    }

    @Test fun `cancelled native obstacle lands safely without XP lap progress or temporary monkey model`() {
        val f = Fixture(elite(5)); f.register(f.agility)
        f.player.worn[3] = InvObj("obj.mm_monkey_greegree_for_small_ninja_monkey")
        val loc = f.loc("loc.100_ilm_stepping_stone", CoordGrid(2754, 2742, 0)); f.allocate(CoordGrid(2752, 2742, 0))
        f.begin { f.player.activeCoroutine = f.coroutine; f.bus.publish(f.access, f.locOps.opTrigger(f.player, loc, InteractionOp.Op1)!!); Unit }
        f.coroutine.cancel()
        assertEquals(CoordGrid(2752, 2742, 0), f.player.coords); assertNull(f.player.transmog)
        assertEquals(0, f.player.statMap.getXP("stat.agility")); assertEquals(0, f.player.vars[ClueAgilityScript.STEP]); assertEquals(9, f.state().phase)
    }

    @Test fun `failed native trap can be dismantled without catch XP or clue completion`() {
        val f = Fixture(elite(12)); f.register(f.traps); val tile = CoordGrid(1316, 3168, 0)
        f.npc(ClueTrapHunting.Kind.RedChinchompa.npc, tile.translate(1, 0)); f.lay(ClueTrapHunting.Kind.RedChinchompa, tile)
        `when`(f.random.randomDouble()).thenReturn(1.0); f.tick(5); f.traps.onPostTick(f.player)
        assertEquals("loc.hunting_boxtrap_failed".asRSCM(), f.worldLocs.getValue(tile).id)
        f.locOp(f.bound(f.worldLocs.getValue(tile)), InteractionOp.Op1)
        assertEquals(1, f.player.inv.count("obj.hunting_box_trap")); assertEquals(0, f.player.inv.count("obj.chinchompa_big_captured"))
        assertEquals(0, f.player.statMap.getXP("stat.hunter")); assertEquals(9, f.state().phase)
    }

    @Test fun `reanimated actor cleanup on departure death and timeout cannot grant kill credit`() {
        for (mode in 0..2) {
            val f = Fixture(master(23)); f.register(f.reanimation); f.reanimationInputs(); f.castHead()
            val actor = f.spawned.single()
            when (mode) { 0 -> { f.player.coords = f.player.coords.translate(22, 0); f.reanimation.onPostTick(f.player) }; 1 -> f.reanimation.cleanup(f.player); 2 -> { f.tick(300); f.reanimation.onPostTick(f.player) } }
            verify(f.npcs, times(1)).del(actor, Int.MAX_VALUE)
            f.reanimation.onKill(NpcDeathKillContext(f.player, actor, 0))
            assertEquals(0, f.player.statMap.getXP("stat.prayer")); assertEquals(9, f.state().phase)
        }
    }

    @Test fun `native skullball routes complete all goals award one game reward and remove the owned ball`() {
        val f = Fixture(elite(4)); f.register(f.skullball); f.player.worn[12] = InvObj("obj.ring_of_charos")
        f.npcOp(f.npc("npc.werewolf_skullballboss", CoordGrid(3549, 9867, 0)))
        val ball = f.spawned.single()
        for (goal in SkullballScript.goals) {
            while (ball.coords != goal) {
                val dx = (goal.x - ball.coords.x).coerceIn(-1, 1); val dz = if (dx == 0) (goal.z - ball.coords.z).coerceIn(-1, 1) else 0
                f.allocate(ball.coords.translate(dx, dz)); f.player.coords = ball.coords.translate(-dx, -dz)
                f.npcOp(ball, InteractionOp.Op1)
                ball.coords = checkNotNull(ball.routeDestination.pollFirst()); f.tick(1); f.skullball.onPostTick(f.player)
            }
        }
        assertEquals(11, f.events.filterIsInstance<SkullballGoalScoredEvent>().size)
        assertEquals(750, f.player.statMap.getXP("stat.agility")); verify(f.npcs, times(1)).del(ball, Int.MAX_VALUE)
        f.skullball.onPostTick(f.player); assertEquals(750, f.player.statMap.getXP("stat.agility"))
    }

    @Test fun `foreign and stale Combine payloads cannot consume solved owned torn parts`() {
        val f = Fixture(master(25), phase = 0); f.register(f.parts); repeat(3) { f.partVisit(it) }
        val slot = f.player.inv.objs.indexOfFirst { it?.id == TrailTornParts.parts.first().asRSCM() }
        val before = f.player.inv.objs.toList(); val foreign = InvObj(TrailTornParts.parts.first(), 1, 0)
        f.run { f.bus.publish(f.access, HeldObjEvents.Op1(slot, foreign, ServerCacheManager.getItem(foreign.id)!!, f.player.inv)); Unit }
        assertEquals(before, f.player.inv.objs.toList()); assertEquals(0, f.player.inv.count(TrailTier.MASTER.casket))
        f.held1(slot); assertEquals(1, f.player.inv.count(TrailTier.MASTER.casket))
    }

    @Test fun `three native torn visits advance a nonfinal trail instead of prematurely granting a casket`() {
        val f = Fixture(master(25), phase = 0); f.register(f.parts)
        val state = f.state().copy(total = 2); f.player.inv[0] = InvObj(ServerCacheManager.getItem(f.player.inv[0]!!.id)!!, 1, state.encode())
        repeat(3) { f.partVisit(it) }
        f.held1(f.player.inv.objs.indexOfFirst { it?.id == TrailTornParts.parts.first().asRSCM() })
        assertEquals(1, f.state().completed); assertEquals(2, f.state().total); assertEquals(0, f.player.inv.count(TrailTier.MASTER.casket))
        for (part in TrailTornParts.parts) assertEquals(0, f.player.inv.count(part))
    }

    private class MutableClock : FarmingClock() { var now = 100_000; override fun minute() = now }
    private class Fixture(row: Int, playerId: Int = 1, phase: Int = 9) {
        val bus = EventBus(); val scripts = ScriptContext(bus, CheatCommandMap(), EngineQueueCache())
        val random = mock(GameRandom::class.java).apply {
            `when`(randomDouble()).thenReturn(0.0); `when`(of(0, 3)).thenReturn(2); `when`(of(1, 5)).thenReturn(1); `when`(of(1, 1000)).thenReturn(1000)
        }
        val progress = TrailProgress(TrailCatalog(), DefaultGameRandom(42)); val coroutine = GameCoroutine("native-final-clue-task")
        val player = Player().apply {
            uuid = playerId.toLong(); slotId = playerId; assignUid(); currentMapClock = 100; processedMapClock = 100; activeCoroutine = coroutine
            inv = Inventory(ServerCacheManager.getInventory("inv.inv".asRSCM())!!, arrayOfNulls(28))
            worn = Inventory(ServerCacheManager.getInventory("inv.worn".asRSCM())!!, arrayOfNulls(14))
            for (stat in listOf("agility", "hunter", "fishing", "cooking", "farming", "magic", "slayer", "prayer", "attack", "strength", "defence", "hitpoints", "ranged", "crafting", "runecrafting", "mining", "smithing", "woodcutting", "firemaking", "thieving")) {
                statMap.setBaseLevel("stat.$stat", 99.toByte()); statMap.setCurrentLevel("stat.$stat", 99)
            }
        }
        val players = PlayerList().apply { set(playerId, player) }
        val collision = CollisionFlagMap(); val areas = mock(AreaChecker::class.java)
        val context = ProtectedAccessContextFactory.empty().copy(getEventBus = { bus }, getRandom = { random }, getCollision = { collision }, getAreaChecker = { areas }, getHitModifier = { NoopPlayerHitModifier }, getTeleportValidator = { PlayerTeleportValidator(emptySet()) })
        val access = ProtectedAccess(player, coroutine, context)
        val contextual = ContextualInteractions(); val npcOps = NpcInteractions(bus, contextual); val locOps = LocInteractions(mock(BoundValidator::class.java), bus)
        val itemOps = LocUInteractions::class.java.getDeclaredConstructor(EventBus::class.java).apply { isAccessible = true }.newInstance(bus)
        val worldLocs = HashMap<CoordGrid, LocInfo>()
        val locs = mock(LocRepository::class.java) { call ->
            when (call.method.name.substringBefore('-')) {
                "add" -> { val loc = call.arguments[0] as LocInfo; worldLocs[loc.coords] = loc; true }
                "del" -> { worldLocs.remove((call.arguments[0] as LocInfo).coords); true }
                "findExact" -> worldLocs[CoordGrid(call.arguments[0] as Int)].takeIf { it?.id == (call.arguments[1] as? dev.openrune.types.ObjectServerType)?.id }
                else -> RETURNS_DEFAULTS.answer(call)
            }
        }
        val npcs = mock(NpcRepository::class.java) { call ->
            when (call.method.name.substringBefore('-')) {
                "add" -> { val n = call.arguments[0] as Npc; n.slotId = 10 + spawned.size; n.assignUid(); n.lifecycleDelCycle = player.currentMapClock + (call.arguments[1] as Int); allocate(n.coords); spawned += n; null }
                "findAll" -> nearby.asSequence()
                else -> RETURNS_DEFAULTS.answer(call)
            }
        }
        val spawned = mutableListOf<Npc>(); val nearby = mutableListOf<Npc>(); val events = mutableListOf<Any>(); val xp = XpModifiers(emptySet()); val clock = MutableClock()
        val agility = ClueAgilityScript(xp); val aerial = AerialFishingScript(xp, mock(WorldRepository::class.java)); val warlock = BlackWarlockScript(npcs, xp, random)
        val traps = ClueTrapHunting(locs, npcs, random, xp); val spirit = SpiritTreeScript(clock, xp); val skullball = SkullballScript(npcs, bus, collision, xp)
        val parts = TrailTornParts(progress, TrailTargets(progress), TrailRequirements(progress.catalog), contextual)
        val spells = repository(MagicSpellRegistry()); val runes = MagicRuneManager(repository(FakeRuneRepository()), repository(ComboRuneRepository()), repository(CompactRuneRepository()), repository(UnlimitedRuneRepository()), repository(StaffSubstituteRepository()), repository(RuneSubstituteRepository()), emptySet())
        val launcher = ProtectedAccessLauncher(mock(ProtectedAccessContextFactory::class.java).apply { `when`(create()).thenReturn(context) })
        val reanimation = AbyssalReanimationScript(bus, launcher, spells, runes, npcs, collision, xp)
        init {
            `when`(areas.inArea("area.dark_altar", CoordGrid(1718, 3886, 0))).thenReturn(true)
            register(InvTransactionsScript(PlayerItemStorage(emptySet())))
            register(TrailSkillChallenges(progress, TrailTargets(progress), TrailRequirements(progress.catalog)))
            scripts.onEvent<SkillingActionCompleteEvent> { events += this }; scripts.onEvent<AgilityLapCompletedEvent> { events += this }
            scripts.onEvent<SkullballGoalScoredEvent> { events += this }; scripts.onEvent<OwnedSpiritTreeTravelEvent> { events += this }; scripts.onEvent<ReanimatedAbyssalKilledEvent> { events += this }
            val clue = progress.catalog.clues.getValue(row)
            player.inv[0] = InvObj(ServerCacheManager.getItem(progress.catalog.item(clue))!!, 1, TrailState(row, 1, phase = phase).encode())
        }
        fun register(script: PluginScript) { with(script) { scripts.startup() } }
        fun allocate(tile: CoordGrid) { collision.allocateIfAbsent(tile.x, tile.z, tile.level) }
        fun tick(ticks: Int) { player.currentMapClock += ticks; player.processedMapClock = player.currentMapClock }
        fun state() = progress.state(player.inv[0]!!)!!
        fun completed() = assertEquals(10, state().phase)
        fun fill(from: Int) { for (i in from..27) if (player.inv[i] == null) player.inv[i] = InvObj("obj.abyssal_whip") }
        fun graceful() { for ((slot, item) in listOf(0 to "hood", 1 to "cape", 4 to "top", 7 to "legs", 9 to "gloves", 10 to "boots")) player.worn[slot] = InvObj("obj.graceful_$item") }
        fun aerialGear() { player.worn[3] = InvObj(AerialFishingScript.GLOVE); player.inv[1] = InvObj(AerialFishingScript.CHUNKS) }
        fun npc(symbol: String, tile: CoordGrid): Npc = Npc(symbol, tile).also { it.slotId = 100 + nearby.size; it.assignUid(); nearby += it; allocate(tile) }
        fun bound(info: LocInfo): BoundLocInfo = BoundLocInfo(info, ServerCacheManager.getObject(info.id)!!)
        fun loc(symbol: String, tile: CoordGrid): BoundLocInfo {
            allocate(tile); player.coords = tile
            return bound(LocInfo(2, tile, LocEntity(symbol.asRSCM(), 10, 0)).also { worldLocs[tile] = it })
        }
        suspend fun npcAction(npc: Npc, op: InteractionOp = InteractionOp.Op1) { player.activeCoroutine = coroutine; bus.publish(access, npcOps.opTrigger(player, npc, op)!!) }
        fun npcOp(npc: Npc, op: InteractionOp = InteractionOp.Op1) = run { npcAction(npc, op) }
        fun locOp(loc: BoundLocInfo, op: InteractionOp) = run { player.activeCoroutine = coroutine; bus.publish(access, locOps.opTrigger(player, loc, op)!!); Unit }
        fun useLoc(loc: BoundLocInfo, slot: Int) = run { player.activeCoroutine = coroutine; itemOps.interactOp(access, loc, loc, ServerCacheManager.getObject(loc.id)!!, ServerCacheManager.getItem(player.inv[slot]!!.id)!!, player.inv, slot) }
        fun itemOnItem(first: Int, second: Int) = run { player.activeCoroutine = coroutine; val a = ServerCacheManager.getItem(player.inv[first]!!.id)!!; val b = ServerCacheManager.getItem(player.inv[second]!!.id)!!; bus.publish(access, HeldUEvents.Type(a, first, b, second)); Unit }
        fun held1(slot: Int) = run { val item = player.inv[slot]!!; player.activeCoroutine = coroutine; bus.publish(access, HeldObjEvents.Op1(slot, item, ServerCacheManager.getItem(item.id)!!, player.inv)); Unit }
        fun lay(kind: ClueTrapHunting.Kind, tile: CoordGrid) {
            player.coords = tile
            for ((i, item) in kind.materials.keys.withIndex()) player.inv[i + 1] = InvObj(item)
            if (kind == ClueTrapHunting.Kind.RedChinchompa) held1(1) else locOp(loc("loc.hunting_sapling_up_mountain", tile), InteractionOp.Op1)
        }
        fun lap(course: ClueAgilityCourse) { for (i in course.obstacles.indices) obstacle(course, i) }
        fun obstacle(course: ClueAgilityCourse, index: Int) {
            val raw = if (course == ClueAgilityCourse.ApeAtoll) listOf(2754 to 2742, 2753 to 2741, 2752 to 2741, 2746 to 2741, 2752 to 2731, 2757 to 2734) else listOf(2625 to 3677, 2621 to 3669, 2623 to 3658, 2629 to 3656, 2643 to 3654, 2647 to 3663, 2654 to 3676)
            val (x, z) = raw[index]; val level = if (course == ClueAgilityCourse.ApeAtoll) if (index == 2) 2 else 0 else if (index == 0) 0 else 3
            val obstacle = course.obstacles[index]; val loc = loc(obstacle.loc, CoordGrid(x, z, level)); allocate(obstacle.landing(player.coords)); locOp(loc, InteractionOp.Op1)
        }
        fun reanimationInputs() { VarPlayerIntMapSetter.set(player, "varbit.spellbook", 3); player.coords = CoordGrid(1718, 3886, 0); allocate(player.coords); allocate(player.coords.translate(1, 0)); player.inv[1] = InvObj("obj.arceuus_corpse_abyssal"); player.inv[2] = InvObj("obj.naturerune", 4); player.inv[3] = InvObj("obj.soulrune", 4); player.inv[4] = InvObj("obj.bloodrune", 2) }
        fun castHead(midway: (() -> Unit)? = null) {
            player.activeCoroutine = null
            bus.publish(IfOverlayButtonT(player, 0, null, 1, ServerCacheManager.getItem(player.inv[1]!!.id)!!, Component("component.magic_spellbook:reanimation_master".asRSCM()), Component("component.inventory:items".asRSCM())))
            midway?.invoke()
            repeat(8) { tick(1); player.activeCoroutine?.advance() }
        }
        fun partVisit(index: Int) {
            val clue = parts.choices(state())[index]
            val fields = progress.catalog.fields(clue.targets.first())
            val tile = CoordGrid(fields.int("coord")); player.coords = tile
            // Outfit requirements are honored by the native contextual consumer.
            for (outfit in clue.fields.ints("outfit")) {
                val outfitFields = progress.catalog.fields(outfit)
                for ((name, slot) in mapOf("hat" to 0, "back" to 1, "front" to 2, "rhand" to 3, "torso" to 4, "lhand" to 5, "legs" to 7, "hands" to 9, "feet" to 10, "ring" to 12)) outfitFields.ints("wearpos_$name").firstOrNull()?.let { player.worn[slot] = InvObj(ServerCacheManager.getItem(it)!!) }
            }
            npcOp(npc(ServerCacheManager.getNpc(fields.int("npc"))!!.internalName, tile))
        }
        fun begin(action: suspend () -> Unit): () -> Result<Unit>? {
            var result: Result<Unit>? = null
            action.startCoroutine(object : Continuation<Unit> { override val context = EmptyCoroutineContext; override fun resumeWith(outcome: Result<Unit>) { result = outcome } })
            return { result }
        }
        fun finish(result: () -> Result<Unit>?) { repeat(30) { if (result() == null) { tick(1); coroutine.advance() } }; checkNotNull(result()).getOrThrow() }
        fun run(action: suspend () -> Unit) = finish(begin(action))
        fun sherlock() {
            val clue = progress.catalog.clues.getValue(state().row)
            val script = TrailInteractionScript(progress, TrailTargets(progress), TrailRequirements(progress.catalog), mock(TrailPuzzleScript::class.java), mock(TrailGuards::class.java), contextual, mock(MusicRepository::class.java))
            register(script); val fields = progress.catalog.fields(clue.targets.single()); npcOp(npc(ServerCacheManager.getNpc(fields.int("npc"))!!.internalName, CoordGrid(fields.int("coord"))))
            assertEquals(1, player.inv.count(clue.tier.casket)); with(script) { scripts.shutdown() }
        }
    }
    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() }
        private fun elite(index: Int) = "dbrow.cluehelper_skillchallenge_elite_$index".asRSCM()
        private fun master(index: Int) = "dbrow.cluehelper_skillchallenge_master_${if (index == 26) "vm01" else index}".asRSCM()
        private fun <T : Any> repository(value: T): T { value.javaClass.declaredMethods.single { it.name.startsWith("init") && it.parameterCount == 0 }.apply { isAccessible = true }.invoke(value); return value }
    }
}
