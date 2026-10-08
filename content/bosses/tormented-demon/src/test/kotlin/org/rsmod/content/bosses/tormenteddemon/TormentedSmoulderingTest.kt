package org.rsmod.content.bosses.tormenteddemon

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dtx.core.Single
import dtx.rs.RSDropTable
import dtx.rs.RSGuaranteedTable
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.Mockito.*
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.DropTableRegistry
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.player.hit.processor.InstantPlayerHitProcessor
import org.rsmod.api.player.interact.ObjInteractions
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.StatBoostDecayPrevention
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.table.PotionEffectRow
import org.rsmod.content.drops.NpcDropTableKillHook
import org.rsmod.content.interfaces.collectionlog.CollectionLog
import org.rsmod.content.other.consumables.potion.PotionEffectService
import org.rsmod.content.other.consumables.potion.PotionSpecialEffectService
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.obj.Obj
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(org.rsmod.annotations.InternalApi::class)
@ResourceLock("ServerCacheManager")
class TormentedSmoulderingTest {
    @Test fun `death reward pipeline creates private flesh with four usable ground options`() {
        val f = Fixture("obj.smouldering_pile_of_flesh")
        f.kill()
        val obj = f.objects().single()
        f.player.statMap.setCurrentLevel("stat.hitpoints", 50)
        val delay = f.player.actionDelay
        repeat(3) { f.consume(obj); assertEquals(1, f.objects().size) }
        assertEquals(104, f.player.statMap.getCurrentLevel("stat.hitpoints").toInt())
        f.consume(obj)
        assertEquals(117, f.player.statMap.getCurrentLevel("stat.hitpoints").toInt())
        assertTrue(f.objects().isEmpty())
        f.consume(obj)
        assertEquals(117, f.player.statMap.getCurrentLevel("stat.hitpoints").toInt())
        assertEquals(delay, f.player.actionDelay)
        assertEquals(0, f.player.inv.count("obj.smouldering_pile_of_flesh"))
    }

    @Test fun `flesh cannot be consumed at overheal cap or by a different player`() {
        val f = Fixture("obj.smouldering_pile_of_flesh"); f.kill(); val obj = f.objects().single()
        f.player.statMap.setCurrentLevel("stat.hitpoints", 117)
        val saved = f.player.invMap[TormentedSmouldering.META]!![0]
        f.consume(obj)
        assertEquals(saved, f.player.invMap[TormentedSmouldering.META]!![0])
        val stranger = Player().apply { statMap.setCurrentLevel("stat.hitpoints", 1) }
        f.effects.consume(stranger, obj)
        assertEquals(1, stranger.statMap.getCurrentLevel("stat.hitpoints").toInt())
        assertEquals(1, f.objects().size)
    }

    @Test fun `logout pauses ground expiry and copied saved inventories restore uses and location`() {
        val f = Fixture("obj.smouldering_pile_of_flesh"); f.kill(); val obj = f.objects().single()
        f.player.statMap.setCurrentLevel("stat.hitpoints", 50); f.consume(obj)
        repeat(100) { f.effects.onPostTick(f.player) }
        val items = f.player.invMap[TormentedSmouldering.ITEMS]!!.objs.map { it?.copy() }
        val meta = f.player.invMap[TormentedSmouldering.META]!!.objs.map { it?.copy() }
        f.effects.logout(f.player)
        assertTrue(f.objects().isEmpty())
        val restored = Player().apply { uuid = f.player.uuid; observerUUID = f.player.observerUUID; slotId = 1; assignUid(); coords = f.player.coords }
        items.forEachIndexed { i, item -> restored.invMap.getOrPut(TormentedSmouldering.ITEMS)[i] = item }
        meta.forEachIndexed { i, item -> restored.invMap.getOrPut(TormentedSmouldering.META)[i] = item }
        f.effects.login(restored); f.effects.login(restored)
        assertEquals(1, f.objects().size)
        assertEquals(f.player.coords, f.objects().single().coords)
        assertEquals(3, restored.invMap[TormentedSmouldering.META]!![0]!!.vars ushr 10)
        repeat(899) { f.effects.onPostTick(restored) }
        assertEquals(1, f.objects().size)
        f.effects.onPostTick(restored)
        assertTrue(f.objects().isEmpty())
        Assertions.assertNull(restored.invMap[TormentedSmouldering.ITEMS]!![0])
    }

