plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
}

dependencies {
    testImplementation(libs.or2.all.cache)
    testImplementation(libs.fastutil)
    testImplementation(projects.api.invStorage)
    implementation(projects.api.invtx)
    testImplementation("org.mockito:mockito-core:5.14.2")
    implementation(projects.api.combat.combatManager)
    implementation(projects.api.player)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.script)
    implementation(projects.api.spells)
    implementation(projects.api.spellsRunes)
    implementation(projects.content.quest)
}
