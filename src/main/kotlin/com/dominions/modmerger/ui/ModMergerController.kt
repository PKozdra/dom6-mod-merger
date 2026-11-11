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
import com.dominions.modmerger.domain.ModPreset
import com.dominions.modmerger.gamedata.GameDataProvider
import com.dominions.modmerger.infrastructure.GamePathsManager
import com.dominions.modmerger.infrastructure.Logging
import com.dominions.modmerger.infrastructure.PreferencesManager
import com.dominions.modmerger.ui.components.ModPresetsPanel
import com.dominions.modmerger.ui.model.ModListItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
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
    private var selectedModsProvider: (() -> List<ModListItem>)? = null
    private var missingModsCallback: ((List<String>) -> Unit)? = null

    fun setOutputConfigProvider(provider: () -> ModOutputConfig?) {
        outputConfigProvider = provider
    }

    fun setModLoadListener(listener: (List<ModListItem>) -> Unit) {
        modLoadListener = listener
    }

    fun setSelectedModsProvider(provider: () -> List<ModListItem>) {
        selectedModsProvider = provider
    }

    fun setMissingModsCallback(callback: (List<String>) -> Unit) {
        missingModsCallback = callback
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
        coroutineScope.launch(Dispatchers.IO) {
            val startNanos = System.nanoTime()
            val modItems = try {
                scanMods()
            } catch (e: Exception) {
                error("Error scanning mods: ${e.message}", e)
                emptyList()
            }
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000

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
                info("Found ${modItems.size} total mods in $elapsedMs ms")
            }
        }
    }

    private fun scanMods(): List<ModListItem> {
        val paths = buildList {
            gamePathsManager.findSteamModPath()?.let {
                add(it)
                info("Found Steam workshop path: $it", useDispatcher = false)
            }

            add(gamePathsManager.getLocalModPath().also {
                info("Using local mods path: $it", useDispatcher = false)
            })
        }

        if (paths.isEmpty()) {
            warn("No valid mod paths found. Please specify a custom path in Settings.")
            return emptyList()
        }

        val modItems = mutableListOf<ModListItem>()
        val modsByGroup = mutableMapOf<ModGroup?, MutableList<String>>()

        paths.forEach { path ->
            try {
                findModFiles(path).forEach { modFile ->
                    val group = groupRegistry.findGroupForMod(modFile)
                    modsByGroup.getOrPut(group) { mutableListOf() }.add(modFile.modName)
                    modItems.add(ModListItem(modFile = modFile, group = group))
                }
            } catch (e: Exception) {
                error("Error loading mods from $path: ${e.message}", e)
            }
        }

        return modItems.map { mod ->
            if (mod.group == null) {
                mod
            } else {
                val relatedMods = modsByGroup[mod.group]?.filter { it != mod.modFile.modName } ?: emptyList()
                mod.copy(relatedMods = relatedMods)
            }
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

    private fun findModFiles(root: File): List<ModFile> {
        if (!root.isDirectory) return emptyList()

        val suffix = ".${GameConstants.MOD_FILE_EXTENSION}"
        val found = mutableListOf<ModFile>()
        Files.walkFileTree(root.toPath(), object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (attrs.isRegularFile && file.fileName.toString().endsWith(suffix, ignoreCase = true)) {
                    try {
                        found.add(ModFile.fromFile(file.toFile()))
                    } catch (e: Exception) {
                        warn("Skipping mod file $file: ${e.message}", useDispatcher = false)
                    }
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
        })
        return found
    }


    fun savePreset(name: String, overwrite: Boolean): Boolean {
        val selectedMods = selectedModsProvider?.invoke() ?: emptyList()
        val selectedPaths = selectedMods
            .mapNotNull { it.modFile.file?.absolutePath }
            .toSet()

        if (selectedPaths.isEmpty()) {
            warn("Cannot save preset: No mods selected")
            return false
        }

        // Check if preset exists and we're not overwriting
        if (!overwrite) {
            val existingPresets = PreferencesManager.getPresets()
            if (existingPresets.any { it.name == name }) {
                warn("Preset '$name' already exists and overwrite is not enabled")
                return false
            }
        }

        return PreferencesManager.savePreset(name, selectedPaths).also { success ->
            if (success) {
                info("Saved preset '$name' with ${selectedPaths.size} mods")
            }
        }
    }

    /**
     * Loads a preset by name and applies it to the current mod list
     */
    fun loadPreset(name: String): Boolean {
        val preset = PreferencesManager.getPresets().find { it.name == name }
        if (preset == null) {
            warn("Preset '$name' not found", useDispatcher = false)
            return false
        }

        if (preset.modPaths.isEmpty()) {
            warn("Preset '$name' contains no mod paths", useDispatcher = false)
            return false
        }

        return applyPresetSelection(preset.modPaths, name)
    }

    /**
     * Applies a preset selection to the current mod list
     */
    private fun applyPresetSelection(presetPaths: Set<String>, presetName: String): Boolean {
        // Get ALL available mods
        val allAvailableMods = getAllAvailableModsProvider?.invoke()?.toList() ?: emptyList()
        if (allAvailableMods.isEmpty()) {
            warn("No mods are loaded", useDispatcher = false)
            return false
        }

        debug("Applying preset '$presetName' with ${presetPaths.size} paths", useDispatcher = false)

        // Create a map of normalized paths to mods for easier lookup
        val modsByPath = allAvailableMods.mapNotNull { mod ->
            mod.modFile.file?.absolutePath?.normalizePath()?.let { path -> path to mod }
        }.toMap()

        // Process each preset path and track missing mods
        val missingModNames = mutableListOf<String>()
        val selectedPaths = mutableSetOf<String>()

        presetPaths.forEach { presetPath ->
            val normalizedPath = presetPath.normalizePath()

            // Check if path exists in available mods
            if (normalizedPath in modsByPath.keys) {
                // Found the mod - add its actual path to selected paths
                modsByPath[normalizedPath]?.modFile?.file?.absolutePath?.let {
                    selectedPaths.add(it)
                }
            } else {
                // Path not found - add to missing mods
                missingModNames.add(File(presetPath).nameWithoutExtension)
            }
        }

        // Update the selection state of all mods
        val updatedMods = allAvailableMods.map { mod ->
            val isSelected = mod.modFile.file?.absolutePath in selectedPaths
            mod.copy(isSelected = isSelected)
        }

        // Send the updated list to the UI
        modLoadListener?.invoke(updatedMods)

        // Report any missing mods
        if (missingModNames.isNotEmpty()) {
            warn("Preset '$presetName' contains ${missingModNames.size} mods that could not be found: ${missingModNames.joinToString()}", useDispatcher = false)
            missingModsCallback?.invoke(missingModNames)
        }

        val selectedCount = selectedPaths.size
        info("Applied preset '$presetName' selection: $selectedCount mods selected, ${missingModNames.size} mods missing", useDispatcher = true)
        return true
    }

    // Simple helper to normalize paths
    private fun String.normalizePath(): String {
        return this.replace('\\', '/').lowercase()
    }

    private var getAllAvailableModsProvider: (() -> List<ModListItem>)? = null

    fun setAllAvailableModsProvider(provider: () -> List<ModListItem>) {
        getAllAvailableModsProvider = provider
    }

    // Add this callback
    private var updateSelectionStatesCallback: ((Set<String>) -> Unit)? = null

    fun setUpdateSelectionStatesCallback(callback: (Set<String>) -> Unit) {
        updateSelectionStatesCallback = callback
    }

    fun deletePreset(name: String): Boolean {
        return PreferencesManager.deletePreset(name).also { success ->
            if (success) {
                info("Deleted preset '$name'")
            } else {
                warn("Failed to delete preset '$name'")
            }
        }
    }

    fun getAllPresets(): List<ModPreset> {
        return PreferencesManager.getPresets()
    }
}