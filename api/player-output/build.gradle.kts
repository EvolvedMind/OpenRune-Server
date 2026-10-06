plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(libs.rsprot.api)
    implementation(projects.api.config)
    implementation(projects.api.attr)
    implementation(projects.engine.game)
    implementation(projects.engine.map)
}
