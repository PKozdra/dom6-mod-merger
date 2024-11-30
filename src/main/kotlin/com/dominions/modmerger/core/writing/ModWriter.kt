package com.dominions.modmerger.core.writing

import com.dominions.modmerger.MergeWarning
import com.dominions.modmerger.domain.LogDispatcher
import com.dominions.modmerger.domain.LogLevel
import com.dominions.modmerger.domain.MappedModDefinition
import com.dominions.modmerger.domain.ModOutputConfig
import com.dominions.modmerger.infrastructure.FileSystem
import mu.KotlinLogging

/**
 * Handles writing merged mod content following Dominions 6 mod structure requirements.
 * Coordinates between header writing, content processing, and resource handling.
 */
class ModWriter(
    private val fileSystem: FileSystem,
    private val logDispatcher: LogDispatcher,
    private val contentWriter: ModContentWriter,
    private val resourceCopier: ModResourceCopier,
    private val headerWriter: ModHeaderWriter
) {
    private val logger = KotlinLogging.logger {}
    private val warnings = mutableListOf<MergeWarning>()

    /**
     * Writes the merged mod by coordinating header writing, content processing, and resource copying.
     *
     * @param mappedDefinitions Map of mod names to their mapped definitions
     * @param config Configuration for the output mod
     * @return List of warnings generated during the mod writing process
     */
    fun writeMergedMod(
        mappedDefinitions: Map<String, MappedModDefinition>,
        config: ModOutputConfig
    ): List<MergeWarning> {
        logger.debug { "Starting mod merge with config: $config" }

        warnings.clear()
        try {
            // Validate mod structure
            fileSystem.ensureValidModStructure(config.modName, config.directory)

            // Get properly structured output file
            val outputFile = fileSystem.getOutputFile(config.modName)
            log(LogLevel.INFO, "Writing merged mod to: ${outputFile.absolutePath}")

            outputFile.bufferedWriter().use { writer ->
                // Write header
                val sourceModFiles = mappedDefinitions.values.map { it.modFile }
                val headerWarnings = headerWriter.writeHeader(writer, config, sourceModFiles)
                warnings.addAll(headerWarnings)

                // Process content
                val contentWarnings = contentWriter.processModContent(mappedDefinitions, writer)
                warnings.addAll(contentWarnings)
            }

            // Copy resources if present
            val resourceWarnings = resourceCopier.copyModResources(config, mappedDefinitions)
            warnings.addAll(resourceWarnings)

            log(LogLevel.INFO, "Successfully wrote merged mod")
            return warnings

        } catch (e: Exception) {
            val errorMsg = "Failed to write merged mod: ${e.message}"
            log(LogLevel.ERROR, errorMsg)
            throw e
        }
    }

    private fun log(level: LogLevel, message: String) {
        logger.info { message }
        logDispatcher.log(level, message)
    }
}
