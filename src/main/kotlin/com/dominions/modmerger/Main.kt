// src/main/kotlin/com/dominions/modmerger/Main.kt
package com.dominions.modmerger

import com.dominions.modmerger.core.ModMergerService
import com.dominions.modmerger.core.mapping.IdMapper
import com.dominions.modmerger.core.parsing.*
import com.dominions.modmerger.core.scanning.DefaultModScanner
import com.dominions.modmerger.core.writing.ModWriter
import com.dominions.modmerger.domain.LogDispatcher
import com.dominions.modmerger.infrastructure.FileSystem
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

    val fileSystem = FileSystem()
    val lineTypeDetector = LineTypeDetector()
    val spellBlockParser = SpellBlockParser()
    val entityParser = EntityParser()
    val eventParser = EventParser()

    val modParser = ModParser(
        spellBlockParser = spellBlockParser,
        entityParser = entityParser,
        eventParser = eventParser,
        lineTypeDetector = lineTypeDetector
    )
    val scanner = DefaultModScanner(modParser)
    val mapper = IdMapper(emptyMap())
    val writer = ModWriter(fileSystem)
    val logDispatcher = LogDispatcher()

    val modMergerService = ModMergerService(modParser, scanner, mapper, writer, fileSystem, logDispatcher)

    val application = if (useGui) {
        FlatLightLaf.setup()
        GuiApplication(modMergerService, fileSystem, logDispatcher)
    } else {
        ConsoleApplication(modMergerService, fileSystem)
    }

    application.run()
}