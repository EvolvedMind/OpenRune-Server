package org.rsmod.content.other.pets

import dev.openrune.definition.EntityOpsBuilder
import dev.openrune.types.ItemServerType
import dev.openrune.types.NpcServerType
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class PetMatchingTest {
    @Test
    fun `matches one follower with a pickup option ignoring case and outer whitespace`() {
        val pet = follower(11, "hErOn")
        val unrelated = follower(12, "Rock golem")

        assertSame(pet, uniqueFollowerFor(item("  Heron  "), listOf(unrelated, pet)))
    }

    @Test
    fun `refuses an ambiguous item to npc mapping`() {
        val first = follower(11, "Heron")
        val second = follower(12, " heron ")

        assertNull(uniqueFollowerFor(item("Heron"), listOf(first, second)))
    }

    @Test
    fun `invalid namesakes do not make a valid follower ambiguous`() {
        val pet = follower(11, "Heron")
        val ordinaryNpc = follower(12, "Heron").copy(isFollower = false)
        val unclaimableNpc = follower(13, "Heron").copy(actions = EntityOpsBuilder().build())

        assertSame(pet, uniqueFollowerFor(item("Heron"), listOf(ordinaryNpc, pet, unclaimableNpc)))
    }

    @Test
    fun `ignores matching npcs that are not followers or cannot be picked up`() {
        val ordinaryNpc = follower(11, "Heron").copy(isFollower = false)
        val unclaimableNpc = follower(12, "Heron").copy(actions = EntityOpsBuilder().build())
        val otherPet = follower(13, "Phoenix")

        assertNull(uniqueFollowerFor(item("Heron"), listOf(ordinaryNpc, unclaimableNpc, otherPet)))
        assertNull(uniqueFollowerFor(item("Heron"), emptyList()))
    }

    private fun item(name: String): ItemServerType = ItemServerType(id = 10, name = name)

    private fun follower(id: Int, name: String): NpcServerType =
        NpcServerType(
            id = id,
            name = name,
            isFollower = true,
            actions = EntityOpsBuilder().op(1, "pick-UP").build(),
        )
}
