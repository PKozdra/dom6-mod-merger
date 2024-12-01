package com.dominions.modmerger.core.writing

import com.dominions.modmerger.MergeWarning
import com.dominions.modmerger.domain.*
import mu.KotlinLogging
import java.io.File

/**
 * Handles copying of mod resources like graphics, sounds, and other assets.
 */
class ModResourceCopier(
    private val logDispatcher: LogDispatcher
) {
    private val logger = KotlinLogging.logger {}
    private val warnings = mutableListOf<MergeWarning>()

    companion object {
        private val EXCLUDED_FILES = setOf(
            "dom6ws.tmp",
            "dom6ws_pfid"
        )
        private val EXCLUDED_EXTENSIONS = setOf(
            "dm",  // mod files are handled separately
            "tmp"  // temporary files
        )
    }

    /**
     * Copies resources from the source mods to the target mod directory.
     *
     * @param config Configuration for the output mod
     * @param mappedDefinitions Map of mod names to their mapped definitions
     * @return List of warnings generated during resource copying
     */
    fun copyModResources(
        config: ModOutputConfig,
        mappedDefinitions: Map<String, MappedModDefinition>
    ): List<MergeWarning> {
        warnings.clear()
        val targetModDir = File(config.directory, config.modName)

        mappedDefinitions.values.forEach { mappedDef ->
            val sourceModDir = mappedDef.modFile.file?.parentFile ?: return@forEach
            copyDirectoryContent(sourceModDir, targetModDir, mappedDef.modFile)
        }

        return warnings
    }

    private fun copyDirectoryContent(sourceDir: File, targetDir: File, modFile: ModFile) {
        if (!sourceDir.exists() || !sourceDir.isDirectory) {
            log(LogLevel.WARN, "Source directory does not exist or is not a directory: ${sourceDir.absolutePath}")
            return
        }

        try {
            // Create target directory if it doesn't exist
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            // Walk through all files and directories
            sourceDir.walkTopDown()
                .filter { file -> shouldCopyFile(file) }
                .forEach { source ->
                    val relativePath = source.toRelativeString(sourceDir)
                    val target = File(targetDir, relativePath)

                    when {
                        source.isDirectory -> {
                            target.mkdirs()
                            log(LogLevel.DEBUG, "Created directory: ${target.absolutePath}")
                        }
                        source.isFile -> {
                            copyFileWithConflictResolution(source, target, modFile)
                        }
                    }
                }
        } catch (e: Exception) {
            val warningMessage = "Failed to copy resources from ${sourceDir.absolutePath}: ${e.message}"
            warnings.add(
                MergeWarning.ResourceWarning(
                    modFile = modFile,
                    message = warningMessage,
                    resourcePath = sourceDir.absolutePath
                )
            )
            log(LogLevel.ERROR, warningMessage)
        }
    }

    private fun shouldCopyFile(file: File): Boolean {
        return when {
            file.isDirectory -> true
            file.name in EXCLUDED_FILES -> false
            file.extension.lowercase() in EXCLUDED_EXTENSIONS -> false
            else -> true
        }
    }

    private fun copyFileWithConflictResolution(source: File, target: File, modFile: ModFile) {
        try {
            if (target.exists()) {
                handleFileConflict(source, target)
            } else {
                target.parentFile?.mkdirs()
                source.copyTo(target)
                log(LogLevel.DEBUG, "Copied file: ${source.name} -> ${target.absolutePath}")
            }
        } catch (e: Exception) {
            val warningMessage = "Failed to copy file ${source.name}: ${e.message}"
            warnings.add(
                MergeWarning.ResourceWarning(
                    modFile = modFile,
                    message = warningMessage,
                    resourcePath = source.absolutePath
                )
            )
            log(LogLevel.WARN, warningMessage)
        }
    }

    private fun handleFileConflict(source: File, target: File) {
        // Use "latest wins" strategy
        if (source.lastModified() > target.lastModified()) {
            source.copyTo(target, overwrite = true)
            log(LogLevel.INFO, "Overwrote existing file with newer version: ${target.absolutePath}")
        } else {
            log(LogLevel.DEBUG, "Kept existing file as it's newer: ${target.absolutePath}")
        }
    }

    private fun log(level: LogLevel, message: String) {
        //logger.info { message }
        //logDispatcher.log(level, message)
    }
}
