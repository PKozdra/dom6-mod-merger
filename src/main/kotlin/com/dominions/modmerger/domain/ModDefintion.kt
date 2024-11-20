// src/main/kotlin/com/dominions/modmerger/domain/ModDefinition.kt
package com.dominions.modmerger.domain

data class ModDefinition(
    val modFile: ModFile,
    var name: String = "",
    val version: String = "",
    private val definitions: MutableMap<EntityType, EntityDefinition> = mutableMapOf()
) {
    init {
        // Initialize all entity types with empty definitions
        EntityType.entries.forEach { type ->
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

    fun findConflicts(other: ModDefinition): List<ModConflict> {
        return EntityType.entries.mapNotNull { type ->
            val thisDefinition = getDefinition(type)
            val otherDefinition = other.getDefinition(type)

            val conflictingIdsLong = thisDefinition.definedIds.intersect(otherDefinition.definedIds)
            if (conflictingIdsLong.isNotEmpty()) {
                val conflictingIds = conflictingIdsLong.map { id -> ModIdentifier(id) }.toSet()
                ModConflict(
                    type = type,
                    conflictingIds = conflictingIds,
                    firstMod = this.modFile.name,
                    secondMod = other.modFile.name
                )
            } else null
        }
    }
}
