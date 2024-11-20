// src/main/kotlin/com/dominions/modmerger/infrastructure/FileSystem.kt
package com.dominions.modmerger.infrastructure

import com.dominions.modmerger.domain.ModFile
import mu.KotlinLogging
import java.io.File
import java.io.IOException
import java.nio.charset.MalformedInputException
import java.nio.file.Path

class FileSystem {
    private val logger = KotlinLogging.logger {}

    companion object {
        private const val MOD_EXTENSION = "dm"
        private const val MERGED_MOD_NAME = "modmerger"
    }

    /**
     * Finds all mod files in the current directory, excluding the merged output file.
     * @return List of ModFile objects representing valid mod files
     */
    fun findModFiles(): List<ModFile> {
        logger.debug { "Searching for .dm files in current directory" }

        val currentDir = Path.of(".").toAbsolutePath()
        logger.debug { "Current directory: $currentDir" }

        return try {
            File(".")
                .listFiles { file ->
                    val isModFile = file.extension == MOD_EXTENSION
                    val isNotMergedOutput = file.nameWithoutExtension != MERGED_MOD_NAME
                    isModFile && isNotMergedOutput
                }
                ?.mapNotNull { file ->
                    try {
                        logger.debug { "Found potential mod file: ${file.name}" }
                        ModFile.fromFile(file)
                    } catch (e: Exception) {
                        when (e) {
                            is MalformedInputException -> {
                                logger.warn(e) { "Invalid character encoding in file: ${file.name}" }
                                null
                            }
                            is IllegalArgumentException -> {
                                logger.warn(e) { "Invalid mod file: ${file.name}" }
                                null
                            }
                            else -> {
                                logger.error(e) { "Unexpected error reading file: ${file.name}" }
                                null
                            }
                        }
                    }
                }
                ?.toList()
                ?: emptyList()
        } catch (e: SecurityException) {
            logger.error(e) { "Security error accessing directory: $currentDir" }
            emptyList()
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error scanning directory: $currentDir" }
            emptyList()
        }.also {
            logger.info { "Found ${it.size} valid mod files" }
            if (logger.isDebugEnabled) {
                it.forEach { mod ->
                    logger.debug { "Valid mod file: ${mod.name}" }
                }
            }
        }
    }

    /**
     * Writes content to a file at the specified path.
     * @param path The path where to write the file
     * @param content The content to write
     * @throws IOException if writing fails
     */
    fun writeFile(path: String, content: String) {
        logger.debug { "Writing file: $path" }
        try {
            File(path).apply {
                // Ensure the parent directories exist
                parentFile?.mkdirs()
                writeText(content)
            }
            logger.info { "Successfully wrote file: $path" }
        } catch (e: IOException) {
            logger.error(e) { "Failed to write file: $path" }
            throw e
        } catch (e: SecurityException) {
            logger.error(e) { "Security error writing file: $path" }
            throw IOException("Security error writing file: $path", e)
        }
    }

    /**
     * Reads the content of a file.
     * @param file The file to read
     * @return The content of the file as a string
     * @throws IOException if reading fails
     */
    fun readFile(file: File): String {
        logger.debug { "Reading file: ${file.name}" }
        return try {
            file.readText().also {
                logger.debug { "Successfully read file: ${file.name} (${it.length} characters)" }
            }
        } catch (e: MalformedInputException) {
            logger.error(e) { "Invalid character encoding in file: ${file.name}" }
            throw IOException("Invalid character encoding in file: ${file.name}", e)
        } catch (e: IOException) {
            logger.error(e) { "Failed to read file: ${file.name}" }
            throw e
        } catch (e: SecurityException) {
            logger.error(e) { "Security error reading file: ${file.name}" }
            throw IOException("Security error reading file: ${file.name}", e)
        }
    }

    /**
     * Creates a ModFile from a regular File, with validation and error handling.
     * @param file The file to convert
     * @return ModFile instance
     * @throws IOException if file reading fails
     * @throws IllegalArgumentException if file is not a valid mod file
     */
    fun createModFile(file: File): ModFile {
        logger.debug { "Creating ModFile from: ${file.name}" }
        return try {
            ModFile.fromFile(file).also {
                logger.debug { "Successfully created ModFile: ${it.name}" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to create ModFile from: ${file.name}" }
            throw e
        }
    }
}