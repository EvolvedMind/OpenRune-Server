package org.rsmod.content.other.treasure.trails

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.or2.central.account.Rights
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@ResourceLock("ServerCacheManager")
@OptIn(org.rsmod.annotations.InternalApi::class)
class TrailTestCommandsTest {
    @Test fun `kit preserves owned equipment and all additions roll back if they do not fit`() {
        val f = Fixture()
        val weapon = InvObj("obj.abyssal_whip")
        f.player.inv[0] = weapon
        f.run("cluekit")
        // Inventory transactions copy values; preserving the item does not require object identity.
        assertEquals(weapon, f.player.inv[0])
        for (tool in TrailTestCommands.kit) assertEquals(1, f.player.inv.count(tool))
        assertTrue(f.player.worn.objs.all { it == null })
        for (slot in 10..27) f.player.inv[slot] = InvObj("obj.abyssal_whip")
        val before = f.player.inv.objs.toList()
        f.run("cluekit")
        assertEquals(before, f.player.inv.objs.toList())
        assertEquals(Rights.ADMINISTRATOR, f.commands.commands.getValue("cluekit").requiredRights)
        assertEquals(Rights.ADMINISTRATOR, f.commands.commands.getValue("cluetest").requiredRights)
    }

    @Test fun `bare actions supply beginner items instead of only showing help`() {
        val f = Fixture()
        f.run("cluetest", "box")
        f.run("cluetest", "casket")
        f.run("cluetest", "scroll")
        val tier = TrailTier.entries.first { it.key == "beginner" }
        assertEquals(1, f.player.inv.objs.filterNotNull().filter { it.id == tier.box }.sumOf { it.count })
        assertEquals(1, f.player.inv.objs.filterNotNull().filter { it.id == tier.casket }.sumOf { it.count })
        assertEquals(tier, f.progress.catalog.clues.getValue(f.states().single().row).tier)
    }

    @Test fun `all tiers provide matching boxes caskets and initialized scrolls`() {
        for (tier in TrailTier.entries) {
            val f = Fixture()
            f.run("cluetest", "box", tier.key, "2")
            f.run("cluetest", "casket", tier.key)
            assertEquals(2, f.player.inv.objs.filterNotNull().filter { it.id == tier.box }.sumOf { it.count })
            assertEquals(1, f.player.inv.objs.filterNotNull().filter { it.id == tier.casket }.sumOf { it.count })
            f.run("cluetest", "scroll", tier.key)
            val state = f.states().single()
            assertEquals(tier, f.progress.catalog.clues.getValue(state.row).tier)
            assertTrue(state.total in tier.steps)
            assertEquals(0, state.completed)
            assertEquals(0, state.phase)
            val before = f.player.inv.objs.toList()
            f.run("cluetest", "scroll", tier.key)
            f.run("cluetest", "info")
            assertEquals(before, f.player.inv.objs.toList())
        }
    }

    @Test fun `eel and gem fixtures start assigned tasks without changing stats or equipment`() {
        for ((alias, row) in listOf("eel" to TrailSkillChallenges.sacredEelTask, "gem" to TrailSkillChallenges.gemStallTask)) {
            val f = Fixture()
            val beforeCooking = f.player.statMap.getCurrentLevel("stat.cooking")
            f.run("cluetest", alias)
            assertEquals(TrailState(row, 1, phase = 9), f.states().single())
            assertEquals(beforeCooking, f.player.statMap.getCurrentLevel("stat.cooking"))
            assertTrue(f.player.worn.objs.all { it == null })
            if (alias == "eel") {
                assertEquals(3, f.player.inv.count("obj.snakeboss_eel"))
                assertEquals(1, f.player.inv.count("obj.knife"))
            }
            val before = f.player.inv.objs.toList()
            f.run("cluetest", alias)
            assertEquals(before, f.player.inv.objs.toList())
        }
    }

    @Test fun `fixture and supplies are one transaction and bad arguments do not mutate inventory`() {
        val f = Fixture()
        for (slot in 0..25) f.player.inv[slot] = InvObj("obj.abyssal_whip")
        val before = f.player.inv.objs.toList()
        f.run("cluetest", "eel")
        for (args in listOf(listOf("box", "master", "0"), listOf("casket", "easy", "29"), listOf("box", "bad"), listOf("task", "master", "999"), listOf("scroll", "hard", "bad"), listOf("task", "elite", "-1"))) f.run("cluetest", *args.toTypedArray())
        assertEquals(before, f.player.inv.objs.toList())
        assertTrue(f.states().isEmpty())
    }

    @Test fun `explicit elite task retains native scroll mapping and map selection stays a map`() {
        val f = Fixture()
        f.run("cluetest", "task", "elite", "9")
        assertEquals(TrailState("dbrow.cluehelper_skillchallenge_elite_9".asRSCM(), 1, phase = 9), f.states().single())
        val g = Fixture()
        g.run("cluetest", "scroll", "easy", "map")
        assertEquals("map", g.progress.catalog.clues.getValue(g.states().single().row).kind)
    }

    private class Fixture {
        val commands = CheatCommandMap()
        val progress = TrailProgress(TrailCatalog(), DefaultGameRandom(42))
        val player = Player(RecordingClient()).apply {
            modLevel = Rights.ADMINISTRATOR
            inv = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())), arrayOfNulls(28))
            worn = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.worn".asRSCM())), arrayOfNulls(14))
        }
        init {
            val scripts = ScriptContext(EventBus(), commands, EngineQueueCache())
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { scripts.startup() }
            with(TrailTestCommands(progress, DefaultGameRandom(42))) { scripts.startup() }
        }
        fun run(command: String, vararg args: String) { assertTrue(commands.execute(player, command, args.toList())) }
        fun states() = player.inv.objs.filterNotNull().mapNotNull { progress.state(it) }
    }

    private class RecordingClient : Client<Any, Any> {
        override fun write(message: Any) = Unit
        override fun close() = Unit
        override fun read(player: Player) = Unit
        override fun flush() = Unit
        override fun flushHighPriority() = Unit
        override fun unregister(service: Any, player: Player) = Unit
    }
    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
