// src/test/kotlin/com/dominions/modmerger/testutils/TestUtils.kt
package com.dominions.modmerger.testutils

import com.dominions.modmerger.core.ModMergerService
import com.dominions.modmerger.core.mapping.IdMapper
import com.dominions.modmerger.core.parsing.ModParser
import com.dominions.modmerger.core.processing.EntityProcessor
import com.dominions.modmerger.core.processing.SpellBlockProcessor
import com.dominions.modmerger.core.scanning.ModScanner
import com.dominions.modmerger.core.writing.ModContentWriter
import com.dominions.modmerger.core.writing.ModHeaderWriter
import com.dominions.modmerger.core.writing.ModResourceCopier
import com.dominions.modmerger.core.writing.ModWriter
import com.dominions.modmerger.core.writing.config.ModOutputConfig
import com.dominions.modmerger.domain.ModFile
import com.dominions.modmerger.infrastructure.FileSystem
import com.dominions.modmerger.infrastructure.GamePathsManager
import java.io.File

object TestUtils {
    fun createModParser(): ModParser {
        val entityProcessor = EntityProcessor()
        return ModParser(
            entityProcessor = entityProcessor
        )
    }

    fun loadTestMod(filename: String): ModFile {
        val content = ClassLoader.getSystemResource("mods/$filename")?.readText()
            ?: throw IllegalStateException("Test mod $filename not found")
        return ModFile.fromContent(filename, content)  // Use the factory method
    }

    fun extractRemapInfo(content: String, type: String): Map<Long, Long> {
        val pattern = "-- MOD MERGER: Remapped $type (\\d+) -> (\\d+)".toRegex()
        return pattern.findAll(content).associate {
            it.groupValues[1].toLong() to it.groupValues[2].toLong()
        }
    }

    fun createTestServices(tempDir: File): ServiceContainer {
        val logDispatcher = TestLogDispatcher()
        val entityProcessor = EntityProcessor()
        val parser = createModParser()
        val scanner = ModScanner(parser)
        val mapper = IdMapper(logDispatcher)

        // Use TestGamePathsManager instead of regular GamePathsManager
        val gamePathsManager = TestGamePathsManager(tempDir)
        val fileSystem = FileSystem(gamePathsManager)

        val spellBlockProcessor = SpellBlockProcessor()

        val contentWriter = ModContentWriter(entityProcessor, spellBlockProcessor, logDispatcher)
        val resourceCopier = ModResourceCopier(logDispatcher)
        val headerWriter = ModHeaderWriter(logDispatcher)

        val writer = ModWriter(
            fileSystem = fileSystem,
            logDispatcher = logDispatcher,
            contentWriter = contentWriter,
            resourceCopier = resourceCopier,
            headerWriter = headerWriter
        )

        val service = ModMergerService(
            scanner = scanner,
            mapper = mapper,
            writer = writer,
            config = ModOutputConfig("merged_mod", GamePathsManager()),
            fileSystem = fileSystem,
            logDispatcher = logDispatcher
        )

        return ServiceContainer(
            modMergerService = service,
            mapper = mapper,
            scanner = scanner,
            writer = writer,
            parser = parser,
            logDispatcher = logDispatcher,
            fileSystem = fileSystem,
            gamePathsManager = gamePathsManager
        )
    }

    fun setupTestMods(tempDir: File) {
        val modsDir = File(tempDir, "mods")
        modsDir.mkdirs()

        // Copy test mods from test resources to temp directory
        val testMod1Content = loadTestMod("test_mod_1.dm")
        val testMod2Content = loadTestMod("test_mod_2.dm")

        File(modsDir, "test_mod_1.dm").writeText(testMod1Content.content)
        File(modsDir, "test_mod_2.dm").writeText(testMod2Content.content)
    }
}

// Update ServiceContainer to include gamePathsManager
data class ServiceContainer(
    val modMergerService: ModMergerService,
    val mapper: IdMapper,
    val scanner: ModScanner,
    val writer: ModWriter,
    val parser: ModParser,
    val logDispatcher: TestLogDispatcher,
    val fileSystem: FileSystem,
    val gamePathsManager: TestGamePathsManager
)