package com.dominions.modmerger.ui

import com.dominions.modmerger.constants.GameConstants
import com.dominions.modmerger.core.ModMerger
import com.dominions.modmerger.core.mapping.IdManager
import com.dominions.modmerger.core.processing.EntityProcessor
import com.dominions.modmerger.core.processing.SpellBlockProcessor
import com.dominions.modmerger.core.writing.ModContentWriter
import com.dominions.modmerger.core.writing.ModHeaderWriter
import com.dominions.modmerger.core.writing.ModResourceCopier
import com.dominions.modmerger.core.writing.ModWriter
import com.dominions.modmerger.core.writing.config.ModOutputConfig
import com.dominions.modmerger.domain.MergeResult
import com.dominions.modmerger.domain.ModFile
import com.dominions.modmerger.domain.ModGroup
import com.dominions.modmerger.domain.ModGroupRegistry
import com.dominions.modmerger.gamedata.GameDataProvider
import com.dominions.modmerger.infrastructure.GamePathsManager
import com.dominions.modmerger.infrastructure.Logging
import com.dominions.modmerger.infrastructure.PreferencesManager
import com.dominions.modmerger.ui.model.ModListItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import javax.swing.SwingUtilities

class ModMergerController(
    private val modMergerService: ModMerger,
    private val gamePathsManager: GamePathsManager,
    private val groupRegistry: ModGroupRegistry,
    private val gameDataProvider: GameDataProvider
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

    // Add this function to check for existing files
    fun checkExistingFiles(config: ModOutputConfig): Int {
        val writer = ModWriter(
            contentWriter = ModContentWriter(entityProcessor = EntityProcessor(gameDataProvider = gameDataProvider), spellBlockProcessor = SpellBlockProcessor(gameDataProvider)),
            resourceCopier = ModResourceCopier(),
            headerWriter = ModHeaderWriter()
        )
        return writer.checkExistingFiles(config)
    }

    fun loadMods(onComplete: ((List<ModListItem>) -> Unit)? = null) {
        val paths = buildList {
            // Add Steam workshop path if available
            gamePathsManager.findSteamModPath()?.let {
                add(it)
                info("Found Steam workshop path: $it", useDispatcher = false)
            }

            // Add local mods directory
            add(gamePathsManager.getLocalModPath().also {
                info("Using local mods path: $it", useDispatcher = false)
            })
        }

        if (paths.isEmpty()) {
            warn("No valid mod paths found. Please specify a custom path in Settings.")
            return
        }

        val modItems = mutableListOf<ModListItem>()
        var totalMods = 0

        val modsByGroup = mutableMapOf<ModGroup?, MutableList<String>>()

        paths.forEach { path ->
            try {
                val mods = findModFiles(path)
                mods.forEach { modFile ->
                    val group = groupRegistry.findGroupForMod(modFile)
                    modsByGroup.getOrPut(group) { mutableListOf() }.add(modFile.modName)

                    modItems.add(ModListItem(
                        modFile = modFile,
                        group = group,
                        relatedMods = emptyList() // Filled in later
                    ))
                    totalMods++
                }
            } catch (e: Exception) {
                error("Error loading mods from $path: ${e.message}", e)
            }
        }

        // Fill in related mods
        modItems.forEachIndexed { index, mod ->
            if (mod.group != null) {
                val relatedMods = modsByGroup[mod.group]?.filter { it != mod.modFile.modName } ?: emptyList()
                modItems[index] = mod.copy(relatedMods = relatedMods)
            }
        }

        SwingUtilities.invokeLater {
            if (PreferencesManager.isAutoRestoreEnabled) {
                val savedPaths = PreferencesManager.getSelectedModPaths()
                modLoadListener?.invoke(modItems.map { mod ->
                    mod.copy(isSelected = mod.modFile.file?.absolutePath in savedPaths)
                })
            } else {
                modLoadListener?.invoke(modItems)
            }
            onComplete?.invoke(modItems)
            info("Found $totalMods total mods")
        }
    }

    fun mergeMods(mods: List<ModFile>, config: ModOutputConfig, onMergeCompleted: () -> Unit) {
        modMergerService.updateConfig(config)

        info("Starting merge of ${mods.size} mods...")
        info("Output will be created as: ${config.modName}")

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

    fun saveSelections(selectedMods: List<ModListItem>) {
        if (PreferencesManager.isAutoRestoreEnabled) {
            val paths = selectedMods
                .mapNotNull { it.modFile.file?.absolutePath }
                .toSet()
            PreferencesManager.saveSelectedModPaths(paths)
        }
    }

    private fun findModFiles(path: File): List<ModFile> {
        return path.walkTopDown()
            .filter { it.isFile && it.extension == GameConstants.MOD_FILE_EXTENSION }
            .map { ModFile.fromFile(it) }
            .toList()
    }
}