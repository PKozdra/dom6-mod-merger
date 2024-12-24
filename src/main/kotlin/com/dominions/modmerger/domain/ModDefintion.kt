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
    private var _isFrozen: Boolean = false
    private var _cachedAllDefinitions: Map<EntityType, Set<Long>>? = null
    private var _cachedVanillaEditedIds: Map<EntityType, Set<Long>>? = null

    init {
        EntityType.entries
            .sortedBy { it.name }
            .forEach { type ->
                definitions[type] = type.createDefinition()
            }
    }

    fun getDefinition(type: EntityType): EntityDefinition =
        definitions[type] ?: throw IllegalArgumentException("No definition found for entity type: $type")

    fun addDefinedId(type: EntityType, id: Long) {
        check(!_isFrozen) { "Cannot modify frozen ModDefinition" }
        trace("Adding defined ID $id for entity type $type", useDispatcher = false)
        getDefinition(type).addDefinedId(id)
    }

    fun addDefinedName(type: EntityType, name: String, id: Long?, lineNumber: Int) {
        check(!_isFrozen) { "Cannot modify frozen ModDefinition" }
        trace("Adding defined name $name for entity type $type", useDispatcher = false)
        getDefinition(type).addDefinedName(name, id, lineNumber)
    }

    fun addVanillaEditedId(type: EntityType, id: Long) {
        check(!_isFrozen) { "Cannot modify frozen ModDefinition" }
        trace("Adding vanilla edited ID $id for entity type $type", useDispatcher = false)
        getDefinition(type).addVanillaEditedId(id)
    }

    fun addImplicitDefinition(type: EntityType): Int {
        check(!_isFrozen) { "Cannot modify frozen ModDefinition" }
        trace("Adding implicit definition for entity type $type", useDispatcher = false)
        return getDefinition(type).incrementImplicitDefinitions()
    }

    fun getNameIdMapping(type: EntityType): Map<String, Long?> =
        getDefinition(type).definedNames.mapValues { it.value.id }

    fun getAllDefinitions(): Map<EntityType, Set<Long>> =
        _cachedAllDefinitions ?: definitions.entries
            .sortedBy { it.key.name }
            .associate { (type, def) ->
                type to def.definedIds
            }

    fun getAllVanillaEditedIds(): Map<EntityType, Set<Long>> =
        _cachedVanillaEditedIds ?: definitions.entries
            .sortedBy { it.key.name }
            .associate { (type, def) ->
                type to def.vanillaEditedIds
            }

    fun freeze() {
        if (!_isFrozen) {
            definitions.values.forEach { it.freeze() }
            _cachedAllDefinitions = getAllDefinitions()
            _cachedVanillaEditedIds = getAllVanillaEditedIds()
            _isFrozen = true
        }
    }

    fun cleanup() {
        trace("Cleaning up definitions", useDispatcher = false)
        definitions.values.forEach { it.cleanup() }
        definitions.clear()
        _cachedAllDefinitions = null
        _cachedVanillaEditedIds = null
        _isFrozen = false
    }
}