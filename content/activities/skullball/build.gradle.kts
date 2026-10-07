plugins { id("base-conventions") }
dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.api.repo)
    implementation(projects.api.death)
    implementation(projects.api.npc)
    implementation(projects.api.stats.xpmod)
}
