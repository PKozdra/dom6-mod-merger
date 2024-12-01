// src/main/kotlin/com/dominions/modmerger/domain/ModDefinition.kt
package com.dominions.modmerger.domain

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
) {
    init {
        // Initialize with sorted EntityTypes
        EntityType.entries
            .sortedBy { it.name }
            .forEach { type ->
                definitions[type] = type.createDefinition()
            }
    }

    fun getDefinition(type: EntityType): EntityDefinition =
        definitions[type] ?: error("Definition not found for type: $type")

    fun addDefinedId(type: EntityType, id: Long) {
        getDefinition(type).addDefinedId(id)
    }

    fun addVanillaEditedId(type: EntityType, id: Long) {
        getDefinition(type).addVanillaEditedId(id)
    }

    fun addImplicitDefinition(type: EntityType) {
        getDefinition(type).incrementImplicitDefinitions()
    }

    fun getAllDefinitions(): Map<EntityType, Set<Long>> =
        definitions.entries
            .sortedBy { it.key.name }
            .associate { (type, def) ->
                type to def.definedIds
            }
}