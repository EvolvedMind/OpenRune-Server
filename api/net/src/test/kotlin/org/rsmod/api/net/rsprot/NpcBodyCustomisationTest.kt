package org.rsmod.api.net.rsprot

import io.netty.buffer.Unpooled
import io.netty.buffer.UnpooledByteBufAllocator
import net.rsprot.buffer.extensions.toJagByteBuf
import net.rsprot.compression.provider.HuffmanCodecProvider
import net.rsprot.protocol.common.client.OldSchoolClientType
import net.rsprot.protocol.game.outgoing.codec.npcinfo.extendedinfo.NpcBodyCustomisationEncoder
import net.rsprot.protocol.game.outgoing.codec.npcinfo.extendedinfo.writer.NpcAvatarExtendedInfoDesktopWriter
import net.rsprot.protocol.game.outgoing.info.npcinfo.NpcAvatarExtendedInfo
import net.rsprot.protocol.game.outgoing.info.npcinfo.NpcAvatarExtendedInfoBlocks
import net.rsprot.protocol.internal.client.ClientTypeMap
import net.rsprot.protocol.internal.game.outgoing.info.encoder.PrecomputedExtendedInfoEncoder
import net.rsprot.protocol.internal.game.outgoing.info.npcinfo.extendedinfo.BodyCustomisation
import net.rsprot.protocol.internal.game.outgoing.info.npcinfo.extendedinfo.TypeCustomisation
import net.rsprot.protocol.internal.game.outgoing.info.precompute
import net.rsprot.protocol.internal.game.outgoing.info.shared.extendedinfo.util.SpotAnim
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class NpcBodyCustomisationTest {
    @Test
    fun `native variant and reset packets contain no body customisation block`() {
        val writer = NpcAvatarExtendedInfoDesktopWriter()
        val blocks = NpcAvatarExtendedInfoBlocks(listOf(writer))
        val huffman = mock(HuffmanCodecProvider::class.java)
        for (id in listOf(65524, 65523, 13599, 13600)) {
            blocks.transformation.id = id.toUShort()
            blocks.transformation.precompute(UnpooledByteBufAllocator.DEFAULT, huffman)
            val buffer = Unpooled.buffer().toJagByteBuf()
            try {
                writer.pExtendedInfo(buffer, 1, 2, NpcAvatarExtendedInfo.TRANSFORMATION, blocks)
                assertEquals(8, buffer.g1())
                assertEquals(id, buffer.g2Alt2())
                assertEquals(0, buffer.readableBytes())
            } finally { buffer.buffer.release() }
        }
        blocks.transformation.getBuffer(OldSchoolClientType.DESKTOP)?.release()
    }

    @Test
    fun `combined revision 240 graphics model and sequence blocks remain aligned`() {
        val writer = NpcAvatarExtendedInfoDesktopWriter()
        val blocks = NpcAvatarExtendedInfoBlocks(listOf(writer))
        val huffman = mock(HuffmanCodecProvider::class.java)
        for (model in listOf(55475, 55474, null)) {
            blocks.spotAnims.set(5, SpotAnim(2849, 0, 0, false))
            blocks.sequence.id = 11389.toUShort()
            blocks.sequence.delay = 0.toUShort()
            blocks.bodyCustomisation.customisation = model?.let { TypeCustomisation(listOf(it), emptyList(), emptyList(), false) }
            blocks.spotAnims.precompute(UnpooledByteBufAllocator.DEFAULT, huffman)
            blocks.bodyCustomisation.precompute(UnpooledByteBufAllocator.DEFAULT, huffman)
            blocks.sequence.precompute(UnpooledByteBufAllocator.DEFAULT, huffman)
            val buffer = Unpooled.buffer().toJagByteBuf()
            try {
                writer.pExtendedInfo(buffer, 1, 2,
                    NpcAvatarExtendedInfo.SPOTANIM or NpcAvatarExtendedInfo.BODY_CUSTOMISATION or NpcAvatarExtendedInfo.SEQUENCE, blocks)
                var flag = buffer.g1()
                if (flag and 0x20 != 0) flag = flag or (buffer.g1() shl 8)
                if (flag and 0x1000 != 0) flag = flag or (buffer.g1() shl 16)
                if (flag and 0x40000 != 0) flag = flag or (buffer.g1() shl 24)
                assertEquals(0x2041026, flag)
                assertEquals(1, buffer.g1Alt3())
                assertEquals(5, buffer.g1())
                assertEquals(2849, buffer.g2())
                assertEquals(0, buffer.g4Alt1())
                assertEquals(0, buffer.g1Alt1())
                assertEquals(if (model == null) 1 else 2, buffer.g1Alt3())
                if (model != null) {
                    assertEquals(1, buffer.g1())
                    assertEquals(model, buffer.g4())
                }
                assertEquals(11389, buffer.g2Alt1())
                assertEquals(0, buffer.g1Alt1())
                assertEquals(0, buffer.readableBytes())
            } finally { buffer.buffer.release() }
        }
        for (block in listOf(blocks.spotAnims, blocks.bodyCustomisation, blocks.sequence)) {
            block.getBuffer(OldSchoolClientType.DESKTOP)?.release()
        }
    }

    @Test
    fun `revision 240 model updates and reset consume exactly one body block`() {
        val encoder = NpcBodyCustomisationEncoder()
        for (models in listOf(listOf(55475), listOf(55474), listOf(50930, -1), emptyList())) {
            @Suppress("UNCHECKED_CAST")
            val encoders = mock(ClientTypeMap::class.java) as ClientTypeMap<PrecomputedExtendedInfoEncoder<BodyCustomisation>>
            val block = BodyCustomisation(encoders)
            if (models.isNotEmpty()) {
                block.customisation = TypeCustomisation(models, emptyList(), emptyList(), false)
            }
            val encoded = encoder.precompute(UnpooledByteBufAllocator.DEFAULT, mock(HuffmanCodecProvider::class.java), block)
            try {
                // Independent revision-240 client format: g1Alt3 flag, g1 count, g4 models.
                val flag = encoded.g1Alt3()
                if (models.isEmpty()) {
                    assertEquals(1, flag)
                } else {
                    assertEquals(2, flag)
                    assertEquals(models.size, encoded.g1())
                    assertEquals(models, List(models.size) { encoded.g4() })
                }
                assertEquals(0, encoded.readableBytes())
            } finally {
                encoded.buffer.release()
            }
        }
    }
}
