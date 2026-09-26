package org.rsmod.api.net.central.embed

import dev.or2.central.account.BadWordIndex
import dev.or2.central.config.CentralConfig
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.server.config.OpenRuneCentralGameConfig
import org.rsmod.api.server.config.ServerConfig

class EmbeddedCentralRuntimeConfigTest {
    @Test
    fun `local development avoids remote bad words fetch during first login`() {
        val runtime = runtimeConfig(offlineBadWords = true)

        assertTrue(runtime.badWords.remoteUrl.isBlank())
        assertTimeoutPreemptively(Duration.ofSeconds(5)) { BadWordIndex(runtime).roots() }
    }

    @Test
    fun `normal embedded central retains remote bad words source`() {
        val runtime = runtimeConfig(offlineBadWords = false)

        assertFalse(runtime.badWords.remoteUrl.isBlank())
    }

    private fun runtimeConfig(offlineBadWords: Boolean): CentralConfig {
        val central = OpenRuneCentralGameConfig(sameInstance = true)
        val server =
            ServerConfig(
                name = "OpenRune",
                gamePort = 43594,
                revision = 240,
                environment = "LIVE",
                world = 255,
                central = central,
            )
        return embeddedCentralRuntimeConfig(
            server,
            central,
            "jdbc:postgresql://127.0.0.1:5432/test",
            "openrune",
            "test",
            10,
            offlineBadWords,
        )
    }
}
