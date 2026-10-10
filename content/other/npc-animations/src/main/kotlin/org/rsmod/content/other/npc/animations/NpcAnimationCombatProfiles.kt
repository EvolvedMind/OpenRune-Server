package org.rsmod.content.other.npc.animations

// Active forms and sequence names are native revision-240 mappings.
internal object NpcAnimationCombatProfiles {
    data class Profile(val active: String, val parts: List<NpcAnimationSpawnProfiles.Part>, val death: List<String>)
    val profiles = mapOf(
        "npc.wyrm_dark" to Profile("npc.wyrm_light", listOf(NpcAnimationSpawnProfiles.Part("npc.wyrm_light", "seq.wyrm_transition_up")), listOf("seq.wyrm_death")),
        "npc.superior_wyrm_dark" to Profile("npc.superior_wyrm_light", listOf(NpcAnimationSpawnProfiles.Part("npc.superior_wyrm_light", "seq.wyrm_transition_up")), listOf("seq.wyrm_death")),
        "npc.league_superior_wyrm_dark" to Profile("npc.league_superior_wyrm_light", listOf(NpcAnimationSpawnProfiles.Part("npc.league_superior_wyrm_light", "seq.wyrm_transition_up")), listOf("seq.wyrm_death")),
        "npc.varlamore_wyrm_dark01" to Profile("npc.varlamore_wyrm_light01", listOf(NpcAnimationSpawnProfiles.Part("npc.varlamore_wyrm_light01", "seq.wyrm_transition_up")), listOf("seq.wyrm_death")),
        "npc.wyrmscraig_wyrm01_walk" to Profile("npc.wyrmscraig_wyrm01", listOf(NpcAnimationSpawnProfiles.Part("npc.wyrmscraig_wyrm01", "seq.wyrm_transition_up")), listOf("seq.wyrm_death")),
        "npc.slayer_killerwatt_ball" to Profile("npc.slayer_killerwatt", listOf(NpcAnimationSpawnProfiles.Part("npc.slayer_killerwatt", "seq.killerwatt_biped_spawn")), listOf("seq.killerwatt_biped_death")),
        "npc.shadeshadow_level1" to Profile("npc.shade_level1", listOf(NpcAnimationSpawnProfiles.Part("npc.shadeshadow_level1", "seq.shadeshadow_sink"), NpcAnimationSpawnProfiles.Part("npc.shade_level1", "seq.shade_rise")), listOf("seq.shade_sink")),
        "npc.shadeshadow_level2" to Profile("npc.shade_level2", listOf(NpcAnimationSpawnProfiles.Part("npc.shadeshadow_level2", "seq.shadeshadow_sink"), NpcAnimationSpawnProfiles.Part("npc.shade_level2", "seq.shade_rise")), listOf("seq.shade_sink")),
        "npc.shadeshadow_level3" to Profile("npc.shade_level3", listOf(NpcAnimationSpawnProfiles.Part("npc.shadeshadow_level3", "seq.shadeshadow_sink"), NpcAnimationSpawnProfiles.Part("npc.shade_level3", "seq.shade_rise")), listOf("seq.shade_sink")),
        "npc.shadeshadow_level4" to Profile("npc.shade_level4", listOf(NpcAnimationSpawnProfiles.Part("npc.shadeshadow_level4", "seq.shadeshadow_sink"), NpcAnimationSpawnProfiles.Part("npc.shade_level4", "seq.shade_rise")), listOf("seq.shade_sink")),
        "npc.shadeshadow_level6" to Profile("npc.shade_level6", listOf(NpcAnimationSpawnProfiles.Part("npc.shadeshadow_level6", "seq.shadeshadow_sink"), NpcAnimationSpawnProfiles.Part("npc.shade_level6", "seq.shade_rise")), listOf("seq.shade_sink")),
    )
}
