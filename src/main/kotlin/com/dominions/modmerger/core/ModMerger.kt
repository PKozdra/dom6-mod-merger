package com.dominions.modmerger.core

import com.dominions.modmerger.core.mapping.IdMapper
import com.dominions.modmerger.core.scanning.ModScanner
import com.dominions.modmerger.core.writing.ModWriter
import com.dominions.modmerger.core.writing.config.ModOutputConfig
import com.dominions.modmerger.domain.MappedModDefinition
import com.dominions.modmerger.domain.MergeResult
import com.dominions.modmerger.domain.ModDefinition
import com.dominions.modmerger.domain.ModFile
import com.dominions.modmerger.infrastructure.FileSystem
import com.dominions.modmerger.infrastructure.Logging

class ModMerger(
    private val scanner: ModScanner,
    private val mapper: IdMapper,
    private val writer: ModWriter,
    private val config: ModOutputConfig,
    private val fileSystem: FileSystem
) : Logging {

    suspend fun mergeMods(modFiles: List<ModFile>): MergeResult {
        var modDefinitions: Map<String, ModDefinition>? = null
        var mappedDefinitions: Map<String, MappedModDefinition>? = null

        try {
            // Scan mods
            info("Scanning mod files...")
            modDefinitions = scanner.scanMods(modFiles)
            info("Finished scanning mods. Total mods scanned: ${modDefinitions.size}")

            // Log definitions
            logDefinitions(modDefinitions)

            // Map IDs
            info("Mapping IDs...")
            mappedDefinitions = mapper.createMappings(modDefinitions)
            logMappings(mappedDefinitions)
            info("Finished mapping IDs.")

            // Write merged mod
            info("Generating merged mod...")
            return when (val result = writer.writeMergedMod(mappedDefinitions, config)) {
                is MergeResult.Success -> {
                    val outputPath = fileSystem.getOutputFile(config.modName).absolutePath
                    info("Merged mod saved to: $outputPath")
                    result
                }

                is MergeResult.Failure -> {
                    error("Failed to write merged mod: ${result.error}")
                    result
                }
            }
        } catch (e: Exception) {
            //error("Failed to process mods: ${e.message}", e)
            return MergeResult.Failure(e.message ?: "Unknown error occurred")
        } finally {
            // Clean up resources
            modDefinitions?.values?.forEach { it.cleanup() }
            mappedDefinitions?.values?.forEach { it.cleanup() }
            System.gc() // Request garbage collection after large operation
        }
    }

    private fun logDefinitions(modDefinitions: Map<String, ModDefinition>) {
        modDefinitions.forEach { (name, def) ->
            val allDefs = def.getAllDefinitions()
            allDefs.forEach { (type, ids) ->
                if (ids.isNotEmpty()) {
                    info("Mod $name contains ${ids.size} $type definitions.")
                    debug("$type definitions in $name: ${ids.joinToString()}")
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
                    debug("Mappings for $type in $name: $changedMappings")
                }
            }
        }
    }
}