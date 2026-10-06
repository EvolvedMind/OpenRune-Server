package org.rsmod.api.market

import dev.openrune.types.ItemServerType

public interface MarketPrices {
    public operator fun get(type: ItemServerType): Int?

    /** Full value for text/stack totals. Legacy int-only client fields may still use [get]. */
    public fun price(type: ItemServerType): Long? = get(type)?.toLong()
}
