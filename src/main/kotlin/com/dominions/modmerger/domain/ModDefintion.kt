// src/main/kotlin/com/dominions/modmerger/domain/ModDefinition.kt
package com.dominions.modmerger.domain

import com.dominions.modmerger.infrastructure.Logging

/**
 * Represents a mod's definition, including all its entity IDs and definitions.
 * ID uniqueness is handled by IdMapper using a first-come-first-served strategy:
 * - First mod to use an ID keeps it
 * - Subsequent mods that try to use the same ID get remapped
 */
data class ModDefinition(
    val modFile: ModFile,
    var name: String = "",
    val version: String = "",
    private val definitions: MutableMap<EntityType, EntityDefinition> = mutableMapOf()
) : Logging {
    init {
        // Initialize with sorted EntityTypes
        EntityType.entries
            .sortedBy { it.name }
            .forEach { type ->
                definitions[type] = type.createDefinition()
            }
    }

    fun getDefinition(type: EntityType): EntityDefinition =
        // get entity defintion or throw
        definitions[type] ?: throw IllegalArgumentException("No definition found for entity type: $type")

    fun addDefinedId(type: EntityType, id: Long) {
        trace("Adding defined ID $id for entity type $type", useDispatcher = false)
        getDefinition(type).addDefinedId(id)
    }

    fun addVanillaEditedId(type: EntityType, id: Long) {
        trace("Adding vanilla edited ID $id for entity type $type", useDispatcher = false)
        getDefinition(type).addVanillaEditedId(id)
    }

    fun addImplicitDefinition(type: EntityType) {
        trace("Adding implicit definition for entity type $type", useDispatcher = false)
        getDefinition(type).incrementImplicitDefinitions()
    }

    fun cleanup() {
        trace("Cleaning up definitions", useDispatcher = false)
        definitions.clear()
    }

    fun getAllDefinitions(): Map<EntityType, Set<Long>> =
        definitions.entries
            .sortedBy { it.key.name }
            .associate { (type, def) ->
                type to def.definedIds
            }
}