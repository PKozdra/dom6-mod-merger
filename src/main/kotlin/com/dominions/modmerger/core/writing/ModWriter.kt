package com.dominions.modmerger.core.writing

import com.dominions.modmerger.core.writing.config.ModOutputConfig
import com.dominions.modmerger.domain.MappedModDefinition
import com.dominions.modmerger.domain.MergeResult
import com.dominions.modmerger.domain.MergeWarning
import com.dominions.modmerger.infrastructure.Logging
import java.io.File
import java.nio.file.Files


/**
 * Handles writing merged mod content with transactional safety.
 * Ensures atomic operations and proper rollback in case of failures.
 */
class ModWriter(
    private val contentWriter: ModContentWriter,
    private val resourceCopier: ModResourceCopier,
    private val headerWriter: ModHeaderWriter
) : Logging {

    /**
     * Writes the merged mod with transactional safety.
     * If any step fails, all changes are rolled back.
     */
    fun writeMergedMod(
        mappedDefinitions: Map<String, MappedModDefinition>,
        config: ModOutputConfig
    ): MergeResult {
        val warnings = mutableListOf<MergeWarning>()
        val tempDir = createTempDirectory(config)
        val backupDir = createBackupDirectory(config)

        try {
            // Create temporary working directory
            val tempModDir = File(tempDir, config.modName)
            tempModDir.mkdirs()

            // Write mod content to temp directory
            val tempOutputFile = File(tempModDir, "${config.modName}.dm")
            val startTimeContent = System.currentTimeMillis()
            writeModContent(tempOutputFile, mappedDefinitions, config, warnings)
            val endTimeContent = System.currentTimeMillis()
            info("Mod content written in ${endTimeContent - startTimeContent} ms")

            // Copy resources to temp directory
            val startTimeResources = System.currentTimeMillis()
            val resourceWarnings = resourceCopier.copyModResources(
                config.copy(directory = tempDir),
                mappedDefinitions
            )
            val endTimeResources = System.currentTimeMillis()
            info("Resources copied in ${endTimeResources - startTimeResources} ms")
            warnings.addAll(resourceWarnings)

            // Backup existing mod if present
            val targetModDir = File(config.directory, config.modName)
            if (targetModDir.exists()) {
                targetModDir.copyRecursively(File(backupDir, config.modName), overwrite = true)
            }

            // Atomic move of temporary directory to final location
            val success = commitChanges(tempModDir, targetModDir)
            if (!success) {
                throw ModWriterException("Failed to commit mod changes")
            }

            return MergeResult.Success(warnings)

        } catch (e: Exception) {
            error("Failed to write merged mod: ${e.message}", e)
            rollback(config, backupDir)
            error("Failed to process mods: ${e.message}")
            return MergeResult.Failure(e.message ?: "Unknown error occurred")
        } finally {
            cleanup(tempDir, backupDir)
        }
    }

    private fun writeModContent(
        outputFile: File,
        mappedDefinitions: Map<String, MappedModDefinition>,
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
            val contentWarnings = contentWriter.processModContent(mappedDefinitions, writer)
            warnings.addAll(contentWarnings)
        }
    }

    private fun createTempDirectory(config: ModOutputConfig): File {
        val tempDir = Files.createTempDirectory("mod_merger_temp").toFile()
        tempDir.deleteOnExit()
        return tempDir
    }

    private fun createBackupDirectory(config: ModOutputConfig): File {
        val backupDir = Files.createTempDirectory("mod_merger_backup").toFile()
        backupDir.deleteOnExit()
        return backupDir
    }

    private fun commitChanges(tempModDir: File, targetModDir: File): Boolean {
        if (targetModDir.exists()) {
            targetModDir.deleteRecursively()
        }
        return tempModDir.renameTo(targetModDir)
    }

    private fun rollback(config: ModOutputConfig, backupDir: File) {
        val targetModDir = File(config.directory, config.modName)
        val backupModDir = File(backupDir, config.modName)

        if (targetModDir.exists()) {
            targetModDir.deleteRecursively()
        }

        if (backupModDir.exists()) {
            backupModDir.copyRecursively(targetModDir, overwrite = true)
        }
    }

    private fun cleanup(tempDir: File, backupDir: File) {
        tempDir.deleteRecursively()
        backupDir.deleteRecursively()
    }

}

class ModWriterException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Extension of ModOutputConfig to support temporary directories
 */
private fun ModOutputConfig.copy(directory: File): ModOutputConfig =
    ModOutputConfig(
        modName = this.modName,
        displayName = this.displayName,
        directory = directory,
        description = this.description,
        version = this.version,
        icon = this.icon,
        sourceMods = this.sourceMods,
        gamePathsManager = this.gamePathsManager
    )