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
 * ModContentWriter is responsible for orchestrating how lines are processed:
 *  - Removing mod info blocks.
 *  - Filtering lines (e.g., ignoring non-commands unless in multiline quotes).
 *  - Validating open/close blocks.
 *  - Deciding whether a line goes to SpellBlockProcessor (if we're in a spell block)
 *    or to EntityProcessor (normal ID usage references).
 *
 * SpellBlockProcessor also has a reference to an EntityProcessor, so it can
 * delegate lines like #restricted or #descr, while it handles #damage/#effect itself.
 */
class ModContentWriter(
    private val entityProcessor: EntityProcessor,
    private val spellBlockProcessor: SpellBlockProcessor
) : Logging {

    private val warnings = mutableListOf<MergeWarning>()

    /**
     * We'll store information about the current mod file being processed,
     * including a list of "processed lines" that we'll eventually write out.
     */
    private data class ModProcessingContext(
        var inMultilineDescription: Boolean = false,
        var processedLines: MutableList<String> = mutableListOf(),
        val modDefinition: ModDefinition
    )

    /**
     * Main entry point: processes all mapped definitions (i.e. all mod files).
     */
    fun processModContent(
        mappedDefinitions: Map<String, MappedModDefinition>,
        modDefinitions: Map<String, ModDefinition>,
        writer: Writer
    ): List<MergeWarning> {
        warnings.clear()
        try {
            debug("Starting to process ${mappedDefinitions.size} mod definitions")
            val totalStartTime = System.currentTimeMillis()

            // Process each mapped mod file
            mappedDefinitions.forEach { (name, mappedDef) ->
                val originalDef = modDefinitions[name]
                    ?: throw ModContentProcessingException("Cannot find original definition for mod $name")
                processModDefinition(name, mappedDef, originalDef, writer)
            }

            writer.write("\n-- MOD MERGER: End merged content\n")
            val totalTime = System.currentTimeMillis() - totalStartTime
            info("Total mod content processing time: $totalTime ms")
            info("Successfully wrote mod content with ${warnings.size} warnings")
        } catch (e: Exception) {
            handleProcessingError(e)
        }
        return warnings
    }

    /**
     * Processes a single mod definition (i.e. one mod file).
     */
    private fun processModDefinition(
        name: String,
        mappedDef: MappedModDefinition,
        originalDef: ModDefinition,
        writer: Writer
    ) {
        val modStartTime = System.currentTimeMillis()
        entityProcessor.cleanImplicitIdIterators()
        debug("Processing mod: $name with ${mappedDef.modFile.content.lines().size} lines")

        writer.write("\n-- Begin content from mod: $name\n")
        processModFile(mappedDef, originalDef, writer)

        val elapsed = System.currentTimeMillis() - modStartTime
        info("Processed mod '$name' in $elapsed ms")
    }

    /**
     * Processes the raw lines of a single mod file:
     *  1) Remove #modinfo blocks (#description, #domversion, etc.).
     *  2) Filter lines (#commands, comments, multiline quotes, etc.).
     *  3) Actually parse & transform them (via processLines).
     *  4) Validate open/close blocks.
     *  5) Write them out to the final writer.
     */
    private fun processModFile(
        mappedDef: MappedModDefinition,
        originalDef: ModDefinition,
        writer: Writer
    ) {
        // 1) Remove #modinfo-like lines
        val cleanedLines = removeModInfoBlocks(mappedDef.modFile.content.lines())

        // 2) Filter lines (keep #commands, comments, etc.)
        val secondPassLines = filterAndKeepLines(cleanedLines)

        // 3) Actually parse & transform
        val context = ModProcessingContext(modDefinition = originalDef)
        processLines(secondPassLines, context, mappedDef)

        // 4) Validate open/close blocks for #new / #select / #end
        validateBlocks(context.processedLines, mappedDef.modFile.name)

        // 5) Identify if any #end lines can be skipped (empty blocks, etc.)
        val skippableEnds = identifySkippableEnds(context.processedLines)

        // 6) Write out final
        context.processedLines.forEachIndexed { index, line ->
            if (!skippableEnds.contains(index)) {
                writer.write(line + "\n")
            }
        }
    }

    /**
     * Example logic that removes #description, #icon, #version, etc.
     * from the mod file entirely. If a line starts #description "abc"
     * and we haven't closed the quotes, skip them.
     */
    fun removeModInfoBlocks(lines: List<String>): List<String> {
        val result = mutableListOf<String>()
        var inModInfoBlock = false
        var totalQuotesInBlock = 0

        for (originalLine in lines) {
            // Normalize fancy quotes
            // val line = originalLine
            //     .replace('“', '"')
            //     .replace('”', '"')
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
                // skip
                continue
            }

            if (isModInfoLine(trimmed)) {
                // e.g. #description, #icon, #version, #domversion, #modname
                val quotesHere = line.count { it == '"' }
                if (quotesHere % 2 == 1) {
                    inModInfoBlock = true
                    totalQuotesInBlock = quotesHere
                }
                continue
            }

            // Keep
            result.add(line)
        }

        return result
    }

    /**
     * Additional filter pass: keep #commands, comments, multiline quotes.
     * Possibly keep blank lines only if last line was a command.
     */
    private fun filterAndKeepLines(lines: List<String>): List<String> {
        val filteredIndices = mutableSetOf<Int>()
        var inMultilineBlock = false
        var lastLineWasCommand = false

        lines.forEachIndexed { i, line ->
            val trimmed = line.trimStart()

            when {
                // 1) Blank line => keep only if last line was a command
                line.isBlank() -> {
                    if (lastLineWasCommand) {
                        filteredIndices.add(i)
                        lastLineWasCommand = false
                    }
                }

                // 2) If we're in a multiline block => keep, check if closing quote
                inMultilineBlock -> {
                    filteredIndices.add(i)
                    val noComment = line.split("--")[0]
                    if (noComment.contains("\"")) {
                        inMultilineBlock = false
                    }
                    lastLineWasCommand = true
                }

                // 3) #command or --comment => keep
                trimmed.startsWith("--") || trimmed.startsWith("#") -> {
                    filteredIndices.add(i)
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

        return lines.filterIndexed { i, _ -> i in filteredIndices }
    }

    /**
     * The main "loop" that processes each line:
     *  - Possibly skip it if it's part of a multiline description, etc.
     *  - Otherwise send to handleRegularLine, which decides if we're in a spell block or not.
     */
    private fun processLines(
        lines: List<String>,
        context: ModProcessingContext,
        mappedDef: MappedModDefinition
    ) {
        var i = 0
        while (i < lines.size) {
            try {
                i = processNextLine(lines, i, context, mappedDef)
            } catch (e: Exception) {
                handleLineProcessingError(e, i, lines, mappedDef)
            }
        }
    }

    /**
     * Called for each line in the filtered set. We skip lines if they're in
     * a multiline #description, or pass them to handleRegularLine otherwise.
     */
    private fun processNextLine(
        lines: List<String>,
        currentIndex: Int,
        context: ModProcessingContext,
        mappedDef: MappedModDefinition
    ): Int {
        val line = ModUtils.removeUnreadableCharacters(lines[currentIndex])
        trace("Processing line $currentIndex: $line", useDispatcher = false)

        // Possibly skip if in multiline #description
        return if (shouldSkipLine(line, currentIndex, context)) {
            currentIndex + 1
        } else {
            handleRegularLine(line, mappedDef, context)
            currentIndex + 1
        }
    }

    /**
     * This is where we decide: if we're currently in a spell block, pass to SpellBlockProcessor.
     * If not in a block, check if line starts a spell block (#newspell or #selectspell).
     * Otherwise, pass to EntityProcessor for normal ID references.
     */
    private fun handleRegularLine(
        line: String,
        mappedDef: MappedModDefinition,
        context: ModProcessingContext
    ) {
        // Let the SpellBlockProcessor decide if we're in a block
        if (spellBlockProcessor.isInSpellBlock()) {
            val (processedLine, processedComment) = spellBlockProcessor.handleSpellLine(
                line,
                mappedDef,
                context.modDefinition,
                entityProcessor
            )
            if (processedComment != null) {
                context.processedLines.add(processedComment)
            }
            if (processedLine.isNotEmpty()) {
                processedLine.split("\n").forEach { subLine ->
                    context.processedLines.add(subLine)
                }
            }
        } else {
            // If we're not in a block, check if this line *starts* one
            val trimmed = line.trim()
            if (isSpellBlockStart(trimmed)) {
                // Start spell block, and also let the line show up in output
                spellBlockProcessor.startBlock(line)
                // context.processedLines.add(line)
            } else {
                // Normal line => pass to EntityProcessor
                val processed = entityProcessor.processEntity(
                    line = line,
                    mappedDef = mappedDef,
                    remapCommentWriter = { type, oldId, newId ->
                        "-- MOD MERGER: Remapped ${type.name} $oldId -> $newId"
                    },
                    modDef = context.modDefinition
                )
                processed.remapComment?.let { context.processedLines.add(it) }

                // If the processed line has multiple sub-lines (multiline #descr?), add them individually
                if (processed.line.contains("\n")) {
                    processed.line.split("\n").forEach { multi ->
                        context.processedLines.add(multi)
                    }
                } else {
                    context.processedLines.add(processed.line)
                }
            }
        }
    }

    /**
     * A helper that identifies if the line is #newspell or #selectspell <id>.
     */
    private fun isSpellBlockStart(line: String): Boolean {
        return line.startsWith("#newspell") ||
                line.matches(Regex("""^#selectspell\s+\d+.*"""))
    }

    /**
     * Decides if we skip a line because we're in a multiline #description, etc.
     */
    private fun shouldSkipLine(
        line: String,
        index: Int,
        context: ModProcessingContext
    ): Boolean {
        val trimmed = line.trim()
        // Possibly skip the line if it's part of a multiline #description, etc.
        // Using your existing logic (some code removed for brevity):
        return when {
            index == 0 && line.isEmpty() -> true
            context.inMultilineDescription -> {
                if (trimmed.contains("\"")) {
                    // close the multiline
                    context.inMultilineDescription = false
                }
                true
            }
            isDescriptionStart(trimmed) -> {
                context.inMultilineDescription = !trimmed.endsWith("\"")
                true
            }
            isModInfoLine(trimmed) -> true
            else -> false
        }
    }

    private fun isDescriptionStart(line: String): Boolean {
        // Example from your code
        return ModPatterns.MOD_DESCRIPTION_START.matches(line) &&
                !ModPatterns.MOD_DESCRIPTION_LINE.matches(line)
    }

    private fun isModInfoLine(trimmedLine: String): Boolean {
        val lower = trimmedLine.lowercase()
        return lower.startsWith("#description") ||
                lower.startsWith("#modname")     ||
                lower.startsWith("#icon")        ||
                lower.startsWith("#version")     ||
                lower.startsWith("#domversion")
    }

    /**
     * Validates open/close blocks (#new / #select / #end).
     */
    private fun validateBlocks(lines: List<String>, modFileName: String) {
        val blockStack = mutableListOf<BlockContext>()

        lines.forEachIndexed { idx, line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("#select") || trimmed.startsWith("#new") -> {
                    val blockType = if (trimmed.startsWith("#select")) "select" else "new"
                    blockStack.add(BlockContext(idx + 1, blockType, trimmed, modFileName))
                }
                trimmed.startsWith("#end") -> {
                    if (blockStack.isNotEmpty()) {
                        blockStack.removeAt(blockStack.lastIndex)
                    }
                }
                (trimmed.startsWith("#select") || trimmed.startsWith("#new")) && blockStack.isNotEmpty() -> {
                    val lastBlock = blockStack.last()
                    warnings.add(
                        MergeWarning.GeneralWarning(
                            message = "Found new block '$trimmed' at line ${idx + 1} while previous '${lastBlock.blockType}' block from line ${lastBlock.startLine} (${lastBlock.modFile}) is still open (missing #end)",
                            modFile = null
                        )
                    )
                }
            }
        }

        // Any unclosed blocks => warnings
        blockStack.forEach { context ->
            warnings.add(
                MergeWarning.GeneralWarning(
                    message = "Unclosed '${context.blockType}' block from line ${context.startLine}: " +
                            context.actualLine + " (${context.modFile})",
                    modFile = modFileName
                )
            )
        }
    }

    /**
     * Some lines that only contain #end could be "skippable" if there's another #end right after
     * with no content in between. For instance, your code that merges blocks might produce duplicates.
     */
    private fun identifySkippableEnds(lines: List<String>): Set<Int> {
        val skippableEnds = mutableSetOf<Int>()
        var lastEndIndex = -1
        var onlyWhitespaceSinceLastEnd = true

        lines.forEachIndexed { index, line ->
            val trimmed = line.trim()
            when {
                trimmed == "#end" -> {
                    if (lastEndIndex != -1 && onlyWhitespaceSinceLastEnd) {
                        // We can skip this #end if we already had one with no content in between
                        skippableEnds.add(index)
                    } else {
                        lastEndIndex = index
                        onlyWhitespaceSinceLastEnd = true
                    }
                }
                trimmed.isEmpty() -> {
                    // It's whitespace => might remain skippable
                    onlyWhitespaceSinceLastEnd = onlyWhitespaceSinceLastEnd && lastEndIndex != -1
                }
                else -> {
                    // We found content => next #end won't be skippable
                    lastEndIndex = -1
                    onlyWhitespaceSinceLastEnd = false
                }
            }
        }
        return skippableEnds
    }

    private data class BlockContext(
        val startLine: Int,
        val blockType: String,
        val actualLine: String,
        val modFile: String
    )

    // Some helper extension
    private fun String.isBlank(): Boolean = all { it.isWhitespace() }

    /**
     * On any error in the entire mod processing, we log and throw an exception.
     */
    private fun handleProcessingError(e: Exception) {
        val errorMsg = "Failed to process mod content: ${e.message}"
        error(errorMsg, e)
        warnings.add(MergeWarning.GeneralWarning(message = errorMsg, modFile = null))
        throw ModContentProcessingException("Failed to process mod content", e)
    }

    /**
     * Called if we have an error for a particular line
     */
    private fun handleLineProcessingError(
        e: Exception,
        index: Int,
        lines: List<String>,
        mappedDef: MappedModDefinition
    ) {
        val errorMsg = "Error processing line $index in ${mappedDef.modFile.name}: ${e.message}"
        error(errorMsg, e)
        debug("Problematic line content: ${lines.getOrNull(index)}")
        throw ModContentProcessingException(errorMsg, e)
    }
}

class ModContentProcessingException(message: String, cause: Throwable? = null) : Exception(message, cause)
