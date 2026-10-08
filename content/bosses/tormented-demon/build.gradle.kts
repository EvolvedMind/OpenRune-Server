plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
}

dependencies {
    testImplementation(libs.or2.all.cache)
    testImplementation(libs.fastutil)
    testImplementation(projects.api.combat.combatFormulas)
    testImplementation(projects.api.registry)
    testImplementation(projects.api.invStorage)
    testImplementation(projects.content.drops)
    testImplementation(projects.content.other.consumables)
    testImplementation(projects.content.interfaces.collectionLog)
    testImplementation(projects.api.market)
    testImplementation(projects.api.dropTable)
    testImplementation(projects.api.dropTablePlugin)
    testImplementation("org.mockito:mockito-core:5.14.2")
    implementation(projects.api.bosses)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.combat.combatCommons)
    implementation(projects.api.combat.combatWeapon)
    implementation(projects.api.combat.combatManager)
    implementation(projects.api.route)
    implementation(projects.api.weapons)
    implementation(projects.content.quest)
}
