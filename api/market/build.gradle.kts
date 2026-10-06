plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(libs.guice)
    implementation(libs.jackson.databind)
    implementation(libs.kotlin.inline.logger)
    implementation(projects.server.services)
    implementation(projects.engine.game)
    implementation(projects.engine.module)
}
