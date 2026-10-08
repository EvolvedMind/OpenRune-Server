plugins {
    id("base-conventions")
    id("game-cache-test-conventions")

}

dependencies {
    testImplementation(libs.or2.all.cache)
    testImplementation(libs.fastutil)
    testImplementation(projects.api.registry)
    testImplementation("org.mockito:mockito-core:5.14.2")
    implementation(projects.api.bosses)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.combat.combatCommons)
    implementation(projects.api.combat.combatFormulas)
    implementation(projects.api.npc)
    implementation(projects.api.player)
    implementation(projects.api.random)
    implementation(projects.api.script)
    implementation(projects.engine.game)
    implementation(projects.engine.map)
    implementation(projects.engine.plugin)
    implementation(projects.engine.coroutine)
}
