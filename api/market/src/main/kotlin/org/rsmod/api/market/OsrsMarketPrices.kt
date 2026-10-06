package org.rsmod.api.market

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.math.BigInteger
import java.net.URI
import java.net.HttpURLConnection
import java.time.Instant
import dev.openrune.types.ItemServerType
import org.rsmod.game.type.uncert

/** In-memory market estimates. Lookups never perform I/O or read mutable game state off-thread. */
@Singleton
public class OsrsMarketPrices @Inject constructor() : MarketPrices {
    private val fallback: MarketPrices = DefaultMarketPrices()
    private data class Snapshot(val prices: Map<Int, Long>, val refreshedAt: Long)
    @Volatile private var snapshot: Snapshot = Snapshot(emptyMap(), 0)

    override fun get(type: ItemServerType): Int = price(type).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    override fun price(type: ItemServerType): Long {
        val base = uncert(type)
        // Custom/untradeable variants keep their cache value; coins always represent one gp.
        if (base.id == 995) return 1
        val current = snapshot
        val fresh = Instant.now().epochSecond - current.refreshedAt < MAX_CACHE_AGE_SECONDS
        return if (fresh && base.stockmarket) current.prices[base.id] ?: fallback.price(base)!! else fallback.price(base)!!
    }

    /** Called by the background service only; failed refreshes leave the last good snapshot intact. */
    internal fun refresh(fetch: () -> String = ::fetchLatest, now: Long = Instant.now().epochSecond) {
        val prices = decode(fetch(), now)
        require(prices.isNotEmpty()) { "OSRS price response contained no usable prices" }
        snapshot = Snapshot(prices, now)
    }

    internal fun cachedPrice(id: Int): Long? = snapshot.prices[id]

    internal companion object {
        const val ENDPOINT = "https://prices.runescape.wiki/api/v2/osrs/latest"
        const val USER_AGENT = "OpenRune-Fork-market-prices/1.0 (+https://github.com/EvolvedMind/OpenRune-Server)"
        const val MAX_CACHE_AGE_SECONDS = 24 * 60 * 60L
        private const val MAX_TRADE_AGE_SECONDS = 30 * 24 * 60 * 60L
        private const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        private val mapper = ObjectMapper()

        fun decode(json: String, now: Long): Map<Int, Long> {
            val data = mapper.readTree(json).get("data")
            require(data != null && data.isObject) { "Missing OSRS price data object" }
            return buildMap {
                for ((key, row) in data.fields()) {
                    val id = key.toIntOrNull()?.takeIf { it > 0 } ?: continue
                    val values = listOf("high", "low").mapNotNull { side ->
                        val price = row[side]?.takeIf { it.isIntegralNumber }?.bigIntegerValue() ?: return@mapNotNull null
                        val time = row[side + "Time"]?.takeIf { it.canConvertToLong() }?.longValue() ?: return@mapNotNull null
                        if (price.signum() <= 0 || now - time !in -300..MAX_TRADE_AGE_SECONDS) null else price
                    }
                    if (values.isEmpty()) continue
                    val mean = values.fold(BigInteger.ZERO, BigInteger::add).divide(BigInteger.valueOf(values.size.toLong()))
                    put(id, mean.min(BigInteger.valueOf(Long.MAX_VALUE)).toLong())
                }
            }
        }

        fun fetchLatest(): String {
            val connection = URI(ENDPOINT).toURL().openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.setRequestProperty("Accept", "application/json")
                require(connection.responseCode == 200) { "OSRS price HTTP ${connection.responseCode}" }
                return connection.inputStream.use {
                    val bytes = it.readNBytes(MAX_RESPONSE_BYTES + 1)
                    require(bytes.size <= MAX_RESPONSE_BYTES) { "OSRS price response too large" }
                    bytes.toString(Charsets.UTF_8)
                }
            } finally {
                connection.disconnect()
            }
        }
    }
}
