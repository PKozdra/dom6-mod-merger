// src/main/kotlin/com/dominions/modmerger/core/ModMergerService.kt
package com.dominions.modmerger.core

import com.dominions.modmerger.MergeResult
import com.dominions.modmerger.core.mapping.IdMapper
import com.dominions.modmerger.core.parsing.ModParser
import com.dominions.modmerger.core.scanning.ModScanner
import com.dominions.modmerger.core.writing.ModWriter
import com.dominions.modmerger.domain.EntityType
import com.dominions.modmerger.domain.LogDispatcher
import com.dominions.modmerger.domain.LogLevel
import com.dominions.modmerger.domain.ModFile
import com.dominions.modmerger.infrastructure.FileSystem
import mu.KLogger
import mu.KotlinLogging

class ModMergerService(
    private val parser: ModParser,
    private val scanner: ModScanner,
    private val mapper: IdMapper,
    private val writer: ModWriter,
    private val fileSystem: FileSystem,
    private val logDispatcher: LogDispatcher
) {

    suspend fun processMods(modFiles: List<ModFile>): MergeResult {
        return try {
            log(LogLevel.INFO, "Scanning mod files...")
            val modDefinitions = scanner.scanMods(modFiles)
            log(LogLevel.INFO, "Finished scanning mods. Total mods scanned: ${modDefinitions.size}")

            modDefinitions.forEach { (name, def) ->
                EntityType.entries.forEach { type ->
                    val ids = def.getDefinition(type).definedIds
                    if (ids.isNotEmpty()) {
                        log(LogLevel.INFO, "Mod $name contains ${ids.size} $type definitions.")
                        log(LogLevel.DEBUG, "$type definitions in $name: $ids")
                    }
                }
            }

            log(LogLevel.INFO, "Mapping IDs...")
            val mappedDefinitions = mapper.createMappings(modDefinitions)
            log(LogLevel.INFO, "Finished mapping IDs.")

            log(LogLevel.INFO, "Generating merged mod...")
            val warnings = writer.writeMergedMod(mappedDefinitions)

            val outputPath = fileSystem.getOutputFile().absolutePath
            log(LogLevel.INFO, "Merged mod saved to: $outputPath")

            MergeResult.Success(warnings)
        } catch (e: Exception) {
            log(LogLevel.ERROR, "Failed to process mods: ${e.message}")
            MergeResult.Failure(e.message ?: "Unknown error occurred")
        }
    }

    private fun log(level: LogLevel, message: String) {
        logDispatcher.log(level, message)
    }
}
