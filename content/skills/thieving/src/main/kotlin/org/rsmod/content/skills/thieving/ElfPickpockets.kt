package org.rsmod.content.skills.thieving

/** Adapted elf data from OpenRune PR #230, head 8769932dab36733b60eb2b0653dd65dd481c130e.
 * https://github.com/OpenRune/OpenRune-Server/pull/230
 * Prifddinas special rolls: https://oldschool.runescape.wiki/w/Elf_(Thieving)
 * Explicit symbols verified against our revision-240 cache; existing targets are preserved.
 */
internal object ElfPickpockets {
    private val lletya = listOf(
        "npc.mourning_town_elf_1",
        "npc.mourning_town_elf_3",
        "npc.mourning_town_elf_4",
        "npc.mourning_town_elf_5_vis",
    )
    private val prifddinas = listOf(
        "npc.prif_citizen_miriel",
        "npc.prif_citizen_curufin",
        "npc.prif_citizen_enerdhil",
        "npc.prif_citizen_tatie",
        "npc.prif_citizen_finduilas",
        "npc.prif_citizen_gelmir",
        "npc.prif_citizen_mithrellas",
        "npc.prif_citizen_erestor",
        "npc.prif_citizen_lindir",
        "npc.prif_citizen_idril",
        "npc.prif_citizen_ingwion",
        "npc.prif_citizen_thingol",
        "npc.prif_citizen_elenwe",
        "npc.prif_citizen_orophin",
        "npc.prif_citizen_vaire",
        "npc.prif_citizen_elladan",
        "npc.prif_citizen_guilin",
        "npc.prif_citizen_ingwe",
        "npc.prif_citizen_cirdan",
        "npc.prif_citizen_glorfindel",
        "npc.prif_citizen_aredhel",
        "npc.prif_citizen_celegorm",
        "npc.prif_citizen_anaire",
        "npc.prif_citizen_maeglin",
        "npc.prif_citizen_edrahil",
        "npc.prif_citizen_fingon",
        "npc.prif_citizen_salgant",
        "npc.prif_citizen_celebrian",
        "npc.prif_citizen_imin",
        "npc.prif_citizen_oropher",
        "npc.prif_citizen_fingolfin",
        "npc.prif_citizen_mahtan",
        "npc.prif_citizen_indis",
        "npc.prif_citizen_iminye",
        "npc.prif_citizen_feanor",
        "npc.prif_citizen_saeros",
        "npc.prif_citizen_nellas",
        "npc.prif_citizen_enelye",
        "npc.prif_citizen_nerdanel",
        "npc.prif_citizen_nimloth",
        "npc.prif_citizen_findis",
        "npc.prif_citizen_earwen",
        "npc.prif_citizen_caranthir",
        "npc.prif_citizen_enel",
        "npc.prif_citizen_hendor",
        "npc.prif_citizen_galathil",
        "npc.prif_citizen_turgon",
        "npc.prif_citizen_lenwe",
        "npc.prif_citizen_aranwe",
    )
    private val loot = LootTable(listOf(
        105 to Loot("obj.coins", 280, 350),
        8 to Loot("obj.deathrune", 2),
        5 to Loot("obj.naturerune", 3),
        6 to Loot("obj.jug_wine"),
        2 to Loot("obj.fire_orb"),
        1 to Loot("obj.diamond"),
        1 to Loot("obj.gold_ore"),
    ))
    val rogueOutfit = mapOf(0 to "obj.roguesden_helm", 4 to "obj.roguesden_body",
        7 to "obj.roguesden_legs", 9 to "obj.roguesden_gloves", 10 to "obj.roguesden_boots")
    val targets = listOf(target(lletya, false), target(prifddinas, true))

    private fun target(npcs: List<String>, prifddinas: Boolean) = Pickpocket(
        npcs = npcs, level = 85, xp = 353.3, lowChance = 6, highChance = 100,
        stunDamage = 5, caughtShout = "What do you think you're doing?",
        loot = loot, pouch = CoinPouch("obj.pickpocket_coin_pouch_elf", 280, 350),
        elf = true, prifddinas = prifddinas, stunTicks = 11,
    )
}
