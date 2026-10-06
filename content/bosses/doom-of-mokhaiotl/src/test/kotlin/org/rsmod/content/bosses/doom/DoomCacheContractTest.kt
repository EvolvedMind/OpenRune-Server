package org.rsmod.content.bosses.doom

import dev.openrune.ServerCacheManager
import dev.openrune.filesystem.Cache
import dev.openrune.rscm.RSCM.asRSCM
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.Mockito.*
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.instances.InstanceManager
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

@ResourceLock("ServerCacheManager")
class DoomCacheContractTest {
    @Test fun `every configured animation npc loc and game variable resolves in revision 240`() {
        val dir = Paths.get("content/bosses/doom-of-mokhaiotl/src/main/kotlin")
        val pattern = Regex("\"((?:npc|seq|spotanim|loc|varp|varbit|varn|headbar|synth)\\.[a-z0-9_]+)\"")
        val symbols = Files.walk(dir).use { files -> files.filter { it.toString().endsWith(".kt") }
            .flatMap { pattern.findAll(Files.readString(it)).map { match -> match.groupValues[1] }.toList().stream() }
            .toList().toSet() }
        assertTrue(symbols.size > 100)
        val live = Cache.load(Paths.get(".data/cache/LIVE"))
        try {
        for (symbol in symbols) {
            val id = symbol.asRSCM()
            val value: Any? = when (symbol.substringBefore('.')) {
                "npc" -> ServerCacheManager.getNpc(id)
                "seq" -> ServerCacheManager.getAnim(id)
                "spotanim" -> live.data(2, 13, id)
                "loc" -> ServerCacheManager.getObject(id)
                "varp" -> ServerCacheManager.getVarp(id)
                "varbit" -> ServerCacheManager.getVarbit(id)
                "varn" -> ServerCacheManager.getVarn(id)
                "headbar" -> ServerCacheManager.getHealthBar(id)
                "synth" -> live.data(4, id)
                else -> id
            }
            assertNotNull(value, symbol)
        }
        for (id in listOf(7930, 7932, 63240)) assertNotNull(live.data(12, id), "Doom loot clientscript $id")
        } finally { live.close() }
        val boss = checkNotNull(ServerCacheManager.getNpc(DoomNpcs.BOSS.asRSCM()))
        assertEquals(300, boss.attack)
        assertEquals(90, boss.defence)
        assertEquals(275, boss.magic)
    }
    @Test fun `public template boss cannot start an uninitialized encounter and other bosses pass`() {
        val hook = DoomAccessHook(mock(InstanceManager::class.java))
        val player = Player()
        val doom = Npc(checkNotNull(ServerCacheManager.getNpc(DoomNpcs.BOSS.asRSCM())), DoomArena.BOSS_SPAWN)
        assertTrue(hook.validate(player, doom) is NpcAttackValidateResult.Deny)
        val corp = Npc(checkNotNull(ServerCacheManager.getNpc("npc.corp_beast".asRSCM())), CoordGrid(0, 0, 0))
        assertEquals(NpcAttackValidateResult.Pass, hook.validate(player, corp))
    }
    @Test fun `only the boss bound to the players own Doom session is allowed`() {
        val instances = mock(InstanceManager::class.java)
        val player = Player()
        val npc = Npc(checkNotNull(ServerCacheManager.getNpc(DoomNpcs.BOSS.asRSCM())), DoomArena.BOSS_SPAWN)
        val own = mock(org.rsmod.api.instances.InstanceSession::class.java)
        val other = mock(org.rsmod.api.instances.InstanceSession::class.java)
        val id = org.rsmod.api.instances.InstanceId(10)
        `when`(own.key).thenReturn("doom_of_mokhaiotl")
        `when`(instances.sessionForPlayer(player)).thenReturn(own)
        `when`(instances.instanceForNpc(npc)).thenReturn(id)
        `when`(instances.sessionForId(id)).thenReturn(other)
        val hook = DoomAccessHook(instances)
        assertTrue(hook.validate(player, npc) is NpcAttackValidateResult.Deny)
        `when`(instances.sessionForId(id)).thenReturn(own)
        assertEquals(NpcAttackValidateResult.Pass, hook.validate(player, npc))
    }
    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
