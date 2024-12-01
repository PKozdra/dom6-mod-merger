// src/main/kotlin/com/dominions/modmerger/domain/MappedModDefintion.kt
package com.dominions.modmerger.domain

data class MappedModDefinition(
    val modFile: ModFile,
    private val mappings: MutableMap<EntityType, MutableMap<Long, Long>> = mutableMapOf()
) {
    fun addMapping(type: EntityType, original: Long, new: Long) {
        // Only store mappings where the ID actually changes
        if (original != new) {
            mappings.getOrPut(type) { mutableMapOf() }[original] = new
        }
    }

    fun getMapping(type: EntityType, originalId: Long): Long =
        // Return original ID if no mapping exists
        mappings[type]?.get(originalId) ?: originalId

    fun getAllMappings(): List<ModMapping> =
        mappings.entries
            .sortedBy { it.key.name }
            .flatMap { (type, typeMappings) ->
                typeMappings.entries
                    .sortedBy { it.key }
                    .map { (original, new) ->
                        ModMapping(ModIdentifier(original), ModIdentifier(new), type)
                    }
            }

    fun getMappingsByType(): Map<EntityType, Map<Long, Long>> =
        mappings.entries
            .sortedBy { it.key.name }
            .associate { (type, mapping) ->
                type to mapping.entries
                    .sortedBy { it.key }
                    .associate { it.key to it.value }
            }
}

data class ModMapping(
    val originalId: ModIdentifier,
    val newId: ModIdentifier,
    val entityType: EntityType
)