package org.rsmod.api.market

import org.rsmod.module.ExtendedModule
import org.rsmod.server.services.Service

public object MarketModule : ExtendedModule() {
    override fun bind() {
        bindBaseAndImpl<MarketPrices>(OsrsMarketPrices::class.java)
        addSetBinding<Service>(OsrsMarketPriceService::class.java)
    }
}
