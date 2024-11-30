// src/main/kotlin/com/dominions/modmerger/domain/MappedModDefintion.kt
package com.dominions.modmerger.domain

data class ModMapping(
    val originalId: ModIdentifier,
    val newId: ModIdentifier,
    val entityType: EntityType
)

data class MappedModDefinition(
    val modFile: ModFile,
    private val mappings: MutableMap<EntityType, MutableMap<Long, Long>> = mutableMapOf()
) {
    fun addMapping(type: EntityType, original: Long, new: Long) {
        val typeMapping = mappings.getOrPut(type) { mutableMapOf() }
        typeMapping[original] = new
    }

    fun getMapping(type: EntityType, originalId: Long): Long? =
        mappings[type]?.get(originalId)

    fun getAllMappings(): List<ModMapping> =
        mappings.flatMap { (type, typeMappings) ->
            typeMappings.map { (original, new) ->
                ModMapping(ModIdentifier(original), ModIdentifier(new), type)
            }
        }
}
