package dev.openrune.gamevals

import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir

class PluginGamevalMergerTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `migrates the installed Heron mapping before loading the new pet declarations`() {
        val enums = write(".data/gamevals/enum.rscm", "heron_pet_chance=65473\nlocal_enum=65590\n")
        write(
            "content/other/pets/src/main/resources/gamevals.toml",
            "[gamevals.enum]\nheron_pet_chance = 65404\nbeaver_chance = 65406\n",
        )
        GameValDat.write(root.resolve(".data/gamevals-binary/gamevals.dat").toFile(), emptyMap())

        val previousFailure = assertThrows<IllegalArgumentException> { loadProvider() }
        assertTrue(previousFailure.cause?.message.orEmpty().contains("heron_pet_chance"))

        PluginGamevalMerger.merge(root.toFile())

        val provider = loadProvider()
        assertEquals(65404, provider.mappings["enum"]?.get("enum.heron_pet_chance"))
        assertEquals(65406, provider.mappings["enum"]?.get("enum.beaver_chance"))
        assertEquals(65590, provider.mappings["enum"]?.get("enum.local_enum"))
        assertEquals(listOf("heron_pet_chance=65404"), enums.readLines().filter { it.startsWith("heron_pet_chance=") })
    }

    @Test
    fun `repairs both old and new duplicate rows of a declared symbol`() {
        val enums = write(
            ".data/gamevals/enum.rscm",
            "heron_pet_chance=65473\nlocal_enum=65590\nheron_pet_chance=65404\nheron_pet_chance=65473\n",
        )
        write("content/pets/gamevals.toml", "[gamevals.enum]\nheron_pet_chance = 65404\n")

        PluginGamevalMerger.merge(root.toFile())

        assertEquals(listOf("heron_pet_chance=65404"), enums.readLines().filter { it.startsWith("heron_pet_chance=") })
        assertTrue("local_enum=65590" in enums.readLines())
    }

    @Test
    fun `preserves custom mappings comments and Windows line endings`() {
        val enums = write(
            ".data/gamevals/enum.rscm",
            "# Local configuration\r\n\r\nlocal_enum = 65590\r\nheron_pet_chance=65473\r\n",
        )
        val untouched = write(".data/gamevals/obj.rscm", "local_item=65591")
        write(
            "content/pets/gamevals.toml",
            "[gamevals.enum]\nheron_pet_chance = 65404\nbeaver_chance = 65406\n",
        )
        val untouchedBytes = untouched.readBytes()

        PluginGamevalMerger.merge(root.toFile())

        val result = enums.readText()
        assertTrue(result.contains("# Local configuration\r\n\r\nlocal_enum = 65590\r\n"))
        assertTrue(result.contains("heron_pet_chance=65404\r\n"))
        assertTrue(result.contains("beaver_chance=65406\r\n"))
        assertFalse(result.replace("\r\n", "").contains('\n'))
        assertArrayEquals(untouchedBytes, untouched.readBytes())
    }

    @Test
    fun `repeated merges leave reconciled files unchanged`() {
        val enums = write(".data/gamevals/enum.rscm", "heron_pet_chance=65473")
        write(
            "content/pets/gamevals.toml",
            "[gamevals.enum]\nheron_pet_chance = 65404\nbeaver_chance = 65406\n",
        )

        PluginGamevalMerger.merge(root.toFile())
        val first = enums.readBytes()
        PluginGamevalMerger.merge(root.toFile())

        assertArrayEquals(first, enums.readBytes())
        assertTrue(enums.readLines().containsAll(listOf("heron_pet_chance=65404", "beaver_chance=65406")))
    }

    @Test
    fun `conflicting source keys fail before any existing file is changed`() {
        val enums = write(".data/gamevals/enum.rscm", "heron_pet_chance=65473\n")
        write(
            "content/a/gamevals.toml",
            "[gamevals.enum]\nheron_pet_chance = 65404\n[gamevals.dbtable]\nnew_table = 65501\n",
        )
        write("content/z/gamevals.toml", "[gamevals.enum]\nheron_pet_chance = 65405\n")
        val original = enums.readBytes()

        assertThrows<IllegalArgumentException> { PluginGamevalMerger.merge(root.toFile()) }

        assertArrayEquals(original, enums.readBytes())
        assertFalse(root.resolve(".data/gamevals/dbtable.rscm").exists())
    }

    @Test
    fun `conflicting source ids fail before any existing file is changed`() {
        val enums = write(".data/gamevals/enum.rscm", "heron_pet_chance=65473\n")
        write(
            "content/a/gamevals.toml",
            "[gamevals.enum]\nheron_pet_chance = 65404\n[gamevals.dbtable]\nnew_table = 65501\n",
        )
        write("content/z/gamevals.toml", "[gamevals.enum]\nother_pet_chance = 65404\n")
        val original = enums.readBytes()

        assertThrows<IllegalArgumentException> { PluginGamevalMerger.merge(root.toFile()) }

        assertArrayEquals(original, enums.readBytes())
        assertFalse(root.resolve(".data/gamevals/dbtable.rscm").exists())
    }

    @Test
    fun `ignores stale declarations in generated output directories`() {
        write("content/pets/src/main/resources/gamevals.toml", "[gamevals.enum]\nheron_pet_chance = 65404\n")
        for (directory in listOf("build", "out", "target")) {
            write(
                "content/pets/$directory/resources/main/gamevals.toml",
                "[gamevals.enum]\nheron_pet_chance = 65473\ngenerated_only = 65590\n",
            )
        }

        PluginGamevalMerger.merge(root.toFile())

        assertEquals(listOf("heron_pet_chance=65404"), root.resolve(".data/gamevals/enum.rscm").readLines())
    }

    @Test
    fun `repeated unassigned ids do not conflict`() {
        write("content/a/gamevals.toml", "[gamevals.enum]\npending_first = -1\n")
        write("content/b/gamevals.toml", "[gamevals.enum]\npending_second = -1\n")

        PluginGamevalMerger.merge(root.toFile())

        assertEquals(
            setOf("pending_first=-1", "pending_second=-1"),
            root.resolve(".data/gamevals/enum.rscm").readLines().toSet(),
        )
    }

    @Test
    fun `an unassigned declaration preserves its previously allocated local id`() {
        val enums = write(".data/gamevals/enum.rscm", "pending_pet=65473\n")
        write("content/pets/gamevals.toml", "[gamevals.enum]\npending_pet = -1\n")
        val original = enums.readBytes()

        PluginGamevalMerger.merge(root.toFile())

        assertArrayEquals(original, enums.readBytes())
        assertEquals(65473, loadProvider().mappings["enum"]?.get("enum.pending_pet"))
    }

    @Test
    fun `a duplicate placeholder row cannot discard an allocated local id`() {
        val enums = write(".data/gamevals/enum.rscm", "pending_pet=-1\npending_pet=65473\n")
        write("content/pets/gamevals.toml", "[gamevals.enum]\npending_pet = -1\n")

        PluginGamevalMerger.merge(root.toFile())

        assertEquals(listOf("pending_pet=65473"), enums.readLines())
        assertEquals(65473, loadProvider().mappings["enum"]?.get("enum.pending_pet"))
    }

    @Test
    fun `an unassigned declaration cannot silently choose between conflicting local allocations`() {
        val enums = write(".data/gamevals/enum.rscm", "pending_pet=65473\npending_pet=65474\n")
        write("content/pets/gamevals.toml", "[gamevals.enum]\npending_pet = -1\n")
        val original = enums.readBytes()

        assertThrows<IllegalArgumentException> { PluginGamevalMerger.merge(root.toFile()) }

        assertArrayEquals(original, enums.readBytes())
    }

    @Test
    fun `an unmanaged local id collision fails before changing any table`() {
        val enums = write(".data/gamevals/enum.rscm", "heron_pet_chance=65473\nlocal_enum=65404\n")
        val objects = write(".data/gamevals/obj.rscm", "local_item=65590\n")
        write(
            "content/pets/gamevals.toml",
            "[gamevals.obj]\nnew_item = 65591\n[gamevals.enum]\nheron_pet_chance = 65404\n",
        )
        val originalEnums = enums.readBytes()
        val originalObjects = objects.readBytes()

        assertThrows<IllegalArgumentException> { PluginGamevalMerger.merge(root.toFile()) }

        assertArrayEquals(originalEnums, enums.readBytes())
        assertArrayEquals(originalObjects, objects.readBytes())
    }

    @Test
    fun `reconciles API declarations as well as content declarations`() {
        val areas = write(".data/gamevals/area.rscm", "custom_instance=65473\n")
        write("api/instances/src/main/resources/gamevals.toml", "[gamevals.area]\ncustom_instance = 65404\n")

        PluginGamevalMerger.merge(root.toFile())

        assertEquals(listOf("custom_instance=65404"), areas.readLines())
    }

    private fun write(relative: String, contents: String): Path =
        root.resolve(relative).also {
            it.parent.createDirectories()
            it.writeText(contents)
        }

    private fun loadProvider(): GameValProvider =
        GameValProvider.loadIsolated(root.toAbsolutePath().toString() + File.separator)
}
