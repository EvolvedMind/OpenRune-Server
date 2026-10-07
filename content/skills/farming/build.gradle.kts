plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
}

dependencies {
    testImplementation(projects.api.invStorage)
    testImplementation("org.mockito:mockito-core:5.14.2")
    implementation(projects.api.player)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.registry)
}
