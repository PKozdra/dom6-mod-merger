// src/main/kotlin/com/dominions/modmerger/Main.kt
package com.dominions.modmerger

import com.dominions.modmerger.core.ModMergerService
import com.dominions.modmerger.core.mapping.IdMapper
import com.dominions.modmerger.core.parsing.*
import com.dominions.modmerger.core.processing.EntityProcessor
import com.dominions.modmerger.core.scanning.ModScanner
import com.dominions.modmerger.core.writing.ModContentWriter
import com.dominions.modmerger.core.writing.ModHeaderWriter
import com.dominions.modmerger.core.writing.ModResourceCopier
import com.dominions.modmerger.core.writing.ModWriter
import com.dominions.modmerger.domain.LogDispatcher
import com.dominions.modmerger.domain.ModOutputConfig
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

    if (useGui) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val gamePathsManager = GamePathsManager()
    val fileSystem = FileSystem(gamePathsManager)

    val entityProcessor = EntityProcessor()

    val lineTypeDetector = LineTypeDetector(entityProcessor)
    val spellBlockParser = SpellBlockParser()
    val entityParser = EntityParser()
    val eventParser = EventParser()



    val modParser = ModParser(
        spellBlockParser = spellBlockParser,
        entityParser = entityParser,
        eventParser = eventParser,
        lineTypeDetector = lineTypeDetector
    )

    val logDispatcher = LogDispatcher()

    val mapper = IdMapper(logDispatcher)
    val scanner = ModScanner(modParser)

    val contentWriter = ModContentWriter(
        entityProcessor = entityProcessor,
        logDispatcher = logDispatcher
    )

    val resourceCopier = ModResourceCopier(
        logDispatcher = logDispatcher
    )

    val headerWriter = ModHeaderWriter(
        logDispatcher = logDispatcher
    )

    val writer = ModWriter(
        fileSystem = fileSystem,
        logDispatcher = logDispatcher,
        contentWriter = contentWriter,
        resourceCopier = resourceCopier,
        headerWriter = headerWriter
    )

    val modOutputConfig = ModOutputConfig(
        modName = "merged_mod",
        gamePathsManager = gamePathsManager
    )

    val modMergerService = ModMergerService(
        scanner = scanner,
        mapper = mapper,
        writer = writer,
        config = modOutputConfig,
        fileSystem = fileSystem,
        logDispatcher = logDispatcher
    )

    val application = if (useGui) {
        FlatLightLaf.setup()
        GuiApplication(modMergerService, fileSystem, gamePathsManager, logDispatcher)
    } else {
        ConsoleApplication(modMergerService, fileSystem)
    }

    application.run()
}