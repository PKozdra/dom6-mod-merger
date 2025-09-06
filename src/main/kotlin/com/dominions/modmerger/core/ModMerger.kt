package com.dominions.modmerger.core

import com.dominions.modmerger.constants.ModRanges
import com.dominions.modmerger.core.mapping.IdManager
import com.dominions.modmerger.core.mapping.IdMapper
import com.dominions.modmerger.core.parsing.ModParser
import com.dominions.modmerger.core.processing.EntityProcessor
import com.dominions.modmerger.core.processing.SpellBlockProcessor
import com.dominions.modmerger.core.scanning.ModScanner
import com.dominions.modmerger.core.writing.ModContentWriter
import com.dominions.modmerger.core.writing.ModHeaderWriter
import com.dominions.modmerger.core.writing.ModResourceCopier
import com.dominions.modmerger.core.writing.ModWriter
import com.dominions.modmerger.core.writing.config.ModOutputConfig
import com.dominions.modmerger.domain.EntityType
import com.dominions.modmerger.domain.MappedModDefinition
import com.dominions.modmerger.domain.MergeResult
import com.dominions.modmerger.domain.ModDefinition
import com.dominions.modmerger.domain.ModFile
import com.dominions.modmerger.domain.ModGroupHandler
import com.dominions.modmerger.gamedata.GameDataProvider
import com.dominions.modmerger.infrastructure.FileSystem
import com.dominions.modmerger.infrastructure.Logging

