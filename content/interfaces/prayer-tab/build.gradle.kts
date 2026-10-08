plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
}

dependencies {
    implementation(projects.api.invtx)
    testImplementation(projects.api.invStorage)
    testImplementation(libs.or2.all.cache)
    testImplementation("org.mockito:mockito-core:5.14.2")
    implementation(libs.fastutil)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.scriptAdvanced)
}
