plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
}
dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.api.random)
    implementation(projects.api.repo)
    implementation(projects.api.death)
    implementation(projects.api.gameProcess)
    implementation(projects.api.music)
    implementation(projects.content.quest)
    implementation(projects.content.interfaces.collectionLog)
    implementation(projects.content.interfaces.emotes)
    testImplementation(projects.api.invStorage)
    testImplementation(projects.content.skills.thieving)
    testImplementation(projects.content.skills.runecrafting)
    testImplementation(projects.content.skills.farming)
    testImplementation(projects.api.stats.xpmod)
    testImplementation(projects.api.registry)
    testImplementation("org.mockito:mockito-core:5.14.2")
}
