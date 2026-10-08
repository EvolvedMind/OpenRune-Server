package org.rsmod.content.other.commands

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.or2.central.account.Rights
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.Mockito.mock
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@ResourceLock("ServerCacheManager")
class TormentedDemonTestLootCommandTest {
    @Test fun `demon loot aliases use native rewards and reject invalid counts`() {
        ServerCacheManager.init(240).close()
        val kills = mutableListOf<NpcDeathKillContext>()
        val hook = object : NpcDeathKillHook { override fun onKill(context: NpcDeathKillContext) { kills += context } }
        val ctor = AdminCommands::class.java.constructors.single()
        val deps = ctor.parameterTypes.map { if (it == Set::class.java) setOf(hook) else mock(it) }.toTypedArray()
        val commands = CheatCommandMap()
        with(ctor.newInstance(*deps) as AdminCommands) { ScriptContext(EventBus(), commands, EngineQueueCache()).startup() }
        val player = Player().apply { modLevel = Rights.ADMINISTRATOR }
        for (name in listOf("td", "tds", "tormented", "tormented_demon", "tormented_demons")) {
            commands.execute(player, "testloot", listOf(name, "2"))
        }
        assertEquals(10, kills.size)
        assertTrue(kills.all { it.npc.type.id == "npc.tormented_demon_1".asRSCM() })
        for (count in listOf("0", "-1", "1001", "oops")) commands.execute(player, "testloot", listOf("td", count))
        commands.execute(player, "testloot", listOf("td", "1", "bad"))
        assertEquals(10, kills.size)
    }
}
