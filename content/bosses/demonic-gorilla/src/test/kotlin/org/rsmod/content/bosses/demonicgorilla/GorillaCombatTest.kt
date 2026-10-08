package org.rsmod.content.bosses.demonicgorilla

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.Mockito.*
import org.rsmod.api.bosses.runtime.*
import org.rsmod.api.combat.formulas.*
import org.rsmod.api.npc.access.*
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.player.hit.modifier.StandardPlayerHitModifier
import org.rsmod.api.player.hit.processor.StandardPlayerHitProcessor
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.protect.*
import org.rsmod.api.random.*
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.*
import org.rsmod.game.entity.npc.*
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.*
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@ResourceLock("ServerCacheManager")
@OptIn(org.rsmod.annotations.InternalApi::class)
internal class GorillaCombatTest {
    @Test fun `Tortured switches after four actual misses while Demonic still switches after three`() {
        for ((symbol, count) in listOf("npc.mm2_tortured_gorilla_1" to 4, "npc.mm2_tortured_gorilla_2" to 4, "npc.mm2_demon_gorilla_1_melee" to 3)) {
            val f = Fixture(symbol)
            repeat(count - 1) { f.land(HitType.Melee, 0); assertEquals("melee", f.encounter.currentPhaseName) }
            f.land(HitType.Melee, 0); assertNotEquals("melee", f.encounter.currentPhaseName)
            assertEquals(0, f.npc.vars["varn.gorilla_miss_streak"])
        }
    }

    @Test fun `successful damage resets the streak and typeless boulders are ignored`() {
        val f = Fixture("npc.mm2_tortured_gorilla_1")
        repeat(3) { f.land(HitType.Melee, 0) }
        f.land(HitType.Typeless, 0); assertEquals(3, f.npc.vars["varn.gorilla_miss_streak"])
        f.land(HitType.Melee, 1); assertEquals(0, f.npc.vars["varn.gorilla_miss_streak"])
        repeat(3) { f.land(HitType.Melee, 0) }; assertEquals("melee", f.encounter.currentPhaseName)
        f.land(HitType.Melee, 0); assertNotEquals("melee", f.encounter.currentPhaseName)
    }

    @Test fun `native protection turns positive attacks into four misses and respawn resets state`() {
        val f = Fixture("npc.mm2_tortured_gorilla_2")
        val access = ProtectedAccess(f.player, GameCoroutine(), f.context)
        access.vars["varbit.prayer_protectfrommelee"] = 1
        repeat(3) { f.land(HitType.Melee, 13) }; assertEquals("melee", f.encounter.currentPhaseName)
        f.land(HitType.Melee, 13); assertEquals("ranged", f.encounter.currentPhaseName)
        f.bus.publish(NpcStateEvents.Respawn(f.npc)); assertEquals(0, f.npc.vars["varn.gorilla_miss_streak"])
        assertEquals("melee", f.encounter.currentPhaseName)
    }

    @Test fun `all three shared abilities emit the same native attack sequences`() {
        for (symbol in listOf("npc.mm2_tortured_gorilla_1", "npc.mm2_demon_gorilla_1_melee")) {
            val f = Fixture(symbol)
            val access = StandardNpcAccess(f.npc, GameCoroutine(), StandardNpcAccessContext({ DefaultGameRandom(1) }, { StandardNpcHitModifier(f.bus) }, { StandardNpcHitProcessor(PlayerList(), f.bus, emptySet()) }))
            for ((ability, seq) in listOf("melee_attack" to "seq.demonic_gorilla_punch", "ranged_attack" to "seq.demonic_gorilla_range", "magic_attack" to "seq.demonic_gorilla_magic")) {
                f.npc.resetAnim()
                clearInvocations(f.info)
                EffectInterpreter(f.npc, f.player, f.encounter.spec, f.encounter, f.deps).run(access, f.encounter.spec.abilities.getValue(ability))
                verify(f.info).setSequence(seq.asRSCM(), 0)
            }
        }
    }
    private class Fixture(symbol: String) {
        val clock = MapClock(100); val bus = EventBus(); val collision = CollisionFlagMap(); val npcs = NpcList(); val players = PlayerList()
        val player = Player().apply {
            slotId = 1; uuid = 1; assignUid(); coords = CoordGrid(2084, 5664)
            statMap.setBaseLevel("stat.hitpoints", 99); statMap.setCurrentLevel("stat.hitpoints", 99)
            worn = Inventory(ServerCacheManager.getInventory("inv.worn".asRSCM())!!, arrayOfNulls(14))
        }
        val repo = NpcRepository(clock, NpcRegistry(npcs, collision, bus), npcs)
        val modifier = StandardPlayerHitModifier(bus)
        val deps = BossDeps(mock(GameRandom::class.java) { 0 }, mock(WorldRepository::class.java), repo, mock(LocRepository::class.java), players, clock, WorldQueueList(), collision, EncounterRegistry(clock), BossExtensionRegistry(), mock(AccuracyFormulae::class.java), mock(MaxHitFormulae::class.java), modifier)
        val script = DemonicGorilla(deps, npcs)
        val npc = Npc(ServerCacheManager.getNpc(symbol.asRSCM())!!, player.coords.translateZ(1))
        val info = mock(NpcInfoProtocol::class.java)
        val context = ProtectedAccessContextFactory.empty().copy(getEventBus = { bus }, getNpcList = { npcs }, getPlayerList = { players }, getRandom = { DefaultGameRandom(1) })
        val encounter get() = deps.encounter(npc)
        init {
            players[1] = player
            val scripts = ScriptContext(bus, CheatCommandMap(), EngineQueueCache()); with(script) { scripts.startup() }
            npc.infoProtocol = info; repo.add(npc, Int.MAX_VALUE)
        }
        fun land(type: HitType, damage: Int) {
            val hit = player.queueHit(npc, 1, type, damage, modifier)
            val access = ProtectedAccess(player, GameCoroutine(), context)
            with(StandardPlayerHitProcessor) { access.process(hit) }
        }
    }
    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
