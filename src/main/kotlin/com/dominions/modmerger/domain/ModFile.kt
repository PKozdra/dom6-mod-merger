package com.dominions.modmerger.domain

import com.dominions.modmerger.infrastructure.Logging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption

class ModFile private constructor(
    val file: File?,
    val name: String,
    private val contentProvider: () -> String
) : Logging {

    companion object {
        private const val HEADER_SIZE = 4096
        private const val BUFFER_SIZE = 8192

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
                contentProvider = { readFileEfficiently(file) }
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

        private fun readFileEfficiently(file: File): String {
            return FileChannel.open(file.toPath(), StandardOpenOption.READ).use { channel ->
                val buffer = ByteBuffer.allocate(BUFFER_SIZE)
                val builder = StringBuilder((file.length() + 1024).toInt())

                while (channel.read(buffer) != -1) {
                    buffer.flip()
                    builder.append(StandardCharsets.UTF_8.decode(buffer))
                    buffer.clear()
                }

                builder.toString()
            }
        }
    }

    // Cache content using lazy delegation
    private val lazyContent by lazy {
        try {
            debug("Loading content from: $name", useDispatcher = false)
            contentProvider()
        } catch (e: IOException) {
            throw InvalidModFileException("Failed to read content from file: $name", e)
        }
    }

    // Cache metadata using lazy delegation
    private val metadata by lazy { loadMetadata() }

    /**
     * Reads and returns the current content of the mod file.
     */
    val content: String get() = lazyContent

    /**
     * Loads metadata from file header or full content if needed
     */
    private fun loadMetadata(): ModMetadata {
        val headerContent = file?.let { loadFileHeader(it) } ?: content
        return parseMetadata(headerContent)
    }

    private fun loadFileHeader(file: File): String {
        return FileChannel.open(file.toPath(), StandardOpenOption.READ).use { channel ->
            val headerSize = HEADER_SIZE.coerceAtMost(file.length().toInt())
            val buffer = ByteBuffer.allocate(headerSize)
            channel.read(buffer)
            buffer.flip()
            StandardCharsets.UTF_8.decode(buffer).toString()
        }
    }

    private fun parseMetadata(content: String): ModMetadata {
        debug("Parsing metadata for mod: $name", useDispatcher = false)

        // Use sequence for better memory efficiency with large files
        val metadata = METADATA_PREFIXES.mapValues { (prefix, extractor) ->
            content.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.startsWith(prefix) }
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

    // Metadata properties - now use cached metadata
    val modName: String get() = metadata.modName ?: name
    val description: String get() = metadata.description ?: ""
    val version: String get() = metadata.version ?: ""
    val iconPath: String? get() = metadata.iconPath

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