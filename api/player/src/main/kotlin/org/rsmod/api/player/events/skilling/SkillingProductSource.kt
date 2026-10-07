package org.rsmod.api.player.events.skilling

import dev.openrune.types.ItemServerType
import org.rsmod.api.table.mining.MiningRocksRow
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid

public sealed class SkillingProductSource {

    public data object AerialFishing : SkillingProductSource()

    public data class HunterCatch(public val npc: Int) : SkillingProductSource()

    public data object JewelleryEnchantment : SkillingProductSource()

    public data class ThievingChest(public val loc: String, public val coords: CoordGrid) : SkillingProductSource()

    public data object TabletMaking : SkillingProductSource()

    public data object SacredEel : SkillingProductSource()

    public data class ThievingStall(public val loc: String, public val coords: CoordGrid) : SkillingProductSource()

    public data object Herblore : SkillingProductSource()

    public data object Cooking : SkillingProductSource()

    public data object Smithing : SkillingProductSource()

    public data object Fletching : SkillingProductSource()

    public data object Crafting : SkillingProductSource()

    public data class Mining(
        public val rock: BoundLocInfo,
        public val rockData: MiningRocksRow
    ) : SkillingProductSource()

    public data class Woodcutting(
        public val tree: BoundLocInfo,
        public val productType: ItemServerType
    ) : SkillingProductSource()

    public data class Fishing(
        public val productType: ItemServerType,
    ) : SkillingProductSource()
}
