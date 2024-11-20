// src/main/kotlin/com/dominions/modmerger/Main.kt
package com.dominions.modmerger

import com.dominions.modmerger.config.AppConfig
import com.dominions.modmerger.core.ModMergerService
import com.dominions.modmerger.core.scanning.DefaultModScanner
import com.dominions.modmerger.core.parsing.ModParser
import com.dominions.modmerger.core.mapping.IdMapper
import com.dominions.modmerger.core.writing.ModWriter
import com.dominions.modmerger.infrastructure.FileSystem

fun main() {
    val config = AppConfig.load()
    val fileSystem = FileSystem()
    val parser = ModParser()
    val scanner = DefaultModScanner(parser)
    val mapper = IdMapper(emptyMap()) // Will be populated later
    val writer = ModWriter()

    val modMergerService = ModMergerService(parser, scanner, mapper, writer)

    Application(modMergerService, fileSystem).run()
}