package org.rsmod.content.other.commands

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.or2.central.account.Rights
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.Mockito.*
import org.rsmod.api.death.*
import org.rsmod.content.generic.killcount.KillcountNpcKillHook
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@ResourceLock("ServerCacheManager")
internal class ShamanTestLootCommandTest {
    @Test fun `all aliases execute native death hooks and shared permanent killcount`() {
        ServerCacheManager.init(240).close()
        val kills = mutableListOf<NpcDeathKillContext>()
        val hook = object : NpcDeathKillHook { override fun onKill(context: NpcDeathKillContext) { kills += context } }
        val ctor = AdminCommands::class.java.constructors.single()
        val deps = ctor.parameterTypes.map { if (it == Set::class.java) setOf(hook, KillcountNpcKillHook()) else mock(it) }.toTypedArray()
        val script = ctor.newInstance(*deps) as AdminCommands
        val commands = CheatCommandMap(); with(script) { ScriptContext(EventBus(), commands, EngineQueueCache()).startup() }
        val player = Player().apply { modLevel = Rights.ADMINISTRATOR }
        for (alias in listOf("shaman", "shamans", "lizardman_shaman", "lizardman_shamans")) commands.execute(player, "testloot", listOf(alias, "2"))
        assertEquals(8, kills.size); assertEquals(8, player.vars["varp.shaman_killcount"])
        assertTrue(kills.all { it.npc.type.id == "npc.zeah_lizardshaman_1".asRSCM() })
        for (count in listOf("0", "-1", "1001", "oops")) commands.execute(player, "testloot", listOf("shamans", count))
        commands.execute(player, "testloot", listOf("shamans", "1", "oops"))
        assertEquals(8, player.vars["varp.shaman_killcount"])
        commands.execute(player, "testloot", listOf("shamans")); assertEquals(108, player.vars["varp.shaman_killcount"])
        val saved = player.vars.backing["varp.shaman_killcount".asRSCM()]
        val reloaded = Player().apply { vars.backing["varp.shaman_killcount".asRSCM()] = saved }
        assertEquals(108, reloaded.vars["varp.shaman_killcount"])
    }
}
