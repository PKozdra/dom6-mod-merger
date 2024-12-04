package com.dominions.modmerger.core.writing

import com.dominions.modmerger.domain.MergeWarning
import com.dominions.modmerger.core.writing.config.ModOutputConfig
import com.dominions.modmerger.domain.MappedModDefinition
import com.dominions.modmerger.domain.ModFile
import com.dominions.modmerger.infrastructure.Logging
import java.io.File

/**
 * Handles copying of mod resources like graphics, sounds, and other assets.
 */
class ModResourceCopier() : Logging {
    private val warnings = mutableListOf<MergeWarning>()

    private data class CopyStats(
        var totalFiles: Int = 0,
        var processedFiles: Int = 0,
        var skippedFiles: Int = 0,
        var conflictFiles: Int = 0
    ) {
        val progressPercentage: Int
            get() = if (totalFiles > 0) ((processedFiles + skippedFiles) * 100) / totalFiles else 0

        val filesHandled: Int
            get() = processedFiles + skippedFiles
    }

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
        val stats = CopyStats()

        // First count total files to copy
        info("Scanning mod resources...")
        mappedDefinitions.values.forEach { mappedDef ->
            val sourceModDir = mappedDef.modFile.file?.parentFile ?: return@forEach
            countFiles(sourceModDir, stats)
        }
        info("Found ${stats.totalFiles} files to copy")

        // Then copy files with progress tracking
        mappedDefinitions.values.forEach { mappedDef ->
            val sourceModDir = mappedDef.modFile.file?.parentFile ?: return@forEach
            info("Copying resources from: ${mappedDef.modFile.name}")
            copyDirectoryContent(sourceModDir, targetModDir, mappedDef.modFile, stats)
        }

        logFinalStats(stats)
        return warnings
    }

    private fun countFiles(dir: File, stats: CopyStats) {
        dir.walkTopDown()
            .filter { shouldCopyFile(it) }
            .filter { it.isFile }
            .forEach { stats.totalFiles++ }
    }

    private fun copyDirectoryContent(
        sourceDir: File,
        targetDir: File,
        modFile: ModFile,
        stats: CopyStats
    ) {
        if (!sourceDir.exists() || !sourceDir.isDirectory) {
            warn("Source directory not found: ${sourceDir.absolutePath}")
            return
        }

        try {
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            sourceDir.walkTopDown()
                .filter { shouldCopyFile(it) }
                .forEach { source ->
                    val relativePath = source.toRelativeString(sourceDir)
                    val target = File(targetDir, relativePath)

                    when {
                        source.isDirectory -> target.mkdirs()
                        source.isFile -> {
                            copyFileWithProgress(source, target, modFile, stats)
                            logProgress(stats)
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
            error(warningMessage)
        }
    }

    private fun copyFileWithProgress(
        source: File,
        target: File,
        modFile: ModFile,
        stats: CopyStats
    ) {
        try {
            if (target.exists()) {
                handleFileConflict(source, target, stats)
            } else {
                target.parentFile?.mkdirs()
                source.copyTo(target)
                stats.processedFiles++
                //log(LogLevel.DEBUG, "Copied: ${target.name}")
            }
        } catch (e: Exception) {
            stats.skippedFiles++
            val warningMessage = "Failed to copy ${source.name}: ${e.message}"
            warnings.add(
                MergeWarning.ResourceWarning(
                    modFile = modFile,
                    message = warningMessage,
                    resourcePath = source.absolutePath
                )
            )
            warn(warningMessage)
        }
    }

    private fun handleFileConflict(source: File, target: File, stats: CopyStats) {
        stats.conflictFiles++
        if (source.lastModified() > target.lastModified()) {
            source.copyTo(target, overwrite = true)
            stats.processedFiles++
            //log(LogLevel.DEBUG, "Updated: ${target.name} (newer version)")
        } else {
            stats.skippedFiles++
            //log(LogLevel.DEBUG, "Kept existing: ${target.name}")
        }
    }

    private fun logProgress(stats: CopyStats) {
        // Only log if we've handled a new batch of files or reached the end
        if (stats.filesHandled % 50 == 0 || stats.filesHandled == stats.totalFiles) {
            //log(LogLevel.TRACE, buildProgressMessage(stats))
        }
    }

    private fun buildProgressMessage(stats: CopyStats): String {
        return "Processed ${stats.filesHandled}/${stats.totalFiles} files (${stats.progressPercentage}%)"
    }

    private fun logFinalStats(stats: CopyStats) {
        info("""
            Resource copying completed! Total files processed: ${stats.filesHandled}
        """.trimIndent()
        )
    }

    private fun shouldCopyFile(file: File): Boolean = when {
        file.isDirectory -> true
        file.name in EXCLUDED_FILES -> false
        file.extension.lowercase() in EXCLUDED_EXTENSIONS -> false
        else -> true
    }

}