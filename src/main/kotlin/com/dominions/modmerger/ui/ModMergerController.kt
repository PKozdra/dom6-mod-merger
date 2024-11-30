// src/main/kotlin/com/dominions/modmerger/ui/ModMergerController.kt
package com.dominions.modmerger.ui

import com.dominions.modmerger.MergeResult
import com.dominions.modmerger.core.ModMergerService
import com.dominions.modmerger.domain.*
import com.dominions.modmerger.infrastructure.FileSystem
import com.dominions.modmerger.ui.model.ModListItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.io.File
import javax.swing.SwingUtilities

class ModMergerController(
    private val modMergerService: ModMergerService,
    private val fileSystem: FileSystem,
    private val logDispatcher: LogDispatcher
) {
    private val logger = KotlinLogging.logger {}
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private var modLoadListener: ((List<ModListItem>) -> Unit)? = null

    fun setModLoadListener(listener: (List<ModListItem>) -> Unit) {
        modLoadListener = listener
    }

    fun loadMods(customPathText: String) {
        val steamPath = getSteamModPath()
        val customPath = customPathText.takeIf { it.isNotBlank() }?.let { File(it) }

        val paths = buildList {
            if (steamPath != null) {
                add(steamPath)
                logDispatcher.log(LogLevel.INFO, "Found Steam workshop path: $steamPath")
            }
            if (customPath != null) {
                add(customPath)
                logDispatcher.log(LogLevel.INFO, "Using custom path: $customPath")
            }
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

    fun mergeMods(mods: List<ModFile>, onMergeCompleted: () -> Unit) {
        logDispatcher.log(LogLevel.INFO, "Starting merge of ${mods.size} mods...")

        coroutineScope.launch {
            try {
                logDispatcher.log(LogLevel.INFO, "Processing mods: ${mods.joinToString { it.name }}")
                val result = modMergerService.processMods(mods)
                SwingUtilities.invokeLater {
                    when (result) {
                        is MergeResult.Success -> {
                            logDispatcher.log(LogLevel.INFO, "Merge completed successfully!")
                            if (result.warnings.isNotEmpty()) {
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
                    onMergeCompleted()
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

    private fun getSteamModPath(): File? {
        val osName = System.getProperty("os.name")
        val steamPath = when {
            osName.contains("Windows") ->
                File("""C:\Program Files (x86)\Steam\steamapps\workshop\content\2511500""")
            osName.contains("Mac") ->
                File(
                    System.getProperty("user.home"),
                    "Library/Application Support/Steam/steamapps/workshop/content/2511500"
                )
            else ->
                File(System.getProperty("user.home"), ".steam/steam/steamapps/workshop/content/2511500")
        }
        return if (steamPath.exists()) steamPath else null
    }

    private fun findModFiles(path: File): List<ModFile> {
        return path.walkTopDown()
            .filter { it.isFile && it.extension == "dm" }
            .map { ModFile.fromFile(it) }
            .toList()
    }
}
