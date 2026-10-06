package org.rsmod.api.market

import dev.openrune.ServerCacheManager
import java.time.Instant
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class OsrsMarketPricesTest {
    private val now = Instant.now().epochSecond
    private fun quote(high: String, low: String, time: Long = now) =
        """{"data":{"4151":{"high":$high,"low":$low,"highTime":$time,"lowTime":$time}}}"""

    @Test fun `latest high and low are averaged and one sided prices remain usable`() {
        assertEquals(150L, OsrsMarketPrices.decode(quote("200", "100"), now)[4151])
        assertEquals(200L, OsrsMarketPrices.decode(quote("200", "null"), now)[4151])
        assertEquals(100L, OsrsMarketPrices.decode(quote("null", "100"), now)[4151])
    }
    @Test fun `invalid stale and null trades cannot pollute the price cache`() {
        assertTrue(OsrsMarketPrices.decode(quote("-1", "null"), now).isEmpty())
        assertTrue(OsrsMarketPrices.decode(quote("100", "100", now - 31 * 86400), now).isEmpty())
        assertTrue(OsrsMarketPrices.decode(quote("100", "100", now + 3600), now).isEmpty())
        assertTrue(OsrsMarketPrices.decode(quote("1.5", "null"), now).isEmpty())
    }
    @Test fun `OSRS v2 full prices are preserved while legacy int fields saturate safely`() {
        assertEquals(Long.MAX_VALUE, OsrsMarketPrices.decode(quote("99999999999999999999", "8000000000000000000"), now)[4151])
        val prices = OsrsMarketPrices()
        prices.refresh({ quote("5000000000", "5000000000") }, now)
        assertEquals(5_000_000_000L, prices.price(ServerCacheManager.getItem(4151)!!))
        assertEquals(Int.MAX_VALUE, prices[ServerCacheManager.getItem(4151)!!])
    }
    @Test fun `failed HTTP or malformed refresh preserves the last good snapshot`() {
        val prices = OsrsMarketPrices()
        prices.refresh({ quote("200", "100") }, now)
        assertThrows(java.io.IOException::class.java) { prices.refresh({ throw java.io.IOException("HTTP 429") }, now) }
        assertThrows(Exception::class.java) { prices.refresh({ "not JSON" }, now) }
        assertThrows(IllegalArgumentException::class.java) { prices.refresh({ "{\"data\":{}}" }, now) }
        assertEquals(150L, prices.cachedPrice(4151))
    }
    @Test fun `lookup resolves notes and defaults unknown items without performing HTTP`() {
        val prices = OsrsMarketPrices()
        val whip = ServerCacheManager.getItem(4151)!!
        val note = ServerCacheManager.getItem(whip.certlink)!!
        assertEquals(DefaultMarketPrices()[whip], prices[whip])
        prices.refresh({ quote("200", "100") }, now)
        assertEquals(150, prices[whip]); assertEquals(150, prices[note])
        assertEquals(1, prices[ServerCacheManager.getItem(995)!!])
        val other = ServerCacheManager.getItem(1735)!!
        assertEquals(DefaultMarketPrices()[other], prices[other])
        // Cache expires if refreshes fail for a full day; HA and LA stay definition-owned.
        prices.refresh({ quote("200", "100", now - 90000) }, now - 90000)
        assertEquals(DefaultMarketPrices()[whip], prices[whip])
        assertEquals(72000, whip.highAlch)
    }
    @Test fun `GE only lookup normalizes notes but never substitutes fallback or stale prices`() {
        val prices = OsrsMarketPrices()
        val whip = ServerCacheManager.getItem(4151)!!
        val note = ServerCacheManager.getItem(whip.certlink)!!
        assertNull(prices.gePrice(whip))
        assertNotNull(prices.price(whip))
        prices.refresh({ quote("1000000", "1000000") }, now)
        assertEquals(1_000_000L, prices.gePrice(whip))
        assertEquals(1_000_000L, prices.gePrice(note))
        assertNull(prices.gePrice(ServerCacheManager.getItem(1735)!!))
        assertNull(prices.gePrice(ServerCacheManager.getItem(995)!!))
        prices.refresh({ quote("1000000", "1000000", now - 90000) }, now - 90000)
        assertNull(prices.gePrice(whip))
        assertNotNull(prices.price(whip))
    }
    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(240).close() }
    }
}
