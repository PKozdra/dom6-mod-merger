package com.dominions.modmerger.domain

import java.io.File
import java.io.IOException

/**
 * Represents a Dominions 6 mod file with validation and lazy-loaded content.
 */
data class ModFile(
    val file: File? = null,
    val name: String,
    private val contentProvider: () -> String
) {
    private val logger = mu.KotlinLogging.logger {}

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
    }
}

class InvalidModFileException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)