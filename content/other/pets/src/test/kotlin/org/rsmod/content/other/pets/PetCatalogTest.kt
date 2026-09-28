package org.rsmod.content.other.pets

import dev.openrune.definition.EntityOpsBuilder
import dev.openrune.types.ItemServerType
import dev.openrune.types.NpcServerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PetCatalogTest {
    @Test
    fun `canonical symbols distinguish pet variants with identical display names`() {
        val beaver = ItemServerType(id = 10, name = "Beaver")
        val oakBeaver = ItemServerType(id = 11, name = "Beaver")
        val baseNpc = follower(20, "Beaver")
        val oakNpc = follower(21, "Beaver")
        val unrelatedNpc = follower(22, "Beaver")
        val catalog = PetCatalog(
            items = mapOf("skillpetwc" to beaver, "skillpet_wc_oak" to oakBeaver),
            npcs = mapOf(
                "skillpetwc" to baseNpc,
                "skillpet_wc_oak" to oakNpc,
                "unrelated_beaver" to unrelatedNpc,
            ),
        )

        assertSame(baseNpc, catalog.find(beaver.id)?.npc)
        assertSame(oakNpc, catalog.find(oakBeaver.id)?.npc)
        assertEquals(2, catalog.types.size)
        assertTrue(catalog.unsupportedItems.isEmpty())
    }

    @Test
    fun `known pet items without usable cache npcs remain available for an explanatory handler`() {
        val missingNpc = ItemServerType(id = 10, name = "Beaver")
        val notFollower = ItemServerType(id = 11, name = "Heron")
        val noPickup = ItemServerType(id = 12, name = "Rocky")
        val catalog = PetCatalog(
            items = mapOf("skillpetwc" to missingNpc, "skillpetfish" to notFollower, "skillpetthieving" to noPickup),
            npcs = mapOf(
                "skillpet_fish" to follower(21, "Heron").copy(isFollower = false),
                "skillpet_thieving" to follower(22, "Rocky").copy(actions = EntityOpsBuilder().build()),
            ),
        )

        assertTrue(catalog.types.isEmpty())
        assertEquals(setOf(missingNpc.id, notFollower.id, noPickup.id), catalog.unsupportedItems.map { it.id }.toSet())
        assertNull(catalog.find(missingNpc.id))
    }

    @Test
    fun `absent optional pet items do not prevent the catalog from loading`() {
        val catalog = PetCatalog(emptyMap(), emptyMap())

        assertTrue(catalog.types.isEmpty())
        assertTrue(catalog.unsupportedItems.isEmpty())
    }

    private fun follower(id: Int, name: String): NpcServerType = NpcServerType(
        id = id,
        name = name,
        isFollower = true,
        isInteractable = true,
        actions = EntityOpsBuilder().op(4, "Pick-up").build(),
    )
}
