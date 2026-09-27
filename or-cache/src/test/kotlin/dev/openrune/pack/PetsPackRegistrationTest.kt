package dev.openrune.pack

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PetsPackRegistrationTest {
    @Test
    fun `pet definitions are discovered by the cache builder`() {
        val projectRoot = File("..").canonicalFile
        val petsConfig =
            projectRoot.resolve("content/other/pets/pack/src/main/resources/pack/configs/pets.toml")

        assertTrue(petsConfig.isFile, "Pet definitions must exist in the pets pack.")

        val discoveredConfigs =
            PluginPacks.discover(projectRoot).configDirectories().map { it.resolve("pets.toml").canonicalFile }

        assertTrue(
            petsConfig.canonicalFile in discoveredConfigs,
            "The pets pack must be registered so pets.toml is included in the server cache.",
        )
    }
}
