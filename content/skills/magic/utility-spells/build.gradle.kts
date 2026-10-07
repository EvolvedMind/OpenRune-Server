plugins { id("base-conventions") }
dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.api.combat.combatManager)
    implementation(projects.api.spells)
    implementation(projects.api.spellsRunes)
    implementation(projects.content.skills.utils)
    implementation(projects.api.death)
    implementation(projects.api.repo)
    implementation(projects.api.npc)
    implementation(projects.api.stats.xpmod)
}
