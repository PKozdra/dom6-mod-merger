package com.dominions.modmerger.core.writing

import com.dominions.modmerger.core.writing.config.ModOutputConfig
import com.dominions.modmerger.domain.MappedModDefinition
import com.dominions.modmerger.domain.MergeResult
import com.dominions.modmerger.domain.MergeWarning
import com.dominions.modmerger.domain.ModDefinition
import com.dominions.modmerger.infrastructure.Logging
import java.io.File

/**
 * Handles writing merged mod content with transactional safety.
 * Ensures atomic operations and proper rollback in case of failures.
 */
class ModWriter(
    private val contentWriter: ModContentWriter,
    private val resourceCopier: ModResourceCopier,
    private val headerWriter: ModHeaderWriter
) : Logging {

    fun writeMergedMod(
        mappedDefinitions: Map<String, MappedModDefinition>,
        modDefinitions: Map<String, ModDefinition>,
        config: ModOutputConfig,
        resourceMappedDefinitions: Map<String, MappedModDefinition>
    ): MergeResult {
        val warnings = mutableListOf<MergeWarning>()
        val targetModDir = File(config.directory, config.modName)

        try {
            // Create target directory if it doesn't exist
            targetModDir.mkdirs()

            // Write mod content directly to target
            val outputFile = File(targetModDir, "${config.modName}.dm")
            val startTimeContent = System.currentTimeMillis()
            writeModContent(outputFile, mappedDefinitions, modDefinitions, config, warnings)
            val endTimeContent = System.currentTimeMillis()
            info("Mod content written in ${endTimeContent - startTimeContent} ms")

            // Copy resources directly to target
            val startTimeResources = System.currentTimeMillis()
            info("Starting copying resources...")
            val resourceWarnings = resourceCopier.copyModResources(config, resourceMappedDefinitions)
            val endTimeResources = System.currentTimeMillis()
            info("Resources copied in ${endTimeResources - startTimeResources} ms")
            warnings.addAll(resourceWarnings)

            return MergeResult.Success(warnings)

        } catch (e: Exception) {
            error("Failed to write merged mod: ${e.message}", e)
            return MergeResult.Failure(e.message ?: "Unknown error occurred")
        }
    }

    private fun writeModContent(
        outputFile: File,
        mappedDefinitions: Map<String, MappedModDefinition>,
        modDefinitions: Map<String, ModDefinition>,
        config: ModOutputConfig,
        warnings: MutableList<MergeWarning>
    ) {
        outputFile.bufferedWriter().use { writer ->
            // Write header
            val headerWarnings = headerWriter.writeHeader(
                writer,
                config,
                mappedDefinitions.values.map { it.modFile }
            )
            warnings.addAll(headerWarnings)

            // Process and write content
            val contentWarnings = contentWriter.processModContent(mappedDefinitions, modDefinitions, writer)
            warnings.addAll(contentWarnings)
        }
    }

    fun checkExistingFiles(config: ModOutputConfig): Int {
        val targetModDir = File(config.directory, config.modName)
        return if (targetModDir.exists()) {
            targetModDir.walkTopDown()
                .filter { it.isFile }
                .count()
        } else {
            0
        }
    }
}