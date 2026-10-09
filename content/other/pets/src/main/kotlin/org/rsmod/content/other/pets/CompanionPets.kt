package org.rsmod.content.other.pets

import dev.openrune.rscm.RSCM.asRSCM

/** Native quest/holiday companions are separate from the boss pet reward and insurance registry. */
internal object CompanionPets {
    data class Entry(val name: String, val obj: String, val npc: String? = null) {
        val objId: Int by lazy { obj.asRSCM() }
        val form: PetForm? by lazy { npc?.let { PetForm(objId, it.asRSCM(), runs = false) } }
    }
    val archibald = listOf(
        "obj.easter26_egg_companion", "obj.easter26_egg_companion02",
        "obj.easter26_egg_companion03", "obj.easter26_egg_companion04",
        "obj.easter26_egg_companion05", "obj.easter26_egg_companion06",
        "obj.easter26_egg_companion07",
    ).map { Entry("Archibald", it) }
    val fish = listOf(
        Entry("Pet fish (blue)", "obj.fishbowl_bluefish"),
        Entry("Pet fish (green)", "obj.fishbowl_greenfish"),
        Entry("Pet fish (spinefish)", "obj.fishbowl_spinefish"),
    )
    val broav = Entry("Broav", "obj.wgs_broav", "npc.wgs_broav")
    val egg = Entry("Humphrey Dumphrey", "obj.scrambled_egg")
    val mayor = Entry("Mayor of Catherby", "obj.current_affairs_mayor_of_catherby")
    val rock = Entry("Pet rock", "obj.vt_useless_rock")
    val chair = Entry("Spooky chair", "obj.hw25_chair_obj_reward", "npc.poh_hw_chair")
    val toyCat = Entry("Toy cat", "obj.poh_toy_cat", "npc.poh_toy_cat")
    val all = archibald + fish + listOf(broav, egg, mayor, rock, chair, toyCat)
    private val byObj by lazy { all.associateBy { it.objId } }
    fun formForObj(id: Int): PetForm? = byObj[id]?.form
}
