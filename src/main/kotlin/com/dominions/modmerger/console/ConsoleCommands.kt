package com.dominions.modmerger.console

import com.dominions.modmerger.domain.MergeResult
import com.dominions.modmerger.infrastructure.Logging
import java.io.File

class ConsoleCommands(
    private val modManager: ConsoleModManager,
    private val pageSize: Int = 10
) : Logging {
    private var currentPage = 0

    fun handleMainMenuCommand(input: String): Boolean {
        return when {
            input.startsWith("select ") -> {
                val filename = input.substringAfter("select ").trim()
                handleDirectModSelection(filename)
                true
            }
            input.startsWith("merge") -> {
                handleMerge()
                true
            }
            input.startsWith("path ") -> {
                val path = input.substringAfter("path ").trim()
                handleAddPath(path)
                true
            }
            else -> false
        }
    }

    private fun handleDirectModSelection(filename: String) {
        // Search case-insensitive but match exact filename
        modManager.getFilteredMods()
            .find { it.fileName.equals(filename, ignoreCase = true) }
            ?.let {
                modManager.toggleModSelection(it)
                println("${if (modManager.getSelectedMods().contains(it)) "Selected" else "Deselected"}: ${it.fileName}")
            } ?: println("Mod not found: $filename")
    }

    fun handleModSelectionCommand(input: String, pageStart: Int) {
        when {
            input.startsWith("select ") -> {
                val selector = input.substringAfter("select ").trim()

                // Try number selection first
                selector.toIntOrNull()?.let { num ->
                    if (num in 1..pageSize) {
                        val mods = modManager.getFilteredMods()
                        val index = pageStart + num - 1
                        if (index in mods.indices) {
                            modManager.toggleModSelection(mods[index])
                        }
                    }
                } ?: handleDirectModSelection(selector)
            }
            input.startsWith("filter ") -> {
                val filter = input.substringAfter("filter ")
                modManager.filterMods(filter)
                currentPage = 0
            }
        }
    }

    fun handleCustomPathCommand(input: String) {
        when {
            input.startsWith("path ") || input.startsWith("add ") -> {
                val pathStr = input.substringAfter(" ").trim()
                handleAddPath(pathStr)
            }
            input.startsWith("remove ") -> {
                val num = input.substringAfter("remove ").toIntOrNull()
                if (num != null && modManager.removeCustomPath(num - 1)) {
                    println("Removed custom path #$num")
                } else {
                    println("Invalid path number")
                }
            }
        }
    }

    private fun handleAddPath(pathStr: String) {
        val path = File(pathStr)
        if (modManager.addCustomPath(path)) {
            println("Added custom path: $pathStr")
        } else {
            println("Invalid path or directory does not exist: $pathStr")
        }
    }

    private fun handleMerge() {
        if (modManager.getSelectedMods().size < 2) {
            println("Please select at least two mods to merge")
            return
        }

        println("\nStarting merge with ${modManager.getSelectedMods().size} mods...")

        kotlinx.coroutines.runBlocking {
            when (val result = modManager.performMerge()) {
                is MergeResult.Success -> {
                    println("\nMerge completed successfully!")
                    if (result.warnings.isNotEmpty()) {
                        println("\nWarnings during merge:")
                        result.warnings.forEach { warning ->
                            println("- $warning")
                        }
                    }
                }
                is MergeResult.Failure -> {
                    println("\nMerge failed: ${result.error}")
                }
            }
        }
    }
}