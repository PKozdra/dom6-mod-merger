// src/main/kotlin/com/dominions/modmerger/core/ModMergerService.kt
package com.dominions.modmerger.core

import com.dominions.modmerger.MergeResult
import com.dominions.modmerger.core.mapping.IdMapper
import com.dominions.modmerger.core.parsing.ModParser
import com.dominions.modmerger.core.scanning.ModScanner
import com.dominions.modmerger.core.writing.ModWriter
import com.dominions.modmerger.domain.*
import mu.KLogger
import mu.KotlinLogging

class ModMergerService(
    private val parser: ModParser,
    private val scanner: ModScanner,
    private val mapper: IdMapper,
    private val writer: ModWriter
) {
    private val logger: KLogger = KotlinLogging.logger { }

    suspend fun processMods(modFiles: List<ModFile>): MergeResult {
        return try {
            // Scan and parse mod files
            logger.info("Scanning mod files...")
            val modDefinitions = scanner.scanMods(modFiles)

            // Map IDs
            logger.info("Mapping IDs...")
            val mappedDefinitions = mapper.createMappings(modDefinitions)

            // Generate merged mod
            logger.info("Generating merged mod...")
            val warnings = writer.writeMergedMod(mappedDefinitions)

            MergeResult.Success(warnings)
        } catch (e: Exception) {
            logger.error("Failed to process mods", e)
            MergeResult.Failure(e.message ?: "Unknown error occurred")
        }
    }
}
