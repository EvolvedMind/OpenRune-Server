package org.rsmod.content.bosses.tormenteddemon

import java.util.EnumSet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.formulas.attributes.CombatNpcAttributes
import org.rsmod.api.combat.formulas.attributes.CombatSpellAttributes
import org.rsmod.api.combat.formulas.maxhit.magic.MagicMaxHitOperations

class TormentedDemonFormulaTest {
    @Test fun `slow spell flat bonus raises both endpoints while ordinary spell range stays intact`() {
        val empty = EnumSet.noneOf(CombatSpellAttributes::class.java)
        val td = EnumSet.of(CombatNpcAttributes.TormentedDemonUnshielded)
        assertEquals(33..83, MagicMaxHitOperations.modifySpellDamageRange(50, 50, 7, 0, empty, td))
        td += CombatNpcAttributes.TormentedDemonOverheadMagic
        assertEquals(11..61, MagicMaxHitOperations.modifySpellDamageRange(50, 50, 7, 0, empty, td))
        assertEquals(0..50, MagicMaxHitOperations.modifySpellDamageRange(50, 50, 7, 0, empty, EnumSet.noneOf(CombatNpcAttributes::class.java)))
    }
}
