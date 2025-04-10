package com.dominions.modmerger

import com.dominions.modmerger.core.ModMerger
import com.dominions.modmerger.core.writing.config.ModOutputConfig
import com.dominions.modmerger.domain.MergeResult
import com.dominions.modmerger.domain.ModFile
import com.dominions.modmerger.infrastructure.GamePathsManager
import com.dominions.modmerger.infrastructure.Logging
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.system.exitProcess

class AutoMergeMode(
    private val modMerger: ModMerger,
    private val gamePathsManager: GamePathsManager
) : Logging {
    fun run(args: Array<String>) {
        val parsedArgs = parseArgs(args)

        if (!validateArgs(parsedArgs)) {
            exitProcess(1)
        }

        val modFiles = loadModFiles(parsedArgs.modPaths)
        if (modFiles.isEmpty()) {
            error("No valid mod files found")
            exitProcess(1)
        }

        // Ensure the output directory exists
        if (!parsedArgs.outputPath.exists()) {
            info("Creating output directory: ${parsedArgs.outputPath}")
            parsedArgs.outputPath.mkdirs()
        }

        // Create default config with proper output directory
        val config = ModOutputConfig(
            modName = parsedArgs.outputName.removeSuffix(".dm"),
            displayName = parsedArgs.outputName.removeSuffix(".dm").replace('_', ' '),
            directory = parsedArgs.outputPath,
            description = "Automatically merged via ModMerger",
            version = "1.0",
            sourceMods = modFiles.map { it.modName },
            gamePathsManager = gamePathsManager
        )

        // Handle cleaning of target mod directory if requested
        val targetModDir = File(parsedArgs.outputPath, config.modName)
        if (parsedArgs.cleanOutput) {
            cleanTargetDirectory(targetModDir)
        } else if (targetModDir.exists()) {
            val fileCount = countFilesInDirectory(targetModDir)
            if (fileCount > 0) {
                warn("Directory '${targetModDir.absolutePath}' contains $fileCount files that may be overwritten")
            }
        }

        // Update the merger's config
        modMerger.updateConfig(config)

        info("Starting auto-merge with ${modFiles.size} mods")
        info("Output will be created at: ${File(parsedArgs.outputPath, parsedArgs.outputName).absolutePath}")

        runBlocking {
            when (val result = modMerger.mergeMods(modFiles)) {
                is MergeResult.Success -> {
                    info("Merge completed successfully")
                    if (result.warnings.isNotEmpty()) {
                        warn("Warnings during merge:")
                        result.warnings.forEach { warning ->
                            warn("- $warning")
                        }
                    }
                    exitProcess(0)
                }
                is MergeResult.Failure -> {
                    error("Merge failed: ${result.error}")
                    exitProcess(1)
                }
            }
        }
    }

    private fun cleanTargetDirectory(directory: File) {
        if (!directory.exists()) return

        info("Cleaning mod directory: ${directory.absolutePath}")
        directory.walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                try {
                    file.delete()
                    trace("Deleted file: ${file.absolutePath}")
                } catch (e: Exception) {
                    warn("Failed to delete file ${file.absolutePath}: ${e.message}")
                }
            }
    }

    private fun countFilesInDirectory(directory: File): Int {
        return if (directory.exists()) {
            directory.walkTopDown()
                .filter { it.isFile }
                .count()
        } else {
            0
        }
    }

    private fun parseArgs(args: Array<String>): AutoMergeArgs {
        var modPaths = listOf<String>()
        var outputName = "merged_mod.dm"
        var outputPath = File(System.getProperty("user.dir"))
        var cleanOutput = false

        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--mods" -> {
                    if (i + 1 < args.size) {
                        modPaths = args[i + 1]
                            .trim('[', ']', '"')
                            .split(',')
                            .map { it.trim('"', ' ') }
                        i += 2
                    }
                }
                "--output" -> {
                    if (i + 1 < args.size) {
                        outputName = args[i + 1]
                        if (!outputName.endsWith(".dm")) {
                            outputName += ".dm"
                        }
                        i += 2
                    }
                }
                "--output-path" -> {
                    if (i + 1 < args.size) {
                        outputPath = File(args[i + 1])
                        i += 2
                    }
                }
                "--clean" -> {
                    cleanOutput = true
                    i++
                }
                else -> i++
            }
        }

        return AutoMergeArgs(
            modPaths = modPaths,
            outputName = outputName,
            outputPath = outputPath,
            cleanOutput = cleanOutput
        )
    }

    private fun validateArgs(args: AutoMergeArgs): Boolean {
        if (args.modPaths.isEmpty()) {
            error("No mod paths provided. Use --mods with a list of complete paths to mod files")
            return false
        }

        if (!args.outputPath.exists()) {
            info("Creating output directory: ${args.outputPath}")
            if (!args.outputPath.mkdirs()) {
                error("Failed to create output directory: ${args.outputPath}")
                return false
            }
        }

        return true
    }

    private fun loadModFiles(modPaths: List<String>): List<ModFile> {
        return modPaths.mapNotNull { path ->
            val modFile = File(path)
            if (modFile.exists()) {
                ModFile.fromFile(modFile).also {
                    info("Loaded mod: ${modFile.absolutePath}")
                }
            } else {
                warn("Mod file not found: ${modFile.absolutePath}")
                null
            }
        }
    }

    private data class AutoMergeArgs(
        val modPaths: List<String>,
        val outputName: String,
        val outputPath: File,
        val cleanOutput: Boolean
    )

    companion object {
        fun printUsage() {
            println("""
                Auto-merge mode usage:
                java -jar modmerger.jar --auto-merge [options]

                Options:
                --mods <paths>     List of complete paths to mod files (required)
                                  Format: ["/path/to/mod1.dm","/path/to/mod2.dm"]
                --output <name>    Output filename for merged mod (optional)
                                  Default: merged_mod.dm
                --output-path <dir> Directory to store the merged mod (optional)
                                  Default: Current directory
                --clean           Clean output directory before merging (optional)
                                  Removes all files in the target mod directory

                Example:
                java -jar modmerger.jar --auto-merge \
                    --mods "[/path/to/mod1.dm,/path/to/mod2.dm]" \
                    --output merged.dm \
                    --output-path /path/to/output \
                    --clean

                Exit codes:
                0 - Success
                1 - Error (check logs for details)
            """.trimIndent())
        }
    }
}