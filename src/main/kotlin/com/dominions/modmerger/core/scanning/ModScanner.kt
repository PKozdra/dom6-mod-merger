// src/main/kotlin/com/dominions/modmerger/core/scanning/ModScanner.kt
package com.dominions.modmerger.core.scanning

import com.dominions.modmerger.core.parsing.ModParser
import com.dominions.modmerger.domain.ModDefinition
import com.dominions.modmerger.domain.ModFile
import com.dominions.modmerger.infrastructure.Logging
import kotlinx.coroutines.*

class ModScanner(
    private val parser: ModParser,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : Logging {

    suspend fun scanMods(files: List<ModFile>): Map<String, ModDefinition> = coroutineScope {
        files.map { file ->
            async(dispatcher) {
                try {
                    file.name to scanModFile(file)
                } catch (e: Exception) {
                    error("Error scanning mod file ${file.name}: ${e.message}")
                    throw e
                }
            }
        }.awaitAll().toMap()
    }

    private fun scanModFile(file: ModFile): ModDefinition {
        return parser.parse(file)
    }
}