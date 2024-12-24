// src/main/kotlin/com/dominions/modmerger/domain/MappedModDefintion.kt
package com.dominions.modmerger.domain

data class MappedModDefinition(
    val modFile: ModFile,
    private val mappings: MutableMap<EntityType, MutableMap<Long, Long>> = mutableMapOf()
) {
    private var _cachedMappingsByType: Map<EntityType, Map<Long, Long>>? = null
    private var _cachedAllMappings: List<ModMapping>? = null
    private var _isFrozen: Boolean = false

    fun addMapping(type: EntityType, original: Long, new: Long) {
        check(!_isFrozen) { "Cannot modify frozen MappedModDefinition" }
        if (original != new) {
            mappings.getOrPut(type) { mutableMapOf() }[original] = new
        }
    }

    fun getMapping(type: EntityType, originalId: Long): Long =
        if (_isFrozen) {
            _cachedMappingsByType?.get(type)?.get(originalId) ?: originalId
        } else {
            mappings[type]?.get(originalId) ?: originalId
        }

    fun getAllMappings(): List<ModMapping> =
        _cachedAllMappings ?: mappings.entries
            .sortedBy { it.key.name }
            .flatMap { (type, typeMappings) ->
                typeMappings.entries
                    .sortedBy { it.key }
                    .map { (original, new) ->
                        ModMapping(ModIdentifier(original), ModIdentifier(new), type)
                    }
            }

    fun getMappingsByType(): Map<EntityType, Map<Long, Long>> =
        _cachedMappingsByType ?: mappings.entries
            .sortedBy { it.key.name }
            .associate { (type, mapping) ->
                type to mapping.entries
                    .sortedBy { it.key }
                    .associate { it.key to it.value }
            }

    fun freeze() {
        if (!_isFrozen) {
            _cachedMappingsByType = getMappingsByType()
            _cachedAllMappings = getAllMappings()
            _isFrozen = true
        }
    }

    fun cleanup() {
        mappings.clear()
        _cachedMappingsByType = null
        _cachedAllMappings = null
        _isFrozen = false
    }
}

data class ModMapping(
    val originalId: ModIdentifier,
    val newId: ModIdentifier,
    val entityType: EntityType
)