    @Test fun `heart maintains all five boosts for two minutes and synchronises native buff icon`() {
        val f = Fixture("obj.smouldering_heart"); f.kill(); f.consume(f.objects().single())
        assertEquals(119, f.player.statMap.getCurrentLevel("stat.attack").toInt())
        assertEquals(113, f.player.statMap.getCurrentLevel("stat.ranged").toInt())
        assertEquals(110, f.player.statMap.getCurrentLevel("stat.magic").toInt())
        assertEquals(8, f.player.vars["varbit.wgs_smouldering_heart_timer"])
        f.player.statMap.setCurrentLevel("stat.attack", 100)
        repeat(199) { f.effects.onPostTick(f.player) }
        assertEquals(100, f.player.statMap.getCurrentLevel("stat.attack").toInt())
        assertTrue(StatBoostDecayPrevention.prevents(f.player, "stat.attack"))
        f.effects.onPostTick(f.player)
        assertEquals(0, f.player.vars[TormentedSmouldering.HEART])
        assertEquals(0, f.player.vars["varbit.wgs_smouldering_heart_timer"])
        assertEquals(100, f.player.statMap.getCurrentLevel("stat.attack").toInt())
        assertFalse(StatBoostDecayPrevention.prevents(f.player, "stat.attack"))
    }

    @Test fun `divine expiry clears a smouldering boost without clearing unrelated maintained effects`() {
        for (otherSource in listOf(false, true)) {
            val f = Fixture("obj.smouldering_heart"); f.kill()
            val potions = PotionEffectService(mock(PotionSpecialEffectService::class.java), MapClock())
            val context = ProtectedAccessContextFactory.empty().copy(getInstantHitProcessor = { mock(InstantPlayerHitProcessor::class.java) })
            assertTrue(ProtectedAccessLauncher.withProtectedAccess(f.player, context) {
                potions.apply(this, PotionEffectRow.getRow("dbrow.effect_divine_super_attack_boost"))
            })
            f.consume(f.objects().single())
            if (otherSource) StatBoostDecayPrevention.add(f.player, "stat.attack", "another.native.effect")
            f.player.currentMapClock = 1000
            assertTrue(ProtectedAccessLauncher.withProtectedAccess(f.player, context) { potions.processDivineEffects(this) })
            f.effects.onPostTick(f.player)
            assertEquals(if (otherSource) 119 else 99, f.player.statMap.getCurrentLevel("stat.attack").toInt())
            assertEquals(119, f.player.statMap.getCurrentLevel("stat.strength").toInt())
        }
    }

    @Test fun `active heart survives a death reset and removes temporary ownership at logout and unload`() {
        val f = Fixture("obj.smouldering_heart"); f.kill(); f.consume(f.objects().single())
        f.player.statMap.setCurrentLevel("stat.hitpoints", 0); f.effects.onPostTick(f.player)
        f.player.statMap.setCurrentLevel("stat.attack", 99)
        f.player.statMap.setCurrentLevel("stat.hitpoints", 99); f.effects.onPostTick(f.player)
        assertEquals(119, f.player.statMap.getCurrentLevel("stat.attack").toInt())
        f.effects.logout(f.player)
        assertFalse(StatBoostDecayPrevention.prevents(f.player, "stat.attack"))
        val remaining = f.player.vars[TormentedSmouldering.HEART]
        f.effects.login(f.player)
        assertEquals(remaining, f.player.vars[TormentedSmouldering.HEART])
        assertTrue(StatBoostDecayPrevention.prevents(f.player, "stat.attack"))
        f.effects.shutdown()
        assertFalse(StatBoostDecayPrevention.prevents(f.player, "stat.attack"))
    }

