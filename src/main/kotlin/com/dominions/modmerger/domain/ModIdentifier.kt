// src/main/kotlin/com/dominions/modmerger/domain/ModIdentifier.kt
package com.dominions.modmerger.domain

@JvmInline
value class ModIdentifier(val value: Long) {
    init {
        require(value >= 0) { "Identifier must be non-negative" }
    }
}