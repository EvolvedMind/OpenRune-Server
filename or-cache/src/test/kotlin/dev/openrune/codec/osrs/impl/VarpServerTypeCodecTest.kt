package dev.openrune.codec.osrs.impl

import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpServerType
import dev.openrune.types.varp.VarpTransmitLevel
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VarpServerTypeCodecTest {
    @Test
    fun `server-only pet varp does not transmit when absent from the base cache`() {
        val petVarpId = 65658
        val overlay =
            VarpServerType(
                id = petVarpId,
                scope = VarpLifetime.Perm,
                transmit = VarpTransmitLevel.Never,
            )
        val encoded =
            VarpServerTypeCodec(types = emptyMap(), custom = mapOf(petVarpId to overlay))
                .encodeToBuffer(VarpServerType(petVarpId))

        assertTrue(
            encoded.asList().windowed(2).contains(listOf(4.toByte(), VarpTransmitLevel.Never.id.toByte())),
            "The packed varp must explicitly contain transmit=Never (opcode 4) before login.",
        )
    }
}
