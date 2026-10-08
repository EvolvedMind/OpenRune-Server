package org.rsmod.api.combat.formulas.accuracy

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

internal class UnlockedPrayerDefenceTest {
    @Test fun `Deadeye defence requires upgraded Eagle Eye rather than Hawk Eye`() {
        ServerCacheManager.init(240).close()
        val player = Player()
        VarPlayerIntMapSetter.set(player, "varbit.prayer_deadeye_unlocked", 1)
        VarPlayerIntMapSetter.set(player, "varbit.prayer_hawkeye", 1)
        assertEquals(1.0, AccuracyOperations.defensivePrayerBonus(player.vars))
        VarPlayerIntMapSetter.set(player, "varbit.prayer_hawkeye", 0)
        VarPlayerIntMapSetter.set(player, "varbit.prayer_eagleeye", 1)
        assertEquals(1.05, AccuracyOperations.defensivePrayerBonus(player.vars))
        VarPlayerIntMapSetter.set(player, "varbit.prayer_deadeye_unlocked", 0)
        assertEquals(1.0, AccuracyOperations.defensivePrayerBonus(player.vars))
    }
}
