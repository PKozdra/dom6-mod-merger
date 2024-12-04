package com.dominions.modmerger

import com.dominions.modmerger.core.ModMerger
import com.dominions.modmerger.core.mapping.IdMapper
import com.dominions.modmerger.core.parsing.ModParser
import com.dominions.modmerger.core.processing.EntityProcessor
import com.dominions.modmerger.core.scanning.ModScanner
import com.dominions.modmerger.core.writing.*
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
            setupGui()
            startGuiApplication(components)
        } else {
            warn("Command line mode is no longer supported", useDispatcher = true)
            exitProcess(1)
        }
    }

    private fun setupGui() {
        try {
            debug("Setting up GUI look and feel")
            FlatLightLaf.setup()
            UIManager.setLookAndFeel(FlatLightLaf())
        } catch (e: Exception) {
            error("Failed to initialize GUI look and feel", e, useDispatcher = true)
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

        // Core components
        debug("Initializing core components")
        val entityProcessor = EntityProcessor()
        val modParser = ModParser(entityProcessor = entityProcessor)
        val mapper = IdMapper()
        val scanner = ModScanner(modParser)

        // Writers
        debug("Setting up writers")
        val contentWriter = ModContentWriter(entityProcessor = entityProcessor)
        val resourceCopier = ModResourceCopier()
        val headerWriter = ModHeaderWriter()

        val writer = ModWriter(
            contentWriter = contentWriter,
            resourceCopier = resourceCopier,
            headerWriter = headerWriter
        )

        // Create service
        debug("Creating ModMerger service")
        val modMerger = ModMerger(
            scanner = scanner,
            mapper = mapper,
            writer = writer,
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