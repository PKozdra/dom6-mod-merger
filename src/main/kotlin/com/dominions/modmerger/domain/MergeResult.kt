package com.dominions.modmerger.domain

sealed class MergeResult {
    data class Success(val warnings: List<MergeWarning> = emptyList()) : MergeResult()
    data class Failure(val error: String) : MergeResult()
}

sealed class MergeWarning {
    // Original warnings for backwards compatibility
    data class AmbiguousSpell(val modFile: ModFile, val spellName: String) : MergeWarning()
    data class InvalidUtf8(val modFile: ModFile) : MergeWarning()
    data class ImplicitId(val modFile: ModFile, val entityType: String) : MergeWarning()

    // New general-purpose warnings
    data class ResourceWarning(
        val modFile: ModFile,
        val message: String,
        val resourcePath: String? = null
    ) : MergeWarning()

    data class ContentWarning(
        val modFile: ModFile,
        val message: String,
        val lineNumber: Int? = null
    ) : MergeWarning()

    data class ValidationWarning(
        val modFile: ModFile,
        val message: String,
        val field: String? = null
    ) : MergeWarning()

    data class ConflictWarning(
        val sourceModFile: ModFile,
        val targetModFile: ModFile,
        val message: String,
        val resolution: String? = null
    ) : MergeWarning()

    // For misc warnings that don't fit other categories
    data class GeneralWarning(
        val message: String,
        val modFile: String? = null
    ) : MergeWarning()
}