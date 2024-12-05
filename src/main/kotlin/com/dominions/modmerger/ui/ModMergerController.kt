package com.dominions.modmerger.ui

import com.dominions.modmerger.constants.GameConstants
import com.dominions.modmerger.core.ModMerger
import com.dominions.modmerger.core.writing.config.ModOutputConfig
import com.dominions.modmerger.domain.MergeResult
import com.dominions.modmerger.domain.ModFile
import com.dominions.modmerger.infrastructure.GamePathsManager
import com.dominions.modmerger.infrastructure.Logging
import com.dominions.modmerger.ui.model.ModListItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import javax.swing.SwingUtilities

class ModMergerController(
    private val modMergerService: ModMerger,
    private val gamePathsManager: GamePathsManager,
) : Logging {
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private var modLoadListener: ((List<ModListItem>) -> Unit)? = null
    private var outputConfigProvider: (() -> ModOutputConfig?)? = null

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
                info("Found Steam workshop path: $it")
            }

            // Add local mods directory
            add(gamePathsManager.getLocalModPath().also {
                info("Using local mods path: $it")
            })
        }

        if (paths.isEmpty()) {
            warn("No valid mod paths found. Please specify a custom path.")
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
                error("Error loading mods from $path: ${e.message}", e)
            }
        }

        SwingUtilities.invokeLater {
            modLoadListener?.invoke(modItems)
        }
        info("Found $totalMods total mods")
    }

    fun mergeMods(mods: List<ModFile>, onMergeCompleted: () -> Unit) {
        val outputConfig = outputConfigProvider?.invoke()
        if (outputConfig == null) {
            error("Please configure output settings first")
            onMergeCompleted()
            return
        }

        info("Starting merge of ${mods.size} mods...")
        info("Output will be created as: ${outputConfig.modName}")

        coroutineScope.launch {
            try {
                info("Processing mods: ${mods.joinToString { it.name }}")
                val result = modMergerService.mergeMods(mods)
                SwingUtilities.invokeLater {
                    try {
                        when (result) {
                            is MergeResult.Success -> {
                                info("Merge completed successfully!")
                                if (result.warnings.isNotEmpty()) {
                                    warn("Warnings encountered during merge:")
                                    result.warnings.forEach { warning ->
                                        warn("- $warning")
                                    }
                                }
                            }

                            is MergeResult.Failure -> {
                                error("Merge failed: ${result.error}")
                            }
                        }
                    } catch (e: Exception) {
                        error("Error updating UI: ${e.message}", e)
                    } finally {
                        onMergeCompleted()
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    error("Error during merge: ${e.message}", e)
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