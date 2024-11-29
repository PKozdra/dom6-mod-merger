// src/main/kotlin/com/dominions/modmerger/core/scanning/ModScanner.kt
package com.dominions.modmerger.core.scanning

import com.dominions.modmerger.domain.ModDefinition
import com.dominions.modmerger.domain.ModFile

interface ModScanner {
    suspend fun scanMods(files: List<ModFile>): Map<String, ModDefinition>
}