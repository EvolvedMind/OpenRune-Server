plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
}

dependencies {
    implementation(projects.api.bosses)
    testImplementation(projects.api.combat.combatFormulas)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.combat.combatCommons)
    implementation(projects.api.mechanics.toxins)
    testImplementation(projects.api.registry)
    testImplementation(libs.fastutil)
    testImplementation("org.mockito:mockito-core:5.14.2")
}
