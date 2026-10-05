plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
}
dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.content.skills.utils)
    testImplementation(projects.api.invStorage)
}
