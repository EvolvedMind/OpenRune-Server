package org.rsmod.api.market

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import org.rsmod.server.services.Service

/** One bulk request per five minutes, independent of players and the game loop. */
public class OsrsMarketPriceService @Inject constructor(private val prices: OsrsMarketPrices) : Service {
    private val logger = InlineLogger()
    private var executor: ScheduledExecutorService? = null

    override suspend fun startup() {
        check(executor == null)
        executor = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "osrs-market-prices").apply { isDaemon = true }
        }.also { worker ->
            worker.scheduleWithFixedDelay({
                try {
                    prices.refresh()
                    logger.info { "OSRS market price cache refreshed." }
                } catch (e: Exception) {
                    logger.warn { "OSRS market price refresh failed; retaining cached/fallback values: ${e.message}" }
                }
            }, 0, 5, TimeUnit.MINUTES)
        }
    }

    override suspend fun shutdown() {
        executor?.shutdownNow()
        executor = null
    }
}
