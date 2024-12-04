// src/main/kotlin/com/dominions/modmerger/Main.kt
package com.dominions.modmerger

import com.dominions.modmerger.core.ModMergerService
import com.dominions.modmerger.core.mapping.IdMapper
import com.dominions.modmerger.core.parsing.ModParser
import com.dominions.modmerger.core.processing.EntityProcessor
import com.dominions.modmerger.core.scanning.ModScanner
import com.dominions.modmerger.core.writing.ModContentWriter
import com.dominions.modmerger.core.writing.ModHeaderWriter
import com.dominions.modmerger.core.writing.ModResourceCopier
import com.dominions.modmerger.core.writing.ModWriter
import com.dominions.modmerger.core.writing.config.ModOutputConfigManager
import com.dominions.modmerger.domain.LogDispatcher
import com.dominions.modmerger.infrastructure.FileSystem
import com.dominions.modmerger.infrastructure.GamePathsManager
import com.formdev.flatlaf.FlatLightLaf
import javax.swing.UIManager

fun main(args: Array<String>) {
    val useGui = when {
        args.isEmpty() -> true
        args.size == 1 && args[0] == "--console" -> false
        else -> true
    }

    // Initialize infrastructure components
    val gamePathsManager = GamePathsManager()
    val fileSystem = FileSystem(gamePathsManager)
    val logDispatcher = LogDispatcher()

    // Initialize configuration manager
    val configManager = ModOutputConfigManager(fileSystem, gamePathsManager)
    val defaultConfig = configManager.createDefaultConfig()

    // Initialize core components
    val entityProcessor = EntityProcessor()
    val modParser = ModParser(entityProcessor = entityProcessor)
    val mapper = IdMapper(logDispatcher)
    val scanner = ModScanner(modParser)

    // Initialize writers
    val contentWriter = ModContentWriter(
        entityProcessor = entityProcessor,
        logDispatcher = logDispatcher
    )
    val resourceCopier = ModResourceCopier(logDispatcher = logDispatcher)
    val headerWriter = ModHeaderWriter(logDispatcher = logDispatcher)

    val writer = ModWriter(
        fileSystem = fileSystem,
        logDispatcher = logDispatcher,
        contentWriter = contentWriter,
        resourceCopier = resourceCopier,
        headerWriter = headerWriter
    )

    // Create mod merger service with default configuration
    val modMergerService = ModMergerService(
        scanner = scanner,
        mapper = mapper,
        writer = writer,
        config = defaultConfig,
        fileSystem = fileSystem,
        logDispatcher = logDispatcher
    )

    // Initialize and run appropriate application type
    val application = if (useGui) {
        FlatLightLaf.setup()
        GuiApplication(
            modMergerService = modMergerService,
            fileSystem = fileSystem,
            gamePathsManager = gamePathsManager,
            logDispatcher = logDispatcher
        )
    } else {
        ConsoleApplication(
            modMergerService = modMergerService,
            fileSystem = fileSystem,
        )
    }

    application.run()
}