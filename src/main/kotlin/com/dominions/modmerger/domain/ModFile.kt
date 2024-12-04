// src/main/kotlin/com/dominions/modmerger/domain/ModFile.kt
package com.dominions.modmerger.domain

import com.dominions.modmerger.infrastructure.Logging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

class ModFile private constructor(
    val file: File?,
    val name: String,
    private val contentProvider: () -> String
) : Logging {

    companion object {
        private const val HEADER_SIZE = 4096
        private val METADATA_PREFIXES = mapOf(
            "#modname" to { content: String -> content.substringAfter("\"").removeSuffix("\"") },
            "#description" to { content: String -> content.substringAfter("\"").removeSuffix("\"") },
            "#version" to { content: String -> content.substringAfter("\"").removeSuffix("\"") },
            "#icon" to { content: String -> content.substringAfter("\"").removeSuffix("\"") }
        )

        fun fromFile(file: File): ModFile {
            validateFile(file)
            return ModFile(
                file = file,
                name = file.nameWithoutExtension,
                contentProvider = { file.readText() }
            )
        }

        fun fromContent(name: String, content: String): ModFile = ModFile(
            file = null,
            name = name,
            contentProvider = { content }
        )

        fun loadModsFromDirectory(directory: File, chunkSize: Int = 10): Flow<List<ModFile>> = flow {
            directory.walkTopDown()
                .filter { it.isFile && it.extension.equals("dm", ignoreCase = true) }
                .chunked(chunkSize)
                .forEach { chunk ->
                    emit(chunk.map { fromFile(it) })
                }
        }

        private fun validateFile(file: File) {
            require(file.exists()) { "File does not exist: ${file.path}" }
            require(file.isFile) { "Not a valid file: ${file.path}" }
            require(file.canRead()) { "File is not readable: ${file.path}" }
            require(file.extension.equals("dm", ignoreCase = true)) {
                "File must have .dm extension: ${file.path}"
            }
        }
    }

    /**
     * Reads and returns the current content of the mod file.
     */
    val content: String
        get() = try {
            debug("Loading content from: $name", useDispatcher = false)
            contentProvider()
        } catch (e: IOException) {
            throw InvalidModFileException("Failed to read content from file: $name", e)
        }

    /**
     * Loads metadata from file header or full content if needed
     */
    private fun loadMetadata(): ModMetadata {
        val headerContent = file?.let { loadFileHeader(it) } ?: content
        return parseMetadata(headerContent)
    }

    private fun loadFileHeader(file: File): String {
        return RandomAccessFile(file, "r").use { raf ->
            val bytes = ByteArray(HEADER_SIZE.coerceAtMost(file.length().toInt()))
            raf.read(bytes)
            String(bytes)
        }
    }

    private fun parseMetadata(content: String): ModMetadata {
        debug("Parsing metadata for mod: $name", useDispatcher = false)

        val lines = content.lines()
        val metadata = METADATA_PREFIXES.mapValues { (prefix, extractor) ->
            lines.firstOrNull { it.trim().startsWith(prefix) }
                ?.let { extractor(it) }
                ?.also { debug("Found $prefix: $it", useDispatcher = false) }
        }

        return ModMetadata(
            modName = metadata["#modname"],
            description = metadata["#description"],
            version = metadata["#version"],
            iconPath = metadata["#icon"]
        )
    }

    // Metadata properties - always load fresh
    val modName: String get() = loadMetadata().modName ?: name
    val description: String get() = loadMetadata().description ?: ""
    val version: String get() = loadMetadata().version ?: ""
    val iconPath: String? get() = loadMetadata().iconPath

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ModFile) return false
        return file?.absolutePath == other.file?.absolutePath && name == other.name
    }

    override fun hashCode(): Int {
        var result = file?.absolutePath.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }

    override fun toString(): String =
        "ModFile(name=$name, path=${file?.absolutePath ?: "memory"})"
}

class InvalidModFileException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
