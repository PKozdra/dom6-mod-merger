package com.dominions.modmerger.core.mapping

import com.dominions.modmerger.domain.*
import com.dominions.modmerger.infrastructure.Logging

/**
 * Maps IDs for mod entities while preserving sequential relationships.
 * Uses a first-come-first-served strategy - first mod to use an ID keeps it.
 * When remapping is needed, maintains relative ordering of IDs within each mod.
 */
class IdMapper : Logging {

    /**
     * Creates ID mappings for a set of mods.
     * Processes mods in alphabetical order for consistency.
     */
    @Throws(IdMappingException::class)
    fun createMappings(
        modDefinitions: Map<String, ModDefinition>,
        idManager: IdManager
    ): Map<String, MappedModDefinition> {
        if (modDefinitions.isEmpty()) return emptyMap()

        return try {
            modDefinitions.entries
                .sortedBy { it.key }
                .associate { (modName, modDef) ->
                    info("Processing mod: $modName")
                    modName to processMod(modDef, modName, idManager)
                }
        } catch (e: IdMappingException) {
            throw e
        }
    }

    /**
     * Processes a single mod, mapping its IDs while preserving sequential relationships.
     */
    private fun processMod(
        modDef: ModDefinition,
        modName: String,
        idManager: IdManager
    ): MappedModDefinition {
        val mappedDef = MappedModDefinition(modDef.modFile)

        // Process each entity type
        EntityType.entries.forEach { type ->
            processEntityType(type, modDef, modName, idManager, mappedDef)
        }

        return mappedDef
    }

    /**
     * Processes all IDs for a specific entity type within a mod.
     * Maintains sequential relationships by processing IDs in order.
     */
    private fun processEntityType(
        type: EntityType,
        modDef: ModDefinition,
        modName: String,
        idManager: IdManager,
        mappedDef: MappedModDefinition
    ) {
        val entityDef = modDef.getDefinition(type)
        val sortedIds = entityDef.definedIds.sorted()
        if (sortedIds.isEmpty()) return

        // Track base ID for maintaining sequential relationships
        var baseId: Long? = null
        var lastNewId: Long? = null

        // Process all IDs in order
        sortedIds.forEach { originalId ->
            // Request next sequential ID if we're in a sequence
            val requestedId = if (baseId != null && lastNewId != null) {
                lastNewId + (originalId - sortedIds[sortedIds.indexOf(originalId) - 1])
            } else null

            when (val result = idManager.registerOrRemapId(type, originalId, modName, requestedId)) {
                is IdRegistrationResult.Registered -> {
                    // Reset sequence tracking for direct registrations
                    baseId = null
                    lastNewId = null
                    trace("Using original ID for $modName: $type $originalId")
                }
                is IdRegistrationResult.Remapped -> {
                    // Start or continue sequence
                    if (baseId == null) {
                        baseId = result.newId
                    }
                    lastNewId = result.newId
                    mappedDef.addMapping(type, originalId, result.newId)
                    trace("Remapped ID for $modName: $type $originalId -> ${result.newId}")
                }
                is IdRegistrationResult.VanillaConflict -> {
                    // Reset sequence tracking for vanilla conflicts
                    baseId = null
                    lastNewId = null
                    warn("Possible conflict, vanilla ID processed for $modName: $type ${result.id}")
                }
                is IdRegistrationResult.Error -> {
                    throw IdMappingException(
                        "Failed to map ID $originalId for $type in $modName: ${result.message}"
                    )
                }
            }
        }
    }
}

/**
 * Thrown when ID mapping fails.
 */
class IdMappingException(message: String) : Exception(message)