package com.dominions.modmerger.core

import com.dominions.modmerger.MergeResult
import com.dominions.modmerger.core.mapping.IdMapper
import com.dominions.modmerger.core.scanning.ModScanner
import com.dominions.modmerger.core.writing.ModWriter
import com.dominions.modmerger.core.writing.config.ModOutputConfig
import com.dominions.modmerger.domain.*
import com.dominions.modmerger.infrastructure.FileSystem
import mu.KotlinLogging

class ModMergerService(
    private val scanner: ModScanner,
    private val mapper: IdMapper,
    private val writer: ModWriter,
    private val config: ModOutputConfig,
    private val fileSystem: FileSystem,
    private val logDispatcher: LogDispatcher
) : ModMerger {

    private val logger = KotlinLogging.logger {}

    override suspend fun mergeMods(modFiles: List<ModFile>): MergeResult {
        try {
            // Scan mods
            log(LogLevel.INFO, "Scanning mod files...")
            val modDefinitions = scanner.scanMods(modFiles)
            log(LogLevel.INFO, "Finished scanning mods. Total mods scanned: ${modDefinitions.size}")

            // Log definitions
            logDefinitions(modDefinitions)

            // Map IDs
            log(LogLevel.INFO, "Mapping IDs...")
            val mappedDefinitions = mapper.createMappings(modDefinitions)
            logMappings(mappedDefinitions)
            log(LogLevel.INFO, "Finished mapping IDs.")

            // Write merged mod
            log(LogLevel.INFO, "Generating merged mod...")
            return when (val result = writer.writeMergedMod(mappedDefinitions, config)) {
                is MergeResult.Success -> {
                    val outputPath = fileSystem.getOutputFile(config.modName).absolutePath
                    log(LogLevel.INFO, "Merged mod saved to: $outputPath")
                    result
                }

                is MergeResult.Failure -> {
                    log(LogLevel.ERROR, "Failed to write merged mod: ${result.error}")
                    result
                }
            }
        } catch (e: Exception) {
            log(LogLevel.ERROR, "Failed to process mods: ${e.message}")
            return MergeResult.Failure(e.message ?: "Unknown error occurred")
        }
    }

    private fun logDefinitions(modDefinitions: Map<String, ModDefinition>) {
        modDefinitions.forEach { (name, def) ->
            val allDefs = def.getAllDefinitions()
            allDefs.forEach { (type, ids) ->
                if (ids.isNotEmpty()) {
                    log(LogLevel.INFO, "Mod $name contains ${ids.size} $type definitions.")
                    log(LogLevel.DEBUG, "$type definitions in $name: ${ids.joinToString()}")
                }
            }
        }
    }

    private fun logMappings(mappedDefinitions: Map<String, MappedModDefinition>) {
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
    }

    private fun log(level: LogLevel, message: String) {
        // Log to dispatcher
        logDispatcher.log(level, message)

        // Log using KotlinLogging with corresponding levels
        when (level) {
            LogLevel.TRACE -> logger.trace { message }
            LogLevel.DEBUG -> logger.debug { message }
            LogLevel.INFO -> logger.info { message }
            LogLevel.WARN -> logger.warn { message }
            LogLevel.ERROR -> logger.error { message }
            // if level is not recognized, log as info
            else -> logger.info { message }
        }
    }
}