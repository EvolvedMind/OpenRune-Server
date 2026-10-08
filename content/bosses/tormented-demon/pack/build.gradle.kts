plugins { id("base-conventions") }

dependencies {
    implementation(libs.or2.all.cache)
    implementation(libs.or2.tools)
    implementation(libs.or2.definition)
    implementation(libs.netty.buffer)
}
