// src/main/kotlin/com/dominions/modmerger/domain/ModIdentifier.kt
package com.dominions.modmerger.domain

// mod event codes are negative, so the value may be negative
@JvmInline
value class ModIdentifier(val value: Long)
