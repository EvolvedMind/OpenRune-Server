package dev.openrune.gamevals

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.toml.TomlFactory
import dev.openrune.rscm.RSCMType
import java.io.File
import java.io.InputStream

object PluginGamevalMerger {
    private val tomlMapper = ObjectMapper(TomlFactory()).findAndRegisterModules()

    private data class Declaration(val value: Int, val source: String)

    fun merge(rootDir: File) {
        val declarations = linkedMapOf<String, MutableMap<String, Declaration>>()
        sequenceOf(File(rootDir, "content"), File(rootDir, "api"))
            .filter { it.isDirectory }
            .flatMap { it.walk() }
            .filter { it.isFile && it.name == "gamevals.toml" && !it.isGeneratedGamevalPath() }
            .sortedBy { it.invariantSeparatorsPath }
            .forEach { file ->
                file.inputStream().use { collect(it, file.relativeTo(rootDir).path, declarations) }
            }
        writeMappings(File(rootDir, ".data/gamevals"), declarations)
    }

    fun mergeTomlStream(input: InputStream, source: String, gamevalsDir: File) {
        val declarations = linkedMapOf<String, MutableMap<String, Declaration>>()
        collect(input, source, declarations)
        writeMappings(gamevalsDir, declarations)
    }

    private fun collect(
        input: InputStream,
        source: String,
        declarations: MutableMap<String, MutableMap<String, Declaration>>,
    ) {
        val root: Map<String, Any?> =
            tomlMapper.readValue(input, object : TypeReference<Map<String, Any?>>() {})
        val gamevalsSection = root["gamevals"] as? Map<*, *> ?: return

        gamevalsSection.forEach { (tableNameAny, tableValuesAny) ->
            val tableName = tableNameAny as? String ?: return@forEach
            val tableValues = tableValuesAny as? Map<*, *> ?: return@forEach
            require(tableName in RSCMType.RSCM_PREFIXES) {
                "Invalid TOML table '$tableName' in $source. Expected one of: ${RSCMType.RSCM_PREFIXES}"
            }

            val entries = declarations.getOrPut(tableName) { linkedMapOf() }
            tableValues.forEach { (keyAny, valueAny) ->
                val key = keyAny.toString()
                val value = requireNotNull(valueAny.toString().toIntOrNull()) {
                    "Invalid gameval '$tableName.$key' in $source: '$valueAny'"
                }
                val previous = entries[key]
                require(previous == null || previous.value == value) {
                    "Conflicting gameval '$tableName.$key': ${previous?.value} in ${previous?.source}, $value in $source"
                }
                val other = entries.entries.find { it.key != key && it.value.value == value }
                require(value == -1 || other == null) {
                    "Conflicting gameval ID '$tableName.$value': '${other?.key}' in ${other?.value?.source}, '$key' in $source"
                }
                entries[key] = Declaration(value, source)
            }
        }
    }

    private fun writeMappings(
        gamevalsDir: File,
        declarations: Map<String, Map<String, Declaration>>,
    ) {
        val updates = declarations.mapNotNull { (table, entries) ->
            val file = File(gamevalsDir, "$table.rscm")
            val original = if (file.exists()) file.readText() else ""
            val separator = if ("\r\n" in original) "\r\n" else "\n"
            val lines = original.lineSequence().toMutableList()
            if (lines.lastOrNull() == "") lines.removeAt(lines.lastIndex)
            val values = entries.mapValues { (key, declaration) ->
                if (declaration.value != -1) declaration.value
                else {
                    val assigned = lines.asSequence()
                        .filter { mappingKey(it) == key }
                        .mapNotNull { it.substringAfter('=').trim().toIntOrNull() }
                        .filter { it != -1 }
                        .toSet()
                    require(assigned.size <= 1) {
                        "Conflicting assignments for '$table.$key' in ${file.path}: $assigned. Local mappings were not changed."
                    }
                    assigned.firstOrNull() ?: -1
                }
            }
            val seen = mutableSetOf<String>()
            val updated = mutableListOf<String>()

            lines.forEach { line ->
                val key = mappingKey(line)
                val value = values[key]
                if (value == null || key == null) {
                    updated += line
                } else if (seen.add(key)) {
                    val oldValue = line.substringAfter('=').trim().toIntOrNull()
                    updated += if (oldValue == value) line else "$key=$value"
                }
            }
            values.forEach { (key, value) ->
                if (seen.add(key)) updated += "$key=$value"
            }

            val ids = mutableMapOf<Int, String>()
            updated.forEach { line ->
                val key = mappingKey(line) ?: return@forEach
                val value = line.substringAfter('=').trim().toIntOrNull() ?: return@forEach
                if (value == -1) return@forEach
                val previous = ids.putIfAbsent(value, key)
                require(previous == null || previous == key) {
                    "Conflicting gameval ID '$table.$value' in ${file.path}: '$previous' and '$key'. Local mappings were not changed."
                }
            }

            val result = updated.joinToString(separator) +
                if (original.endsWith('\n') || updated.size > lines.size) separator else ""
            if (result == original) null else file to result
        }

        updates.forEach { (file, contents) ->
            file.parentFile.mkdirs()
            file.writeText(contents)
        }
    }

    private fun mappingKey(line: String): String? =
        line.trim().takeIf { it.isNotEmpty() && !it.startsWith('#') && '=' in it }
            ?.substringBefore('=')?.trim()
}

fun main(args: Array<String>) {
    val root = args.firstOrNull()?.let(::File) ?: File(System.getProperty("user.dir"))
    PluginGamevalMerger.merge(root)
}

private fun File.isGeneratedGamevalPath(): Boolean {
    val normalized = invariantSeparatorsPath
    return "/build/" in normalized || "/out/" in normalized || "/target/" in normalized
}
