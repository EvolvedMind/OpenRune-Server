package org.rsmod.content.skills.magic.utility

import dev.openrune.rscm.RSCM.asRSCM
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.spells.runes.MagicRunes
import org.rsmod.game.entity.Player

/** Rune pouch quantities change only after the same transaction commits both runes and output. */
internal fun produceSpell(
    player: Player, manager: MagicRuneManager, spell: MagicSpell,
    input: Int, slot: Int, output: Int,
): Boolean {
    if (!manager.canCastSpell(player, spell)) return false
    val validations = manager.validateSpell(player, spell)
    if (validations.any { it !is MagicRunes.Validation.Valid }) return false
    val sources = validations.filterIsInstance<MagicRunes.Validation.Valid.HasEnough>().flatMap { it.sources }
    val quantities = sources.filterIsInstance<MagicRunes.Source.VarBitSource>().groupBy { it.varbit }.mapValues { (_, parts) -> parts.sumOf { it.count } }
    if (quantities.any { (varbit, count) -> player.vars[varbit] < count }) return false
    val transaction = player.invTransaction(player.inv) {
        val target = select(player.inv)
        delete(target, input, 1, slot)
        for (source in sources.filterIsInstance<MagicRunes.Source.InvSource>()) {
            delete(target, source.obj.asRSCM(), source.count, source.slot)
        }
        add(target, output, 1, 0, slot.takeIf { player.inv[it]?.count == 1 })
    }
    if (!transaction.success) return false
    for ((varbit, count) in quantities) VarPlayerIntMapSetter.set(player, varbit, player.vars[varbit] - count)
    return true
}
