package com.dominions.modmerger

import com.dominions.modmerger.console.ConsoleMenu
import com.dominions.modmerger.console.ConsoleModManager
import com.dominions.modmerger.core.ModMerger
import com.dominions.modmerger.core.writing.config.ModOutputConfigManager
import com.dominions.modmerger.domain.ModGroupHandler
import com.dominions.modmerger.domain.ModGroupRegistry
import com.dominions.modmerger.gamedata.Dom6CsvGameDataProvider
import com.dominions.modmerger.infrastructure.FileSystem
import com.dominions.modmerger.infrastructure.GamePathsManager
import com.dominions.modmerger.infrastructure.Logging
import com.dominions.modmerger.ui.ModMergerGui
import com.formdev.flatlaf.FlatLightLaf
import javax.swing.SwingUtilities
import javax.swing.UIManager
import kotlin.system.exitProcess

class ModMergerApplication : Logging {
    fun run(args: Array<String>) {
        if (args.isEmpty()) {
            startGuiMode()
            return
        }

        val components = initialize()

        when (args[0]) {
            "--console" -> {
                info("Starting Console application", useDispatcher = true)
                startConsoleApplication(components)
            }
            "--auto-merge" -> {
                info("Starting auto-merge mode", useDispatcher = true)
                startAutoMergeMode(components, args.drop(1).toTypedArray())
            }
            "--help" -> {
                println("""
                    ModMerger Usage:
                    No arguments     - Start in GUI mode
                    --console       - Start in interactive console mode
                    --auto-merge    - Start in automatic merge mode
                        Options:
                        --mods <paths>     List of complete paths to mod files (required)
                                          Format: ["/path/to/mod1.dm","/path/to/mod2.dm"]
                        --output <name>    Output filename for merged mod (optional)
                                          Default: merged_mod.dm
                        --output-path <dir> Directory to store the merged mod (optional)
                                          Default: Current directory
                    --help          - Show this help message

                    Examples:
                    GUI Mode:
                    java -jar modmerger.jar

                    Console Mode:
                    java -jar modmerger.jar --console

                    Auto-merge Mode:
                    java -jar modmerger.jar --auto-merge \
                        --mods "[/path/to/mod1.dm,/path/to/mod2.dm]" \
                        --output merged.dm \
                        --output-path /path/to/output
                """.trimIndent())
                exitProcess(0)
            }
            else -> {
                error("Unknown argument: ${args[0]}")
                exitProcess(1)
            }
        }
    }

    private fun startGuiMode() {
        info("Starting GUI application", useDispatcher = true)
        val components = initialize()
        if (!setupGui()) {
            error("Failed to initialize GUI", useDispatcher = true)
            exitProcess(1)
        }
        startGuiApplication(components)
    }

    private fun setupGui(): Boolean {
        try {
            debug("Setting up GUI look and feel")
            FlatLightLaf.setup()
            UIManager.setLookAndFeel(FlatLightLaf())
            return true
        } catch (e: Exception) {
            error("Failed to initialize GUI look and feel", e, useDispatcher = true)
            return false
        }
    }

    private fun startGuiApplication(components: ApplicationComponents) {
        debug("Launching GUI")
        SwingUtilities.invokeLater {
            ModMergerGui(
                modMerger = components.modMerger,
                fileSystem = components.fileSystem,
                gamePathsManager = components.gamePathsManager,
                groupRegistry = components.groupRegistry,
                gameDataProvider = components.gameDataProvider
            ).show()
        }
    }

    private fun startConsoleApplication(components: ApplicationComponents) {
        debug("Launching Console interface")
        val modManager = ConsoleModManager(
            modMerger = components.modMerger,
            gamePathsManager = components.gamePathsManager
        )

        ConsoleMenu(modManager).show()
    }

    private fun startAutoMergeMode(components: ApplicationComponents, args: Array<String>) {
        debug("Launching auto-merge mode")
        AutoMergeMode(
            modMerger = components.modMerger,
            gamePathsManager = components.gamePathsManager
        ).run(args)
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            ModMergerApplication().run(args)
        }
    }

    private fun initialize(): ApplicationComponents {
        debug("Initializing ModMerger application")

        // Infrastructure
        val gamePathsManager = GamePathsManager()
        val fileSystem = FileSystem(gamePathsManager)

        // Game data provider
        val dom6DataProvider = Dom6CsvGameDataProvider()

        // Configuration
        debug("Creating configuration")
        val configManager = ModOutputConfigManager(fileSystem, gamePathsManager)
        val defaultConfig = configManager.createDefaultConfig()
        val registry = ModGroupRegistry()
        val groupHandler = ModGroupHandler(registry)

        // Create service
        debug("Creating ModMerger service")
        val modMerger = ModMerger(
            config = defaultConfig,
            fileSystem = fileSystem,
            groupHandler = groupHandler,
            gameDataProvider = dom6DataProvider
        )

        return ApplicationComponents(
            modMerger = modMerger,
            fileSystem = fileSystem,
            gamePathsManager = gamePathsManager,
            groupRegistry = registry,
            gameDataProvider = dom6DataProvider
        )
    }
}