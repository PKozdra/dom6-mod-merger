// src/main/kotlin/com/dominions/modmerger/domain/ModConflict.kt
package com.dominions.modmerger.domain

data class ModConflict(
    val type: EntityType,
    val conflictingIds: Set<ModIdentifier>,
    val firstMod: String,
    val secondMod: String
) {
    override fun toString(): String =
        "ID conflict in $type between $firstMod and $secondMod: ${conflictingIds.joinToString(", ") { it.value.toString() }}"
}