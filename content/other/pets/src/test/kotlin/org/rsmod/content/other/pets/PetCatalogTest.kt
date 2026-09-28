package org.rsmod.content.other.pets

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.other.pets.cats.Cats
import org.rsmod.content.other.pets.dogs.Dogs

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class PetCatalogTest {
    @Test
    fun `cache contains all ordinary pet cat and dog forms with native pickup options`() {
        assertEquals(71, Pets.all.size)
        assertEquals(182, Pets.all.sumOf { it.forms.size })
        assertEquals(35, Cats.all.size)
        assertEquals(72, Dogs.all.size)
        val forms = Pets.all.flatMap { it.forms } + Cats.all.map { it.form } + Dogs.all.map { it.form }
        assertEquals(289, forms.map { it.objId }.toSet().size)
        for (form in forms) {
            assertNotNull(ServerCacheManager.getItem(form.objId), form.obj)
            val npc = checkNotNull(ServerCacheManager.getNpc(form.npcId))
            assertTrue(npc.isFollower, form.npc)
            assertTrue(npc.isInteractable, form.npc)
            assertNotNull(petOpIndex(form.npc, "Pick-up"), form.npc)
        }
    }

    @Test
    fun `every form resolves to its family without matching display names`() {
        for (pet in Pets.all) for (form in pet.forms) {
            assertSame(pet, Pets.forObj(form.objId)?.first)
            assertSame(form, Pets.forNpc(form.npcId)?.second)
        }
        for (cat in Cats.all) assertSame(cat, Cats.forObj(cat.form.objId))
        for (dog in Dogs.all) assertSame(dog, Dogs.forObj(dog.form.objId))
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() }
    }
}
