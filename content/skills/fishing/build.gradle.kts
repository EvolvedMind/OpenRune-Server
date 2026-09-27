plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.api.attr)
    implementation(projects.content.other.pets)
    implementation(projects.content.quest)
    implementation(projects.content.skills.utils)
}
