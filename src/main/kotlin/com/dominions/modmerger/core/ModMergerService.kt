// src/main/kotlin/com/dominions/modmerger/core/ModMergerService.kt
package com.dominions.modmerger.core

import com.dominions.modmerger.MergeResult
import com.dominions.modmerger.core.mapping.IdMapper
import com.dominions.modmerger.core.scanning.ModScanner
import com.dominions.modmerger.core.writing.ModWriter
import com.dominions.modmerger.domain.*
import com.dominions.modmerger.infrastructure.FileSystem

class ModMergerService(
    private val scanner: ModScanner,
    private val mapper: IdMapper,
    private val writer: ModWriter,
    private val config: ModOutputConfig,
    private val fileSystem: FileSystem,
    private val logDispatcher: LogDispatcher
) : ModMerger {
    override suspend fun mergeMods(modFiles: List<ModFile>): MergeResult {
        return try {
            log(LogLevel.INFO, "Scanning mod files...")
            val modDefinitions = scanner.scanMods(modFiles)
            log(LogLevel.INFO, "Finished scanning mods. Total mods scanned: ${modDefinitions.size}")

            modDefinitions.forEach { (name, def) ->
                val allDefs = def.getAllDefinitions()
                allDefs.forEach { (type, ids) ->
                    if (ids.isNotEmpty()) {
                        log(LogLevel.INFO, "Mod $name contains ${ids.size} $type definitions.")
                        log(LogLevel.DEBUG, "$type definitions in $name: ${ids.joinToString()}")
                    }
                }
            }

            log(LogLevel.INFO, "Mapping IDs...")
            val mappedDefinitions = mapper.createMappings(modDefinitions)

            // In ModMergerService
            mappedDefinitions.forEach { (name, mapped) ->
                val mappingsByType = mapped.getMappingsByType()
                mappingsByType.forEach { (type, typeMapping) ->
                    val changedMappings = typeMapping.entries
                        .filter { (original, new) -> original != new }
                        .joinToString { (original, new) -> "$original→$new" }

                    if (changedMappings.isNotEmpty()) {
                        log(LogLevel.DEBUG, "Mappings for $type in $name: $changedMappings")
                    }
                }
            }

            log(LogLevel.INFO, "Finished mapping IDs.")

            log(LogLevel.INFO, "Generating merged mod...")
            val warnings = writer.writeMergedMod(mappedDefinitions, config)

            val outputPath = fileSystem.getOutputFile(config.modName).absolutePath
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
