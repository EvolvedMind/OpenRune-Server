import java.util.zip.ZipFile

plugins {
    java
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    implementation(project(":shared"))
}

tasks.jar {
    dependsOn(configurations.runtimeClasspath)
    archiveFileName.set("probe-plugin.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from({
        configurations.runtimeClasspath.get().mapNotNull { dependency ->
            when {
                dependency.isDirectory -> dependency
                dependency.isFile && dependency.extension == "jar" -> zipTree(dependency)
                else -> null
            }
        }
    })
}

tasks.register("verifyFatJar") {
    dependsOn(tasks.jar)
    doLast {
        val archive = tasks.jar.get().archiveFile.get().asFile
        ZipFile(archive).use { zip ->
            require(zip.getEntry("probe/shared/StudioProtocol.class") != null) {
                "StudioProtocol.class missing from fat jar"
            }
            require(zip.getEntry("probe/shared/StudioVersion.class") != null) {
                "StudioVersion.class missing from fat jar"
            }
        }
    }
}