    @Test fun `gland restores prayer and any native teleport ends its buff`() {
        val f = Fixture("obj.smouldering_gland"); f.kill(); f.player.statMap.setCurrentLevel("stat.prayer", 0)
        f.consume(f.objects().single()); f.effects.onPostTick(f.player)
        assertEquals(12, f.player.statMap.getCurrentLevel("stat.prayer").toInt())
        assertEquals(20, f.player.vars["varbit.wgs_smouldering_gland_timer"])
        f.player.pendingTelejump = true; f.effects.onPostTick(f.player)
        assertEquals(0, f.player.vars[TormentedSmouldering.GLAND])
        // Teleport cleanup must clear the native icon even before normal timer processing.
        assertEquals(0, f.player.vars["varbit.wgs_smouldering_gland_timer"])
    }

    @Test fun `take hook blocks smouldering items while preserving ordinary loot`() {
        val f = Fixture("obj.smouldering_heart"); f.kill()
        val hook = SmoulderingTakeHook(); val obj = f.objects().single()
        Assertions.assertNotNull(hook.validateTake(f.player, obj, ServerCacheManager.getItem(obj.type)!!))
        val ordinary = Obj.fromOwner(f.player, f.player.coords, "obj.dragon_dagger", 1)
        Assertions.assertNull(hook.validateTake(f.player, ordinary, ServerCacheManager.getItem(ordinary.type)!!))
    }
    private class Fixture(val reward: String) {
        val bus = EventBus()
        val player = Player().apply {
            uuid = 1; observerUUID = 1; slotId = 1; assignUid(); coords = CoordGrid(4075, 4427)
            inv = invMap.getOrPut("inv.inv")
            for (name in listOf("hitpoints", "attack", "strength", "defence", "ranged", "magic", "prayer")) {
                statMap.setBaseLevel("stat.$name", 99.toByte()); statMap.setCurrentLevel("stat.$name", 99)
            }
        }
        val repo = ObjRepository(MapClock(100), ObjRegistry(mock(ZoneUpdateMap::class.java)))
        val random = mock(GameRandom::class.java).apply { `when`(of(10, 15)).thenReturn(12) }
        val effects = TormentedSmouldering(repo, random)
        val areas = mock(AreaChecker::class.java)
        val tables = mock(DropTableRegistry::class.java)
        val npc = Npc(ServerCacheManager.getNpc("npc.tormented_demon_1".asRSCM())!!, player.coords)
        val hook: NpcDropTableKillHook
        init {
            val scripts = ScriptContext(bus, CheatCommandMap(), EngineQueueCache())
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { scripts.startup() }
            with(TormentedSmoulderingScript(effects)) { scripts.startup() }
            val table = RSDropTable<Player, DropRollItem>("test TD rewards", guaranteed = RSGuaranteedTable("test", listOf(Single(DropRollItem(reward, 1)))))
            `when`(tables.forNpc(npc, areas)).thenReturn(table)
            hook = NpcDropTableKillHook(CollectionLog(PlayerList(), mock(MarketPrices::class.java)), tables, areas, repo, random, setOf(effects))
        }
        fun kill() = hook.onKill(NpcDeathKillContext(player, npc, 0))
        fun objects() = repo.findAll(player.coords).toList()
        fun consume(obj: Obj) {
            val event = ObjInteractions(bus).opTrigger(obj, InteractionOp.Op3) ?: error("Missing native ground action")
            assertTrue(ProtectedAccessLauncher.withProtectedAccess(player, ProtectedAccessContextFactory.empty().copy(getEventBus = { bus })) { bus.publish(this, event) })
        }
    }
    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
