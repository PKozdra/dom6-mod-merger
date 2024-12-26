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

        // First process explicit IDs
        EntityType.entries.forEach { type ->
            processExplicitIds(type, modDef, modName, idManager, mappedDef)
        }

        // Then handle implicit definitions and update name mappings
        EntityType.entries.forEach { type ->
            processImplicitDefinitions(type, modDef, modName, idManager, mappedDef)
        }

        return mappedDef
    }

    private fun processImplicitDefinitions(
        type: EntityType,
        modDef: ModDefinition,
        modName: String,
        idManager: IdManager,
        mappedDef: MappedModDefinition
    ) {
        val entityDef = modDef.getDefinition(type)
        val implicitCount = entityDef.getImplicitDefinitionCount()

        if (implicitCount > 0) {
            trace("Processing $implicitCount implicit definitions for $type in $modName")

            val results = idManager.registerImplicitIds(type, implicitCount, modName)

            // Process results and update name mappings
            val implicitNameMappings = entityDef.getImplicitNameMappings()
            results.forEachIndexed { index, result ->
                when (result) {
                    is IdRegistrationResult.Registered -> {

                        // Record the final assigned ID for that implicit index
                        entityDef.setImplicitAssignedId(index, result.id)

                        // Update any names associated with this implicit definition
                        implicitNameMappings[index]?.forEach { name ->
                            entityDef.updateNameToId(name, result.id)
                            // debug("Updated implicit name mapping: $name -> ${result.id}")
                        }
                    }
                    is IdRegistrationResult.Error -> {
                        error("Failed to assign ID for implicit $type in $modName: ${result.message}")
                        throw IdMappingException("Failed to assign ID for implicit $type in $modName: ${result.message}")
                    }
                    else -> {
                        error("Unexpected result type for implicit ID assignment: $result")
                        throw IdMappingException("Unexpected result type for implicit ID assignment: $result")
                    }
                }
            }
        }
    }

    private fun processExplicitIds(
        type: EntityType,
        modDef: ModDefinition,
        modName: String,
        idManager: IdManager,
        mappedDef: MappedModDefinition
    ) {
        val entityDef = modDef.getDefinition(type)
        val sortedIds = entityDef.definedIds.sorted()
        if (sortedIds.isEmpty()) return

        var baseId: Long? = null
        var lastNewId: Long? = null

        sortedIds.forEach { originalId ->
            val requestedId = if (baseId != null && lastNewId != null) {
                lastNewId + (originalId - sortedIds[sortedIds.indexOf(originalId) - 1])
            } else null

            when (val result = idManager.registerOrRemapId(type, originalId, modName, requestedId)) {
                is IdRegistrationResult.Registered -> {
                    baseId = null
                    lastNewId = null
                    trace("Using original ID for $modName: $type $originalId")
                }

                is IdRegistrationResult.Remapped -> {
                    if (baseId == null) {
                        baseId = result.newId
                    }
                    lastNewId = result.newId
                    mappedDef.addMapping(type, originalId, result.newId)
                    trace("Remapped ID for $modName: $type $originalId -> ${result.newId}")
                }

                is IdRegistrationResult.VanillaConflict -> {
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