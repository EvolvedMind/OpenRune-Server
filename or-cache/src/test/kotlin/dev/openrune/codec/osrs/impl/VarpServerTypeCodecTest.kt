package dev.openrune.codec.osrs.impl

import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpServerType
import dev.openrune.types.varp.VarpTransmitLevel
import org.junit.jupiter.api.Assertions.assertEquals
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
        val decoded = VarpServerTypeCodec().loadData(petVarpId, encoded)

        assertEquals(VarpTransmitLevel.Never, decoded.transmit)
        assertEquals(VarpLifetime.Perm, decoded.scope)
    }
}
