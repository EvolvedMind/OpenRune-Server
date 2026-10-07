package org.rsmod.content.skills.farming

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock

@ResourceLock("ServerCacheManager")
class FarmingCacheTest {
    @Test fun `crop identities survive table ordering and patch vars are permanent`() {
        assertEquals(27, FarmingCrops.all.size)
        assertEquals(20, FarmingPatches.all.size)
        assertEquals(7, FarmingCrops.index(FarmingCrops.bySeed("obj.watermelon_seed".asRSCM())!!))
        assertEquals(27, FarmingCrops.index(FarmingCrops.bySeed("obj.snape_grass_seed".asRSCM())!!))
        for (crop in FarmingCrops.all) assertEquals(crop, FarmingCrops.byIndex(FarmingCrops.index(crop)))
        for (patch in FarmingPatches.all) assertEquals("Perm", ServerCacheManager.getVarp(patch.varp.asRSCM())!!.scope.toString())
        assertEquals("Perm", ServerCacheManager.getVarp("varp.farming_clock".asRSCM())!!.scope.toString())
    }

    @Test fun `every reachable crop stage resolves to native revision 240 patch art`() {
        for (patch in FarmingPatches.all) {
            val type = ServerCacheManager.getObject(patch.loc.asRSCM())!!
            assertTrue(type.multiVarBit > 0)
            assertNotNull(ServerCacheManager.getVarbit(type.multiVarBit))
            for (crop in FarmingCrops.all.filter { it.kind == patch.kind }) {
                val states = buildList {
                    for (stage in 0..crop.stages) add(PatchState(stage = stage))
                    if (crop.kind != PatchKind.HERB) for (stage in 0 until crop.stages) add(PatchState(stage = stage, watered = true))
                    for (stage in 1 until crop.stages) {
                        add(PatchState(stage = stage, health = Health.DISEASED))
                        add(PatchState(stage = stage, health = Health.DEAD))
                    }
                }
                for (state in states) {
                    val index = state.transmit(crop)
                    assertTrue(index in type.multiLoc.indices, "${patch.loc} ${crop.name} ${state.health} stage=${state.stage} index=$index")
                    val visual = type.multiLoc[index]
                    assertTrue(visual >= 0, "Missing art: ${patch.loc} ${crop.name} index=$index")
                    assertNotNull(ServerCacheManager.getObject(visual))
                }
            }
        }
    }

    @Test fun `tools and animations exist in the accepted cache`() {
        for (obj in listOf(RAKE, DIBBER, SPADE, PLANT_CURE, WEEDS, "obj.bucket_empty") + WATERING_CANS + Compost.entries.filter { it != Compost.NONE }.map { it.obj }) {
            assertNotNull(ServerCacheManager.getItem(obj.asRSCM()), obj)
        }
        for (seq in listOf(ANIM_RAKE, ANIM_PLANT, ANIM_WATER, ANIM_HARVEST, ANIM_CURE, ANIM_COMPOST)) {
            assertNotNull(ServerCacheManager.getAnim(seq.asRSCM()), seq)
        }
    }
    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
