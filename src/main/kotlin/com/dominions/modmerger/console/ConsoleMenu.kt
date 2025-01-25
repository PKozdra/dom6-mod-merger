package com.dominions.modmerger.console

import com.dominions.modmerger.infrastructure.Logging
import kotlin.system.exitProcess

class ConsoleMenu(
    private val modManager: ConsoleModManager
) : Logging {
    private var currentPage = 0
    private val PAGE_SIZE = 10
    private val commands = ConsoleCommands(modManager, PAGE_SIZE)

    fun show() {
        modManager.loadMods()

        while (true) {
            println("\n=== ModMerger Console ===")
            showMainMenu()

            print("\nEnter choice (1-6) or 'help' for command list: ")

            val input = readlnOrNull()?.trim() ?: continue

            // Try to handle as direct command first
            if (!commands.handleMainMenuCommand(input.lowercase())) {
                // If not a direct command, handle as menu choice
                when (input.lowercase()) {
                    "1" -> showModSelection()
                    "2" -> viewSelectedMods()
                    "3" -> manageCustomPaths()
                    "4" -> startMerge()
                    "5" -> showHelp()
                    "6" -> exitProcess(0)
                    "help" -> showHelp()
                    else -> println("Invalid choice. Type 'help' for available commands.")
                }
            }
        }
    }

    private fun showMainMenu() {
        println("1. Select Mods")
        println("2. View Selected Mods (${modManager.getSelectedMods().size} selected)")
        println("3. Manage Custom Paths (${modManager.getCustomPaths().size} custom paths)")
        println("4. Start Merge")
        println("5. Help")
        println("6. Exit")
    }

    private fun showModSelection() {
        currentPage = 0

        while (true) {
            val mods = modManager.getFilteredMods()
            val pageStart = currentPage * PAGE_SIZE
            val pageEnd = minOf(pageStart + PAGE_SIZE, mods.size)
            val totalPages = (mods.size + PAGE_SIZE - 1) / PAGE_SIZE

            println("\n=== Mod Selection ===")
            if (mods != modManager.getFilteredMods()) {
                println("Filter active: showing ${mods.size} of ${modManager.getFilteredMods().size} mods")
            }
            println("Page ${currentPage + 1} of $totalPages (${mods.size} mods total)")
            println("----------------------------------------")

            mods.subList(pageStart, pageEnd).forEachIndexed { index, mod ->
                val selected = modManager.getSelectedMods().any {
                    it.modFile.file?.absolutePath == mod.modFile.file?.absolutePath
                }
                println("${index + 1}. [${if (selected) "X" else " "}] ${mod.modName}")
                println("   File: ${mod.fileName}")
                println("   Size: ${mod.getFormattedSize()}, Modified: ${mod.getFormattedDate()}")
            }

            print("\nEnter command (type 'help' for commands): ")
            when (val input = readlnOrNull()?.trim()?.lowercase()) {
                null -> continue
                "help" -> showModSelectionHelp()
                "back" -> break
                "clear" -> {
                    modManager.clearFilter()
                    currentPage = 0
                }
                "next" -> {
                    if (currentPage < totalPages - 1) currentPage++
                }
                "prev" -> {
                    if (currentPage > 0) currentPage--
                }
                else -> commands.handleModSelectionCommand(input, pageStart)
            }
        }
    }

    private fun viewSelectedMods() {
        while (true) {
            println("\n=== Selected Mods ===")
            val selectedMods = modManager.getSelectedMods()

            if (selectedMods.isEmpty()) {
                println("No mods selected")
            } else {
                selectedMods.forEachIndexed { index, mod ->
                    println("${index + 1}. ${mod.modName}")
                    println("   File: ${mod.fileName}")
                }
            }

            println("\nCommands:")
            println("remove <number> - Remove a mod from selection")
            println("clear          - Clear all selections")
            println("back           - Return to main menu")
            println("help           - Show available commands")

            print("\nEnter command: ")
            when (val input = readlnOrNull()?.trim()?.lowercase()) {
                null -> continue
                "help" -> showSelectedModsHelp()
                "back" -> break
                "clear" -> modManager.clearSelectedMods()
                else -> {
                    if (input.startsWith("remove ")) {
                        val num = input.substringAfter("remove ").toIntOrNull()
                        if (num != null && num in 1..selectedMods.size) {
                            modManager.removeSelectedMod(num - 1)
                        } else {
                            println("Invalid selection number")
                        }
                    } else if (!commands.handleMainMenuCommand(input)) {
                        println("Invalid command. Type 'help' for available commands.")
                    }
                }
            }
        }
    }

    private fun manageCustomPaths() {
        while (true) {
            println("\n=== Custom Paths ===")
            val paths = modManager.getCustomPaths()

            if (paths.isEmpty()) {
                println("No custom paths added")
            } else {
                paths.forEachIndexed { index, path ->
                    println("${index + 1}. ${path.absolutePath}")
                }
            }

            println("\nCommands:")
            println("path <path>     - Add a custom path")
            println("remove <number> - Remove a custom path")
            println("clear          - Clear all custom paths")
            println("back           - Return to main menu")
            println("help           - Show available commands")

            print("\nEnter command: ")
            when (val input = readlnOrNull()?.trim()?.lowercase()) {
                null -> continue
                "help" -> showCustomPathsHelp()
                "back" -> break
                "clear" -> modManager.clearCustomPaths()
                else -> {
                    if (!commands.handleMainMenuCommand(input)) {
                        commands.handleCustomPathCommand(input)
                    }
                }
            }
        }
    }

    private fun showModSelectionHelp() {
        println("\nAvailable Commands:")
        println("  select <number>    - Select/deselect mod by number")
        println("  select <filename>  - Select mod by filename (e.g., 'select test_mod_1.dm')")
        println("  filter <text>      - Filter mods by name or filename")
        println("  clear             - Clear current filter")
        println("  next              - Next page")
        println("  prev              - Previous page")
        println("  back              - Return to main menu")
        println("  help              - Show this help message")
        println("\nDirect Commands (available from any menu):")
        println("  merge             - Start merge process")
        println("  path <path>       - Add a custom mod path")
        println("\nPress Enter to continue...")
        readlnOrNull()
    }

    private fun showSelectedModsHelp() {
        println("\nAvailable Commands:")
        println("  remove <number>    - Remove a mod from selection")
        println("  clear             - Clear all selections")
        println("  back              - Return to main menu")
        println("  help              - Show this help message")
        println("\nDirect Commands (available from any menu):")
        println("  merge             - Start merge process")
        println("  select <filename> - Select mod by filename")
        println("  path <path>       - Add a custom mod path")
        println("\nPress Enter to continue...")
        readlnOrNull()
    }

    private fun showCustomPathsHelp() {
        println("\nAvailable Commands:")
        println("  path <path>        - Add a custom path")
        println("  remove <number>    - Remove a custom path")
        println("  clear             - Clear all custom paths")
        println("  back              - Return to main menu")
        println("  help              - Show this help message")
        println("\nDirect Commands (available from any menu):")
        println("  merge             - Start merge process")
        println("  select <filename> - Select mod by filename")
        println("\nPress Enter to continue...")
        readlnOrNull()
    }

    private fun showHelp() {
        println("\n=== ModMerger Console Help ===")
        println("Main Menu Options:")
        println("1. Select Mods       - Browse and select mods to merge")
        println("2. View Selected     - View and manage selected mods")
        println("3. Custom Paths      - Add/remove custom mod directories")
        println("4. Start Merge       - Merge selected mods")
        println("5. Help             - Show this help")
        println("6. Exit             - Exit the application")

        println("\nDirect Commands (available from any menu):")
        println("  select <filename>  - Select mod by exact filename")
        println("  merge             - Start merge process")
        println("  path <path>       - Add a custom mod path")

        println("\nGeneral Tips:")
        println("- Commands are case-insensitive")
        println("- Use 'help' in any menu to see available commands")
        println("- Use 'back' to return to the previous menu")
        println("- Filter mods using partial names")

        println("\nPress Enter to continue...")
        readlnOrNull()
    }

    private fun startMerge() {
        commands.handleMainMenuCommand("merge")
        println("\nPress Enter to continue...")
        readlnOrNull()
    }
}