package org.rsmod.api.player.output

import dev.openrune.types.ItemServerType
import java.text.NumberFormat
import java.util.Locale
import java.math.BigInteger
import org.rsmod.api.config.refs.params

internal object ItemExamine {
    // Rev-240 mod_icons frame 15 is the native blue information icon.
    fun description(type: ItemServerType, examine: String, count: Int): String {
        val stack = if (count > 1) " (x${NumberFormat.getIntegerInstance(Locale.US).format(count)})" else ""
        return "<img=15> ${type.name}$stack - $examine"
    }

    fun values(type: ItemServerType, count: Int, marketPrice: Long): String {
        val quantity = count.coerceAtLeast(1).toLong()
        val format = NumberFormat.getIntegerInstance(Locale.US)
        fun value(unit: Long) = format.format(BigInteger.valueOf(unit.coerceAtLeast(0)) * BigInteger.valueOf(quantity)) + " gp"
        val alchable = type.param(params.no_alchemy) == 0
        val high = if (alchable) value(type.highAlch.toLong()) else "N/A"
        val low = if (alchable) value(type.lowAlch.toLong()) else "N/A"
        return "<col=008000>GE: ${value(marketPrice)}</col> - " +
            "<col=0000ff>High Alch: $high</col> - <col=ff0000>Low Alch: $low</col>"
    }
}
