// src/main/kotlin/com/dominions/modmerger/domain/ModFile.kt
package com.dominions.modmerger.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import mu.KotlinLogging
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlin.properties.Delegates

/**
 * Represents a Dominions 6 mod file with validation and lazy-loaded content.
 */
class ModFile(
    val file: File? = null,
    val name: String,
    private val contentProvider: () -> String
) {
    private val logger = KotlinLogging.logger {}

    private var metadata: ModMetadata? by Delegates.observable(null) { _, _, newValue ->
        logger.debug { "Metadata loaded for mod: $name" }
    }

    // Only load the header portion of the file (first few KB) to extract metadata
    private fun loadMetadata(): ModMetadata {
        if (metadata != null) return metadata!!

        val headerContent = file?.let { loadFileHeader(it, 4096) } ?: contentProvider()
        return parseMetadata(headerContent).also { metadata = it }
    }

    private fun loadFileHeader(file: File, maxBytes: Int): String {
        RandomAccessFile(file, "r").use { raf ->
            val bytes = ByteArray(maxBytes.coerceAtMost(file.length().toInt()))
            raf.read(bytes)
            return String(bytes)
        }
    }

    /**
     * The content of the mod file as a string.
     */
    val content: String by lazy {
        try {
            logger.debug { "Loading content from: $name" }
            contentProvider()
        } catch (e: IOException) {
            throw InvalidModFileException("Failed to read content from file: $name", e)
        }
    }

    private fun parseMetadata(content: String): ModMetadata {
        val lines = content.lines()
        val modNameLine = lines.firstOrNull { it.trim().startsWith("#modname") }
        val descriptionLine = lines.firstOrNull { it.trim().startsWith("#description") }
        val versionLine = lines.firstOrNull { it.trim().startsWith("#version") }
        val iconPathLine = lines.firstOrNull { it.trim().startsWith("#icon") }

        logger.debug { "Parsing metadata for mod: $name" }
        logger.debug { "Found modName line: $modNameLine" }
        logger.debug { "Found description line: $descriptionLine" }
        logger.debug { "Found version line: $versionLine" }
        logger.debug { "Found iconPath line: $iconPathLine" }

        return ModMetadata(
            modName = modNameLine?.substringAfter("\"")?.removeSuffix("\""),
            description = descriptionLine?.substringAfter("\"")?.removeSuffix("\""),
            version = versionLine?.substringAfter("\"")?.removeSuffix("\""),
            iconPath = iconPathLine?.substringAfter("\"")?.removeSuffix("\"")
        )
    }

    // Provide metadata properties without loading full content
    val modName: String get() = loadMetadata().modName ?: name
    val description: String get() = loadMetadata().description ?: ""
    val version: String get() = loadMetadata().version ?: ""
    val iconPath: String? get() = loadMetadata().iconPath

    init {
        file?.let { validateFile(it) }
    }

    private fun validateFile(file: File) {
        require(file.exists()) { "File does not exist: ${file.path}" }
        require(file.isFile) { "Not a valid file: ${file.path}" }
        require(file.canRead()) { "File is not readable: ${file.path}" }
        require(file.extension == "dm") { "File must have .dm extension: ${file.path}" }
    }

    companion object {
        /**
         * Factory method to create a ModFile instance from a physical file.
         */
        fun fromFile(file: File): ModFile = ModFile(
            file = file,
            name = file.nameWithoutExtension,
            contentProvider = { file.readText() }
        )

        /**
         * Factory method to create a ModFile instance from a string content (useful for testing).
         */
        fun fromContent(name: String, content: String): ModFile = ModFile(
            name = name,
            contentProvider = { content }
        )

        // Load mods in chunks for large directories
        fun loadModsFromDirectory(directory: File, chunkSize: Int = 10): Flow<List<ModFile>> = flow {
            directory.walkTopDown()
                .filter { it.isFile && it.extension == "dm" }
                .chunked(chunkSize)
                .forEach { chunk ->
                    emit(chunk.map { fromFile(it) })
                }
        }
    }
}

class InvalidModFileException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
