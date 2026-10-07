plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
}

tasks.withType<Test>().configureEach {
    // Native action fixtures load spell/rune definitions alongside the revision-240 clue cache.
    maxHeapSize = "3072m"
    // Release each revision-240 cache graph before the next fixture class loads its copy.
    forkEvery = 1
    maxParallelForks = 1
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
    testImplementation(projects.content.skills.agility)
    testImplementation(projects.content.skills.hunter)
    testImplementation(projects.content.skills.fishing)
    testImplementation(projects.content.activities.skullball)
    testImplementation(projects.content.skills.magic.utilitySpells)
    testImplementation(projects.content.skills.smithing)
    testImplementation(projects.content.skills.firemaking)
    testImplementation(projects.content.activities.shadesOfMortton)
    testImplementation(projects.api.spells)
    testImplementation(projects.api.spellsRunes)
    testImplementation(projects.api.stats.xpmod)
    testImplementation(projects.api.registry)
    testImplementation("org.mockito:mockito-core:5.14.2")
}
