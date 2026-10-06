package org.rsmod.api.player.output

import dev.openrune.types.ItemServerType
import java.text.NumberFormat
import java.util.Locale
import java.math.BigInteger
import org.rsmod.api.config.refs.params

internal object ItemExamine {
    fun values(type: ItemServerType, count: Int, marketPrice: Long): String {
        val quantity = count.coerceAtLeast(1).toLong()
        val format = NumberFormat.getIntegerInstance(Locale.US)
        fun value(unit: Long) = format.format(BigInteger.valueOf(unit.coerceAtLeast(0)) * BigInteger.valueOf(quantity)) + " gp"
        val alchable = type.param(params.no_alchemy) == 0
        val high = if (alchable) value(type.highAlch.toLong()) else "N/A"
        val low = if (alchable) value(type.lowAlch.toLong()) else "N/A"
        val prefix = if (quantity > 1) "Value of ${format.format(quantity)} x ${type.name}: " else "Value: "
        return prefix + "GE: ${value(marketPrice)} | High Alch: $high | Low Alch: $low"
    }
}
