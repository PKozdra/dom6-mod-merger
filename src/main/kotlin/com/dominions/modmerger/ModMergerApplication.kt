package com.dominions.modmerger

import com.dominions.modmerger.core.ModMerger
import com.dominions.modmerger.core.mapping.IdManager
import com.dominions.modmerger.core.mapping.IdMapper
import com.dominions.modmerger.core.parsing.ModParser
import com.dominions.modmerger.core.processing.EntityProcessor
import com.dominions.modmerger.core.scanning.ModScanner
import com.dominions.modmerger.core.writing.ModContentWriter
import com.dominions.modmerger.core.writing.ModHeaderWriter
import com.dominions.modmerger.core.writing.ModResourceCopier
import com.dominions.modmerger.core.writing.ModWriter
import com.dominions.modmerger.core.writing.config.ModOutputConfigManager
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
        val useGui = args.isEmpty() || args.size != 1 || args[0] != "--console"

        val components = initialize()

        if (useGui) {
            info("Starting GUI application", useDispatcher = true)
            if (!setupGui()) {
                error("Failed to initialize GUI", useDispatcher = true)
                exitProcess(1)
            }
            startGuiApplication(components)
        } else {
            warn("Command line mode is no longer supported", useDispatcher = true)
            exitProcess(1)
        }
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
            ).show()
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            ModMergerApplication().run(args)
        }
    }

    private data class ApplicationComponents(
        val modMerger: ModMerger,
        val fileSystem: FileSystem,
        val gamePathsManager: GamePathsManager
    )

    private fun initialize(): ApplicationComponents {
        debug("Initializing ModMerger application")

        // Infrastructure
        val gamePathsManager = GamePathsManager()
        val fileSystem = FileSystem(gamePathsManager)

        // Configuration
        debug("Creating configuration")
        val configManager = ModOutputConfigManager(fileSystem, gamePathsManager)
        val defaultConfig = configManager.createDefaultConfig()

        // Create service
        debug("Creating ModMerger service")
        val modMerger = ModMerger(
            config = defaultConfig,
            fileSystem = fileSystem
        )

        return ApplicationComponents(
            modMerger = modMerger,
            fileSystem = fileSystem,
            gamePathsManager = gamePathsManager
        )
    }
}