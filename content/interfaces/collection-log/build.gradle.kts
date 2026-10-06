plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
}

dependencies {
    implementation(projects.api.market)
    implementation(projects.api.pluginCommons)
    implementation(projects.engine.utilsBits)
    testImplementation(libs.rsprot.api)
    testImplementation(projects.api.invStorage)
}
