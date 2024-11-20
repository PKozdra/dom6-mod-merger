// src/main/kotlin/com/dominions/modmerger/domain/EntityType.kt
package com.dominions.modmerger.domain

enum class EntityType {
    WEAPON,
    ARMOR,
    MONSTER,
    SPELL,
    ITEM,
    SITE,
    NATION,
    NAME_TYPE,
    ENCHANTMENT,
    EVENT,
    POPTYPE,
    MONTAG,
    RESTRICTED_ITEM;

    fun createDefinition() = EntityDefinition(entityType = this)
}
