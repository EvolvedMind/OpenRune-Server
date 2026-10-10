package org.rsmod.content.drops

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.dataformat.toml.TomlFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.Mockito.*
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.droptable.*
import org.rsmod.api.droptable.toml.*
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.drops.toml.ContentDropTableTomlResolver
import org.rsmod.content.interfaces.collectionlog.CollectionLog
import org.rsmod.content.quest.manager.QuestRequirementResolver
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.*
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@ResourceLock("ServerCacheManager")
internal class LizardmanShamanDropsTest {
    private fun definition(): TomlDropTableDef = ObjectMapper(TomlFactory()).registerKotlinModule()
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).readValue(
            javaClass.getResourceAsStream("/drops/tables/monsters/lizardman_shaman.toml")!!)

    @Test fun `production table exposes one warhammer roll at exactly one in three thousand`() {
        val checker = mock(AreaChecker::class.java)
        val resolver = ContentDropTableTomlResolver(checker, mock(QuestRequirementResolver::class.java))
        val def = definition(); assertEquals(5, def.npcs.size)
        val table = DropTableTomlParser.parse(def, resolver)
        val entry = DropTablePreview.entries(table).single { it.item?.obj == "obj.dragon_warhammer" }
        assertEquals(1.0 / 3000, entry.baseChance, 1e-12)
        assertTrue(def.npcs.none { it.contains("raids") })
        assertEquals(1..1, entry.item!!.count)
    }

    @Test fun `successful warhammer rolls use native ground loot and increment collection log for every variant`() {
        val def = definition(); val checker = mock(AreaChecker::class.java)
        val resolver = ContentDropTableTomlResolver(checker, mock(QuestRequirementResolver::class.java))
        val unique = def.tertiary.single { it.obj == "obj.dragon_warhammer" }
        val forced = DropTableTomlParser.parse(def.copy(main = null, tertiary = listOf(unique.copy(denominator = 1))), resolver)
        val player = Player().apply { uuid = 1; observerUUID = 1; currentMapClock = 100 }
        val registry = mock(DropTableRegistry::class.java); val repository = mock(ObjRepository::class.java)
        val players = PlayerList().apply { this[1] = player }
        val bus = EventBus(); with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
            ScriptContext(bus, CheatCommandMap(), EngineQueueCache()).startup()
        }
        val hook = NpcDropTableKillHook(CollectionLog(players, mock(MarketPrices::class.java)), registry, checker, repository, DefaultGameRandom(1), emptySet())
        val coords = CoordGrid(1451, 3696)
        `when`(repository.add("obj.dragon_warhammer", coords, org.rsmod.api.config.constants.lootdrop_duration, player, 1))
            .thenReturn(org.rsmod.game.obj.Obj.fromOwner(player, coords, "obj.dragon_warhammer", 1))
        for ((i, symbol) in def.npcs.withIndex()) {
            val npc = Npc(ServerCacheManager.getNpc(symbol.asRSCM())!!, CoordGrid(1451, 3696))
            `when`(registry.forNpc(npc, checker)).thenReturn(forced)
            hook.onKill(NpcDeathKillContext(player, npc, i))
        }
        val collected = player.invMap.getOrPut("inv.collection_transmit").objs.filterNotNull()
        assertEquals(5, collected.single { it.id == "obj.dragon_warhammer".asRSCM() }.count)
        assertEquals(5, mockingDetails(repository).invocations.count { it.method.name.startsWith("add") })
    }
    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
