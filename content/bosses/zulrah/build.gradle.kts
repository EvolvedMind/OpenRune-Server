plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
}

dependencies {
    implementation(projects.api.bosses)
    implementation(projects.api.combat.combatFormulas)
    implementation(projects.api.bossHpBarPlugin)
    implementation(projects.api.instances)
    implementation(projects.api.pluginCommons)
    testImplementation(projects.api.invStorage)
    testImplementation(projects.api.registry)
}
