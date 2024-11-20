// src/main/kotlin/com/dominions/modmerger/MergeResult.kt
package com.dominions.modmerger

import com.dominions.modmerger.domain.ModFile

sealed class MergeResult {
    data class Success(val warnings: List<MergeWarning> = emptyList()) : MergeResult()
    data class Failure(val error: String) : MergeResult()
}

sealed class MergeWarning {
    data class AmbiguousSpell(val modFile: ModFile, val spellName: String) : MergeWarning()
    data class InvalidUtf8(val modFile: ModFile) : MergeWarning()
    data class ImplicitId(val modFile: ModFile, val entityType: String) : MergeWarning()
}
