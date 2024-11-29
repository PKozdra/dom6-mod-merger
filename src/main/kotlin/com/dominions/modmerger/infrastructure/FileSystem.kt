package com.dominions.modmerger.infrastructure

import com.dominions.modmerger.domain.ModFile
import mu.KotlinLogging
import java.io.File
import java.io.IOException

class FileSystem(
    private val outputDir: String = "./output", // Default output directory
    private val outputFileName: String = "modmerger" // Default output file name without extension
) {
    private val logger = KotlinLogging.logger {}

    companion object {
        private const val MOD_EXTENSION = "dm" // Default file extension for mods
    }

    fun findModFiles(directory: File = File(".").absoluteFile): List<ModFile> {
        logger.debug { "Searching for .dm files in directory: ${directory.absolutePath}" }

        return try {
            directory
                .listFiles { file -> file.isFile && file.extension == MOD_EXTENSION }
                ?.mapNotNull { file ->
                    try {
                        logger.debug { "Found mod file: ${file.name}" }
                        ModFile.fromFile(file)
                    } catch (e: Exception) {
                        logger.warn(e) { "Skipping invalid mod file: ${file.name}" }
                        null
                    }
                }
                ?: emptyList()
        } catch (e: IOException) {
            logger.error(e) { "Error reading directory: ${directory.absolutePath}" }
            emptyList()
        } catch (e: SecurityException) {
            logger.error(e) { "Security error accessing directory: ${directory.absolutePath}" }
            emptyList()
        }
    }

    /**
     * Retrieves the full path of the output file, creating the directory if necessary.
     * @return File instance representing the output file.
     */
    fun getOutputFile(): File {
        val outputDirectory = File(outputDir)
        if (!outputDirectory.exists()) {
            logger.info { "Creating output directory: $outputDir" }
            outputDirectory.mkdirs()
        }
        return File(outputDirectory, "$outputFileName.$MOD_EXTENSION")
    }

    /**
     * Writes content to the specified path.
     */
    fun writeFile(path: String, content: String) {
        logger.debug { "Writing file: $path" }
        try {
            File(path).apply {
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
}
