package com.dominions.modmerger.core.writing

import com.dominions.modmerger.constants.ModPatterns
import com.dominions.modmerger.core.processing.EntityProcessor
import com.dominions.modmerger.core.processing.SpellBlockProcessor
import com.dominions.modmerger.domain.MappedModDefinition
import com.dominions.modmerger.domain.MergeWarning
import com.dominions.modmerger.domain.ModDefinition
import com.dominions.modmerger.infrastructure.Logging
import com.dominions.modmerger.utils.ModUtils
import java.io.Writer

/**
 * Processes mod content by filtering, transforming, and validating mod file lines.
 * Handles ID remapping and ensures proper block structure while preserving original line numbers.
 */
class ModContentWriter(
    private val entityProcessor: EntityProcessor,
    private val spellBlockProcessor: SpellBlockProcessor
) : Logging {

    private val warnings = mutableListOf<MergeWarning>()

    /**
     * Line with metadata preserved for accurate error reporting.
     */
    private data class TrackedLine(
        val content: String,
        val originalLineNumber: Int,
        val fileName: String
    )

    /**
     * Processing context that maintains state during mod file processing.
     */
    private data class ProcessingContext(
        var inMultilineDescription: Boolean = false,
        val processedLines: MutableList<TrackedLine> = mutableListOf(),
        val modDefinition: ModDefinition
    )

    fun processModContent(
        mappedDefinitions: Map<String, MappedModDefinition>,
        modDefinitions: Map<String, ModDefinition>,
        writer: Writer
    ): List<MergeWarning> {
        warnings.clear()

        val startTime = System.currentTimeMillis()
        debug("Processing ${mappedDefinitions.size} mod definitions")

        mappedDefinitions.forEach { (name, mappedDef) ->
            val originalDef = modDefinitions[name]
                ?: throw ModContentProcessingException("Missing definition for mod $name")
            processMod(name, mappedDef, originalDef, writer)
        }

        writer.write("\n-- MOD MERGER: End merged content\n")

        val elapsed = System.currentTimeMillis() - startTime
        info("Processed mod content in ${elapsed}ms with ${warnings.size} warnings")
        return warnings
    }

    private fun processMod(
        name: String,
        mappedDef: MappedModDefinition,
        originalDef: ModDefinition,
        writer: Writer
    ) {
        val startTime = System.currentTimeMillis()
        entityProcessor.cleanImplicitIdIterators()

        debug("Processing mod: $name (${mappedDef.modFile.content.lines().size} lines)")
        writer.write("\n-- Begin content from mod: $name\n")

        val context = ProcessingContext(modDefinition = originalDef)

        // Process lines with proper multiline quote handling
        val cleanedLines = removeModInfoBlocks(mappedDef.modFile.content.lines(), mappedDef.modFile.name)
        val filteredLines = filterAndKeepLines(cleanedLines)

        filteredLines.forEach { trackedLine ->
            processLine(trackedLine, context, mappedDef)
        }

        validateBlocks(context.processedLines)
        writeOutput(context.processedLines, writer)

        val elapsed = System.currentTimeMillis() - startTime
        info("Processed '$name' in ${elapsed}ms")
    }

    /**
     * Removes mod info blocks with proper multiline quote handling.
     */
    private fun removeModInfoBlocks(lines: List<String>, fileName: String): List<TrackedLine> {
        val result = mutableListOf<TrackedLine>()
        var inModInfoBlock = false
        var totalQuotesInBlock = 0

        lines.forEachIndexed { index, originalLine ->
            val line = originalLine
            val trimmed = line.trimStart()

            if (inModInfoBlock) {
                // Count quotes if not fully commented
                if (!trimmed.startsWith("--")) {
                    totalQuotesInBlock += line.count { it == '"' }
                    if (totalQuotesInBlock % 2 == 0) {
                        inModInfoBlock = false
                        totalQuotesInBlock = 0
                    }
                }
                // Skip mod info lines
                return@forEachIndexed
            }

            if (trimmed.isModInfoLine()) {
                val quotesHere = line.count { it == '"' }
                if (quotesHere % 2 == 1) {
                    inModInfoBlock = true
                    totalQuotesInBlock = quotesHere
                }
                // Skip mod info lines
                return@forEachIndexed
            }

            // Keep line with tracking info
            result.add(TrackedLine(line, index + 1, fileName))
        }

        return result
    }

    /**
     * Filters lines with proper multiline handling.
     */
    private fun filterAndKeepLines(lines: List<TrackedLine>): List<TrackedLine> {
        val result = mutableListOf<TrackedLine>()
        var inMultilineBlock = false
        var lastLineWasCommand = false

        lines.forEach { trackedLine ->
            val line = trackedLine.content
            val trimmed = line.trimStart()

            when {
                // 1) Blank line => keep only if last line was a command
                line.isBlank() -> {
                    if (lastLineWasCommand) {
                        result.add(trackedLine)
                        lastLineWasCommand = false
                    }
                }

                // 2) If we're in a multiline block => keep, check if closing quote
                inMultilineBlock -> {
                    result.add(trackedLine)
                    val noComment = line.split("--")[0]
                    if (noComment.contains("\"")) {
                        inMultilineBlock = false
                    }
                    lastLineWasCommand = true
                }

                // 3) #command or --comment => keep
                trimmed.startsWith("--") || trimmed.startsWith("#") -> {
                    result.add(trackedLine)
                    // If there's an odd number of quotes => might start multiline
                    val noComment = trimmed.split("--")[0]
                    val quoteCount = noComment.count { it == '"' }
                    if (quoteCount % 2 == 1) {
                        inMultilineBlock = true
                    }
                    lastLineWasCommand = true
                }

                // 4) Everything else => skip
                else -> {
                    lastLineWasCommand = false
                }
            }
        }

        return result
    }

    private fun processLine(
        trackedLine: TrackedLine,
        context: ProcessingContext,
        mappedDef: MappedModDefinition
    ) {
        val line = ModUtils.removeUnreadableCharacters(trackedLine.content)
        trace("Processing line ${trackedLine.originalLineNumber}: $line")

        if (line.shouldSkip(context)) return

        when {
            spellBlockProcessor.isInSpellBlock() -> {
                handleSpellBlockLine(line, context, mappedDef, trackedLine)
            }
            line.trim().isSpellBlockStart() -> {
                spellBlockProcessor.startBlock(line)
            }
            else -> {
                handleRegularLine(line, context, mappedDef, trackedLine)
            }
        }
    }

    private fun handleSpellBlockLine(
        line: String,
        context: ProcessingContext,
        mappedDef: MappedModDefinition,
        trackedLine: TrackedLine
    ) {
        val (processedLine, comment) = spellBlockProcessor.handleSpellLine(
            line, mappedDef, context.modDefinition, entityProcessor
        )

        comment?.let {
            context.processedLines.add(trackedLine.copy(content = it))
        }

        if (processedLine.isNotEmpty()) {
            processedLine.split("\n").forEach { subLine ->
                context.processedLines.add(trackedLine.copy(content = subLine))
            }
        }
    }

    private fun handleRegularLine(
        line: String,
        context: ProcessingContext,
        mappedDef: MappedModDefinition,
        trackedLine: TrackedLine
    ) {
        val processed = entityProcessor.processEntity(
            line = line,
            mappedDef = mappedDef,
            remapCommentWriter = { type, oldId, newId ->
                "-- MOD MERGER: Remapped ${type.name} $oldId -> $newId"
            },
            modDef = context.modDefinition
        )

        processed.remapComment?.let {
            context.processedLines.add(trackedLine.copy(content = it))
        }

        processed.line.split("\n").forEach { subLine ->
            context.processedLines.add(trackedLine.copy(content = subLine))
        }
    }

    private fun validateBlocks(lines: List<TrackedLine>) {
        val blockStack = mutableListOf<BlockInfo>()

        lines.forEach { trackedLine ->
            val trimmed = trackedLine.content.trim()
            when {
                trimmed.isBlockStart() -> {
                    val blockType = trimmed.extractBlockType()
                    blockStack.add(BlockInfo(
                        line = trackedLine.originalLineNumber,
                        type = blockType,
                        fileName = trackedLine.fileName
                    ))
                }
                trimmed == "#end" -> {
                    if (blockStack.isNotEmpty()) {
                        blockStack.removeAt(blockStack.lastIndex)
                    }
                }
            }
        }

        // Report unclosed blocks with cleaner format
        blockStack.forEach { block ->
            warnings.add(MergeWarning.GeneralWarning(
                message = "Unclosed ${block.type} block at line ${block.line}",
                modFile = block.fileName
            ))
        }
    }

    private fun writeOutput(lines: List<TrackedLine>, writer: Writer) {
        val skipIndices = findSkippableEnds(lines.map { it.content })

        lines.forEachIndexed { index, trackedLine ->
            if (index !in skipIndices) {
                writer.write("${trackedLine.content}\n")
            }
        }
    }

    private fun findSkippableEnds(lines: List<String>): Set<Int> {
        val skippable = mutableSetOf<Int>()
        var lastEndIndex = -1
        var onlyWhitespaceSince = true

        lines.forEachIndexed { index, line ->
            when (line.trim()) {
                "#end" -> {
                    if (lastEndIndex != -1 && onlyWhitespaceSince) {
                        skippable.add(index)
                    } else {
                        lastEndIndex = index
                        onlyWhitespaceSince = true
                    }
                }
                "" -> { /* keep onlyWhitespaceSince state */ }
                else -> {
                    lastEndIndex = -1
                    onlyWhitespaceSince = false
                }
            }
        }
        return skippable
    }

    // Extension functions for cleaner code
    private fun String.isModInfoLine(): Boolean {
        val lower = this.lowercase()
        return lower.startsWith("#description") ||
                lower.startsWith("#modname") ||
                lower.startsWith("#icon") ||
                lower.startsWith("#version") ||
                lower.startsWith("#domversion")
    }

    private fun String.shouldSkip(context: ProcessingContext): Boolean {
        val trimmed = trim()

        return when {
            context.inMultilineDescription -> {
                if (trimmed.contains("\"")) {
                    context.inMultilineDescription = false
                }
                true
            }
            isDescriptionStart(trimmed) -> {
                context.inMultilineDescription = !trimmed.endsWith("\"")
                true
            }
            trimmed.isModInfoLine() -> true
            else -> false
        }
    }

    private fun String.isSpellBlockStart(): Boolean =
        startsWith("#newspell") || matches(Regex("""^#selectspell\s+\d+.*"""))

    private fun String.isBlockStart(): Boolean =
        startsWith("#select") || startsWith("#new")

    private fun String.extractBlockType(): String {
        val command = substringBefore(" ").lowercase()
        return when {
            command.startsWith("#new") -> command.substring(4)
            command.startsWith("#select") -> command.substring(7)
            else -> "unknown"
        }
    }

    private fun isDescriptionStart(line: String): Boolean =
        ModPatterns.MOD_DESCRIPTION_START.matches(line) &&
                !ModPatterns.MOD_DESCRIPTION_LINE.matches(line)

    private data class BlockInfo(
        val line: Int,
        val type: String,
        val fileName: String
    )
}

class ModContentProcessingException(message: String, cause: Throwable? = null) : Exception(message, cause)