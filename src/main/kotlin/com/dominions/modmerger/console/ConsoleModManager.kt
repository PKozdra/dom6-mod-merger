package com.dominions.modmerger.console

import com.dominions.modmerger.constants.GameConstants
import com.dominions.modmerger.core.ModMerger
import com.dominions.modmerger.core.writing.config.ModOutputConfig
import com.dominions.modmerger.domain.MergeResult
import com.dominions.modmerger.domain.ModFile
import com.dominions.modmerger.infrastructure.GamePathsManager
import com.dominions.modmerger.infrastructure.Logging
import com.dominions.modmerger.ui.model.ModListItem
import java.io.File

class ConsoleModManager(
    private val modMerger: ModMerger,
    private val gamePathsManager: GamePathsManager
) : Logging {
    private var allMods: List<ModListItem> = emptyList()
    private var filteredMods: List<ModListItem> = emptyList()
    private val selectedMods = mutableListOf<ModListItem>()
    private val customPaths = mutableListOf<File>()

    fun loadMods() {
        debug("Loading mods from all paths")
        val paths = buildList {
            gamePathsManager.findSteamModPath()?.let {
                add(it)
                info("Found Steam workshop path: $it")
            }
            add(gamePathsManager.getLocalModPath().also {
                info("Using local mods path: $it")
            })
            addAll(customPaths.also {
                if (it.isNotEmpty()) {
                    info("Using ${it.size} custom paths")
                }
            })
        }

        if (paths.isEmpty()) {
            error("No valid mod paths found")
            return
        }

        allMods = paths.flatMap { path ->
            path.walkTopDown()
                .filter { it.isFile && it.extension == GameConstants.MOD_FILE_EXTENSION }
                .map { ModFile.fromFile(it) }
                .map { ModListItem(it) }
                .toList()
        }
        filteredMods = allMods
        debug("Loaded ${allMods.size} total mods")
    }

    fun filterMods(filter: String): List<ModListItem> {
        filteredMods = allMods.filter {
            it.modName.contains(filter, ignoreCase = true) ||
                    it.fileName.contains(filter, ignoreCase = true)
        }
        debug("Filter '${filter}' applied - ${filteredMods.size} mods match")
        return filteredMods
    }

    fun clearFilter() {
        debug("Clearing mod filter")
        filteredMods = allMods
    }

    fun getFilteredMods(): List<ModListItem> = filteredMods

    fun getSelectedMods(): List<ModListItem> = selectedMods.toList()

    fun addCustomPath(path: File): Boolean {
        return if (path.exists() && path.isDirectory) {
            customPaths.add(path)
            debug("Added custom path: ${path.absolutePath}")
            loadMods()
            true
        } else {
            debug("Failed to add invalid path: ${path.absolutePath}")
            false
        }
    }

    fun removeCustomPath(index: Int): Boolean {
        return if (index in customPaths.indices) {
            val path = customPaths.removeAt(index)
            debug("Removed custom path: ${path.absolutePath}")
            loadMods()
            true
        } else {
            debug("Failed to remove custom path at index $index")
            false
        }
    }

    fun clearCustomPaths() {
        debug("Clearing all custom paths")
        customPaths.clear()
        loadMods()
    }

    fun getCustomPaths(): List<File> = customPaths.toList()

    fun toggleModSelection(mod: ModListItem) {
        val existing = selectedMods.find { it.modFile.file?.absolutePath == mod.modFile.file?.absolutePath }
        if (existing != null) {
            selectedMods.remove(existing)
            debug("Deselected mod: ${mod.modName}")
        } else {
            selectedMods.add(mod)
            debug("Selected mod: ${mod.modName}")
        }
    }

    fun removeSelectedMod(index: Int): Boolean {
        return if (index in selectedMods.indices) {
            val mod = selectedMods.removeAt(index)
            debug("Removed mod from selection: ${mod.modName}")
            true
        } else {
            debug("Failed to remove mod at index $index")
            false
        }
    }

    fun clearSelectedMods() {
        debug("Clearing all selected mods")
        selectedMods.clear()
    }

    fun findModByFilename(filename: String): ModListItem? {
        return filteredMods.find { it.fileName == filename }.also { result ->
            if (result != null) {
                debug("Found mod by filename: $filename")
            } else {
                debug("No mod found with filename: $filename")
            }
        }
    }

    suspend fun performMerge(): MergeResult {
        if (selectedMods.size < 2) {
            debug("Cannot merge: Not enough mods selected (${selectedMods.size})")
            return MergeResult.Failure("At least two mods must be selected")
        }

        info("Starting merge with ${selectedMods.size} mods")

        val config = ModOutputConfig(
            modName = "merged_mod.dm",
            displayName = "Merged Mod",
            directory = File(System.getProperty("user.dir")),
            description = "Automatically merged via Console",
            version = "1.0",
            gamePathsManager = gamePathsManager
        )

        modMerger.updateConfig(config)
        return modMerger.mergeMods(selectedMods.map { it.modFile })
    }
}