package org.rsmod.content.other.treasure.trails

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

@ResourceLock("ServerCacheManager")
class TrailGodOutfitsTest {
    @Test fun `Guthix and Zamorak clues require three worn items rather than every listed slot`() {
        val catalog = TrailCatalog()
        val requirements = TrailRequirements(catalog)
        for (name in listOf("juna", "mage_of_zamorak")) {
            val outfitRow = "dbrow.cluehelper_outfit_cryptic_master_$name".asRSCM()
            val clue = catalog.clues.values.first { outfitRow in it.fields.ints("outfit") }
            val fields = catalog.fields(outfitRow)
            val player = Player().apply {
                inv = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())), arrayOfNulls(28))
                worn = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.worn".asRSCM())), arrayOfNulls(14))
            }
            val pieces = listOf("hat" to 0, "back" to 1, "torso" to 4).map { (field, slot) -> slot to InvObj(checkNotNull(ServerCacheManager.getItem(fields.ints("wearpos_$field").first()))) }
            for ((index, piece) in pieces.withIndex()) player.inv[index] = piece.second
            assertNotNull(requirements.missing(player, clue))
            for ((index, piece) in pieces.withIndex()) {
                player.worn[piece.first] = piece.second
                if (index < 2) assertNotNull(requirements.missing(player, clue))
            }
            assertNull(requirements.missing(player, clue))
            player.worn[4] = InvObj("obj.leather_armour")
            assertNotNull(requirements.missing(player, clue))
        }
    }
    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() } }
}
