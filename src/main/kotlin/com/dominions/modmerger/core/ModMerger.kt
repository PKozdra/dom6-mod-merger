package com.dominions.modmerger.core

import com.dominions.modmerger.MergeResult
import com.dominions.modmerger.domain.ModFile

interface ModMerger {
    suspend fun mergeMods(modFiles: List<ModFile>): MergeResult
}