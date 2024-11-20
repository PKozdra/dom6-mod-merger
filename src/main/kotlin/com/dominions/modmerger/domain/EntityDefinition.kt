// src/main/kotlin/com/dominions/modmerger/domain/EntityDefinition.kt
package com.dominions.modmerger.domain

data class EntityDefinition(
    val entityType: EntityType,
    val definedIds: MutableSet<Long> = mutableSetOf(),
    val vanillaEditedIds: MutableSet<Long> = mutableSetOf(),
    var implicitDefinitions: Int = 0
) {
    fun addDefinedId(id: Long) {
        definedIds.add(id)
    }

    fun addVanillaEditedId(id: Long) {
        vanillaEditedIds.add(id)
    }

    fun incrementImplicitDefinitions() {
        implicitDefinitions++
    }

    fun hasConflictsWith(other: EntityDefinition): Boolean {
        return definedIds.intersect(other.definedIds).isNotEmpty()
    }
}