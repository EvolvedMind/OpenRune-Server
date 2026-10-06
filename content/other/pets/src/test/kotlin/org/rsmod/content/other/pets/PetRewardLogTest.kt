package org.rsmod.content.other.pets

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.parallel.ResourceLock
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.rsmod.api.random.GameRandom
import org.rsmod.content.interfaces.collectionlog.CollectionLog
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player

@ResourceLock("ServerCacheManager")
class PetRewardLogTest {
    @Test fun `standalone pet obtains log once with source and table delivery suppresses duplicate logging`() {
        val player = Player(RecordingClient()).apply { inv = invMap.getOrPut("inv.inv") }
        val followers = mock(PetFollowers::class.java)
        val insurance = mock(PetInsurance::class.java)
        val log = mock(CollectionLog::class.java)
        val rewards = PetRewards(followers, insurance, mock(GameRandom::class.java), log)
        val pet = Pets.all.first { it.mainDrop }
        assertTrue(rewards.give(player, pet.base.obj, source = "Boss"))
        verify(log).grant(player, pet.base.objId, 1, "Boss")
        rewards.give(player, pet.base.obj, logReward = false)
        verifyNoMoreInteractions(log)
        verify(followers, times(2)).spawn(player, pet.base)
    }
    @Test fun `pet reclaim and rejected nonpet item do not announce new rewards`() {
        val player = Player(RecordingClient()).apply { inv = invMap.getOrPut("inv.inv") }
        val followers = mock(PetFollowers::class.java)
        val log = mock(CollectionLog::class.java)
        val rewards = PetRewards(followers, mock(PetInsurance::class.java), mock(GameRandom::class.java), log)
        assertFalse(rewards.give(player, "obj.coins"))
        assertTrue(rewards.reclaim(player, Pets.all.first()))
        verifyNoInteractions(log)
    }
    private class RecordingClient : Client<Any, Any> {
        override fun write(message: Any) = Unit
        override fun close() = Unit
        override fun read(player: Player) = Unit
        override fun flush() = Unit
        override fun flushHighPriority() = Unit
        override fun unregister(service: Any, player: Player) = Unit
    }
    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() }
    }
}
