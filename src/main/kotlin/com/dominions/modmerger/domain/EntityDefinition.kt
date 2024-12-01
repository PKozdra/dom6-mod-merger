// src/main/kotlin/com/dominions/modmerger/domain/EntityDefinition.kt
package com.dominions.modmerger.domain

data class EntityDefinition(
    val entityType: EntityType,
    private val _definedIds: MutableSet<Long> = mutableSetOf(),
    private val _vanillaEditedIds: MutableSet<Long> = mutableSetOf(),
    var implicitDefinitions: Int = 0
) {
    val definedIds: Set<Long>
        get() = _definedIds.sorted().toSet()

    val vanillaEditedIds: Set<Long>
        get() = _vanillaEditedIds.sorted().toSet()

    fun addDefinedId(id: Long) {
        _definedIds.add(id)
    }

    fun addVanillaEditedId(id: Long) {
        _vanillaEditedIds.add(id)
    }

    fun incrementImplicitDefinitions() {
        implicitDefinitions++
    }
}