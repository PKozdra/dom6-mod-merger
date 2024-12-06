// VanillaEntity.kt
package com.dominions.modmerger.vanilladata.entity

sealed interface VanillaEntity {
    val id: Long
    val name: String
}