class ModMerger(
    private var config: ModOutputConfig,
    private val fileSystem: FileSystem,
    private val groupHandler: ModGroupHandler,
    private val gameDataProvider: GameDataProvider
) : Logging {

    fun updateConfig(newConfig: ModOutputConfig) {
        this.config = newConfig
    }

    suspend fun mergeMods(modFiles: List<ModFile>): MergeResult {
        var modDefinitions: Map<String, ModDefinition>? = null
        var mappedDefinitions: Map<String, MappedModDefinition>? = null

        try {
            // Initialize game data first
            info("Initializing game data provider...")
            gameDataProvider.initialize()
            info("Game data provider initialized.")

            // Create fresh IdManager for this merge operation
            info("Initializing ID manager for merge operation...")
            val idManager = IdManager.createFromModRanges()
            idManager.reset()
            info("ID manager initialized.")

            // Core components
            debug("Initializing core components...")
            val entityProcessor = EntityProcessor(gameDataProvider = gameDataProvider)
            val spellBlockProcessor = SpellBlockProcessor(gameDataProvider)
            val modParser = ModParser(entityProcessor = entityProcessor)
            val mapper = IdMapper()
            val scanner = ModScanner(modParser)
            debug("Core components initialized.")

            // Writers
            debug("Setting up writers...")
            val contentWriter = ModContentWriter(entityProcessor = entityProcessor, spellBlockProcessor = spellBlockProcessor)
            val resourceCopier = ModResourceCopier()
            val headerWriter = ModHeaderWriter()

            val writer = ModWriter(
                contentWriter = contentWriter,
                resourceCopier = resourceCopier,
                headerWriter = headerWriter
            )
            debug("Writers set up.")

            // Set up groups
            debug("Setting up mod groups...")
            val processedFiles = groupHandler.processFiles(modFiles)
            debug("Mod groups set up.")

            // Scan mods
            info("Scanning mod files...")
            modDefinitions = scanner.scanMods(processedFiles)
            info("Finished scanning mods. Total mods scanned: ${modDefinitions.size}")

            // Log definitions
            logDefinitions(modDefinitions)

            // Map IDs
            info("Mapping IDs...")
            mappedDefinitions = mapper.createMappings(modDefinitions, idManager)
            val resourceMappedDefinitions = buildResourceMappings(mappedDefinitions)
            logMappings(mappedDefinitions)
            info("Finished mapping IDs.")

            // Freeze all definitions after mapping is complete
            info("Freezing definitions...")
            modDefinitions.values.forEach { it.freeze() }
            mappedDefinitions.values.forEach { it.freeze() }
            resourceMappedDefinitions.values.forEach { it.freeze() }
            info("Definitions frozen.")

            // Log possible vanilla conflicts
            val vanillaConflicts = analyzeVanillaConflicts(modDefinitions)
            logVanillaConflictReport(vanillaConflicts)

            // Write merged mod
            info("Generating merged mod...")
            return when (val result = writer.writeMergedMod(
                mappedDefinitions = mappedDefinitions,
                modDefinitions = modDefinitions,
                config = config,
                resourceMappedDefinitions = resourceMappedDefinitions
            )) {
                is MergeResult.Success -> {
                    val outputPath = fileSystem.getOutputFile(config.modName, config.directory).absolutePath
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
            //info("Starting forced GC...")
            //val gcStart = System.currentTimeMillis()
            System.gc() // Request garbage collection after large operation
            //val gcEnd = System.currentTimeMillis()
            //info("Forced GC took ${gcEnd - gcStart} ms")
        }
    }

    private fun buildResourceMappings(
        mappedDefinitions: Map<String, MappedModDefinition>
    ): Map<String, MappedModDefinition> {
        val resourceMappedDefinitions = mutableMapOf<String, MappedModDefinition>()

        mappedDefinitions.forEach { (name, mappedDef) ->
            if (name.startsWith("_Combined_")) {
                // For combined files, add original files for resource copying
                groupHandler.getSourceFiles(name).forEach { originalFile ->
                    resourceMappedDefinitions[originalFile.name] = MappedModDefinition(originalFile)
                }
            } else {
                // For non-grouped files, use original mapping
                resourceMappedDefinitions[name] = mappedDef
            }
        }

        return resourceMappedDefinitions
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
        debug("\nMapping Log Output:")
        mappedDefinitions.forEach { (name, mapped) ->
            val mappingsByType = mapped.getMappingsByType()
            mappingsByType.forEach { (type, typeMapping) ->
                val changedMappings = typeMapping.entries
                    .filter { (original, new) -> original != new }
                    .joinToString { (original, new) -> "$original→$new" }

                if (changedMappings.isNotEmpty()) {
                    val mappingsNumber = typeMapping.size
                    debug("$mappingsNumber Mappings for $type in $name: $changedMappings")
                }
            }
        }
    }

    data class VanillaConflict(
        val entityType: EntityType,
        val id: Long,
        val mods: Set<String>
    )

    private fun analyzeVanillaConflicts(modDefinitions: Map<String, ModDefinition>): List<VanillaConflict> {
        val conflicts = mutableListOf<VanillaConflict>()

        EntityType.entries.forEach { type ->
            val vanillaEdits = mutableMapOf<Long, MutableSet<String>>()

            modDefinitions.forEach { (modName, def) ->
                def.getAllVanillaEditedIds()[type]?.forEach { id ->
                    vanillaEdits.getOrPut(id) { mutableSetOf() }.add(modName)
                }
            }

            vanillaEdits
                .filter { it.value.size > 1 }
                .forEach { (id, mods) ->
                    conflicts.add(VanillaConflict(type, id, mods))
                }
        }

        return conflicts.sortedWith(
            compareBy({ it.entityType.name }, { it.id })
        )
    }

    private fun logVanillaConflictReport(conflicts: List<VanillaConflict>) {
        if (conflicts.isEmpty()) {
            info("No vanilla content conflicts found.")
            return
        }

        warn("Found ${conflicts.size} vanilla content conflicts:")

        conflicts.groupBy { it.entityType }.forEach { (type, typeConflicts) ->
            // Group conflicts by the set of mods that modify them
            val conflictsByMods = typeConflicts.groupBy { it.mods }

            warn("$type conflicts:")
            conflictsByMods.forEach { (mods, conflicts) ->
                val idRanges = conflicts.map { it.id }
                    .sorted()
                    .fold(mutableListOf<Pair<Long, Long>>()) { ranges, id ->
                        if (ranges.isEmpty() || ranges.last().second + 1 != id) {
                            ranges.add(id to id)
                        } else {
                            ranges[ranges.lastIndex] = ranges.last().first to id
                        }
                        ranges
                    }

                val idRangesStr = idRanges.joinToString(", ") { (start, end) ->
                    if (start == end) "$start" else "$start-$end"
                }

                warn("  IDs $idRangesStr modified by: ${mods.joinToString()}")
            }
        }
    }
}
