plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.content.quest)
    implementation(projects.content.interfaces.collectionLog)
    testImplementation(projects.api.registry)
    testImplementation(libs.rsprot.api)
    testImplementation("org.mockito:mockito-core:5.14.2")
}
