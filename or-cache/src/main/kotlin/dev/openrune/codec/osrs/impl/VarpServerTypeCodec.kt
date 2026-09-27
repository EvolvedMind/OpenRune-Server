package dev.openrune.codec.osrs.impl

import dev.openrune.definition.opcode.DefinitionOpcode
import dev.openrune.definition.opcode.OpcodeDefinitionCodec
import dev.openrune.definition.opcode.OpcodeList
import dev.openrune.definition.opcode.OpcodeType
import dev.openrune.definition.opcode.OpcodeType.BOOLEAN.enumType
import dev.openrune.definition.type.VarpType
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpServerType
import dev.openrune.types.varp.VarpTransmitLevel

class VarpServerTypeCodec(
    val types: Map<Int, VarpType>? = null,
    val custom: Map<Int, VarpServerType>? = emptyMap(),
) : OpcodeDefinitionCodec<VarpServerType>() {

    override val definitionCodec =
        OpcodeList<VarpServerType>().apply {
            add(DefinitionOpcode(1, OpcodeType.BOOLEAN, VarpServerType::bitProtect))
            add(DefinitionOpcode(2, OpcodeType.SHORT, VarpServerType::configType))
            add(DefinitionOpcode(3, enumType<VarpLifetime>(), VarpServerType::scope))
            add(DefinitionOpcode(4, enumType<VarpTransmitLevel>(), VarpServerType::transmit))
        }

    override fun VarpServerType.createData() {
        types?.get(id)?.let { configType = it.configType }

        custom?.get(id)?.let { overlay ->
            bitProtect = overlay.bitProtect
            configType = overlay.configType
            scope = overlay.scope
            transmit = overlay.transmit
        }
    }

    override fun createDefinition(): VarpServerType = VarpServerType()
}
