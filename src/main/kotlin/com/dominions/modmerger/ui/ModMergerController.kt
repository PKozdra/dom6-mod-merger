// src/main/kotlin/com/dominions/modmerger/ui/ModMergerController.kt
package com.dominions.modmerger.ui

import com.dominions.modmerger.MergeResult
import com.dominions.modmerger.constants.GameConstants
import com.dominions.modmerger.core.ModMerger
import com.dominions.modmerger.core.writing.config.ModOutputConfig
import com.dominions.modmerger.domain.LogDispatcher
import com.dominions.modmerger.domain.LogLevel
import com.dominions.modmerger.domain.ModFile
import com.dominions.modmerger.infrastructure.FileSystem
import com.dominions.modmerger.infrastructure.GamePathsManager
import com.dominions.modmerger.ui.model.ModListItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.io.File
import javax.swing.SwingUtilities

class ModMergerController(
    private val modMergerService: ModMerger,
    private val fileSystem: FileSystem,
    private val gamePathsManager: GamePathsManager,
    private val logDispatcher: LogDispatcher
) {
    private val logger = KotlinLogging.logger {}
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private var modLoadListener: ((List<ModListItem>) -> Unit)? = null

    private var outputConfigProvider: (() -> ModOutputConfig?)? = null

    // Add method to ModMergerController
    fun setOutputConfigProvider(provider: () -> ModOutputConfig?) {
        outputConfigProvider = provider
    }

    fun setModLoadListener(listener: (List<ModListItem>) -> Unit) {
        modLoadListener = listener
    }

    fun loadMods() {
        val paths = buildList {
            // Add Steam workshop path if available
            gamePathsManager.findSteamModPath()?.let {
                add(it)
                logDispatcher.log(LogLevel.INFO, "Found Steam workshop path: $it")
            }

            // Add local mods directory
            add(gamePathsManager.getLocalModPath().also {
                logDispatcher.log(LogLevel.INFO, "Using local mods path: $it")
            })

        }

        if (paths.isEmpty()) {
            logDispatcher.log(LogLevel.WARN, "No valid mod paths found. Please specify a custom path.")
            return
        }

        val modItems = mutableListOf<ModListItem>()
        var totalMods = 0

        paths.forEach { path ->
            try {
                val mods = findModFiles(path)
                mods.forEach { modFile ->
                    modItems.add(ModListItem(modFile))
                    totalMods++
                }
            } catch (e: Exception) {
                logger.error(e) { "Error loading mods from $path" }
                logDispatcher.log(LogLevel.ERROR, "Error loading mods from $path: ${e.message}")
            }
        }

        SwingUtilities.invokeLater {
            modLoadListener?.invoke(modItems)
        }
        logDispatcher.log(LogLevel.INFO, "Found $totalMods total mods")
    }

    // Modify mergeMods method in ModMergerController
    fun mergeMods(mods: List<ModFile>, onMergeCompleted: () -> Unit) {
        val outputConfig = outputConfigProvider?.invoke()
        if (outputConfig == null) {
            logDispatcher.log(LogLevel.ERROR, "Please configure output settings first")
            onMergeCompleted()
            return
        }

        logDispatcher.log(LogLevel.INFO, "Starting merge of ${mods.size} mods...")
        logDispatcher.log(LogLevel.INFO, "Output will be created as: ${outputConfig.modName}")

        coroutineScope.launch {
            try {
                logDispatcher.log(LogLevel.INFO, "Processing mods: ${mods.joinToString { it.name }}")
                val result = modMergerService.mergeMods(mods)
                SwingUtilities.invokeLater {
                    try {
                        when (result) {
                            is MergeResult.Success -> {
                                logDispatcher.log(LogLevel.INFO, "Merge completed successfully!")
                                if (!result.warnings.isNullOrEmpty()) {
                                    logDispatcher.log(LogLevel.WARN, "Warnings encountered during merge:")
                                    result.warnings.forEach { warning ->
                                        logDispatcher.log(LogLevel.WARN, "- $warning")
                                    }
                                }
                            }

                            is MergeResult.Failure -> {
                                logDispatcher.log(LogLevel.ERROR, "Merge failed: ${result.error}")
                            }
                        }
                    } catch (e: Exception) {
                        logger.error(e) { "Error updating UI after merge" }
                        logDispatcher.log(LogLevel.ERROR, "Error updating UI: ${e.message}")
                    } finally {
                        onMergeCompleted()
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Error during merge" }
                SwingUtilities.invokeLater {
                    logDispatcher.log(LogLevel.ERROR, "Error during merge: ${e.message}")
                    onMergeCompleted()
                }
            }
        }
    }

    private fun findModFiles(path: File): List<ModFile> {
        return path.walkTopDown()
            .filter { it.isFile && it.extension == GameConstants.MOD_FILE_EXTENSION }
            .map { ModFile.fromFile(it) }
            .toList()
    }
}
