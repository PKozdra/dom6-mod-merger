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

class ModContentWriter(
    private val entityProcessor: EntityProcessor,
    private val spellBlockProcessor: SpellBlockProcessor = SpellBlockProcessor(),
) : Logging {

    private val warnings = mutableListOf<MergeWarning>()

    private data class ModProcessingContext(
        var inMultilineDescription: Boolean = false,
        var processedLines: MutableList<String> = mutableListOf(),
        val modDefinition: ModDefinition
    )

    fun processModContent(
        mappedDefinitions: Map<String, MappedModDefinition>,
        modDefinitions: Map<String, ModDefinition>,
        writer: Writer
    ): List<MergeWarning> {
        warnings.clear()
        try {
            debug("Starting to process ${mappedDefinitions.size} mod definitions")
            val totalStartTime = System.currentTimeMillis()

            mappedDefinitions.forEach { (name, mappedDef) ->
                val originalDef = modDefinitions[name]
                    ?: throw ModContentProcessingException("Cannot find original definition for mod $name")
                processModDefinition(name, mappedDef, originalDef, writer)
            }

            writer.write("\n-- End merged content\n")
            logProcessingCompletion(totalStartTime)
        } catch (e: Exception) {
            handleProcessingError(e)
        }
        return warnings
    }

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
        info("Processed mod '$name' in ${System.currentTimeMillis() - modStartTime} ms")
    }

    private fun processModFile(
        mappedDef: MappedModDefinition,
        originalDef: ModDefinition,
        writer: Writer
    ) {
        // First pass - remove mod info blocks
        val cleanedLines = removeModInfoBlocks(mappedDef.modFile.content.lines())

        // Second pass - normal line filtering
        val filteredLineIndices = mutableSetOf<Int>()
        var inMultilineBlock = false
        var lastLineWasCommand = false

        cleanedLines.forEachIndexed { index, line ->
            val trimmedLine = line.trimStart()

            when {
                // 1) Keep blank lines only if they're between command blocks
                line.isBlank() -> {
                    if (lastLineWasCommand) {
                        filteredLineIndices.add(index)
                    }
                }

                // 2) Keep lines if we're currently in a multiline block
                inMultilineBlock -> {
                    filteredLineIndices.add(index)
                    // Look for a closing quote in the portion before any comment
                    val lineWithoutComment = line.split("--")[0]
                    if (lineWithoutComment.contains("\"")) {
                        inMultilineBlock = false
                    }
                    lastLineWasCommand = true
                }

                // 3) Comments (--) or commands (#) are kept
                trimmedLine.startsWith("--") || trimmedLine.startsWith("#") -> {
                    filteredLineIndices.add(index)
                    // Potentially start a multiline block if there's an odd number of quotes
                    if (trimmedLine.startsWith("#") && trimmedLine.contains("\"")) {
                        val lineWithoutComment = trimmedLine.split("--")[0]
                        val quoteCount = lineWithoutComment.count { it == '"' }
                        if (quoteCount % 2 == 1) {
                            inMultilineBlock = true
                        }
                    }
                    lastLineWasCommand = true
                }

                // 4) Anything else is ignored
                else -> {
                    lastLineWasCommand = false
                }
            }
        }

        val secondPassLines = cleanedLines.filterIndexed { index, _ ->
            filteredLineIndices.contains(index)
        }

        // Now proceed with normal line processing
        val context = ModProcessingContext(modDefinition = originalDef)
        debug("Starting to process mod file: ${mappedDef.modFile.name}")
        trace("File has ${cleanedLines.size} lines")

        processLines(secondPassLines, context, mappedDef)

        writeProcessedLines(context.processedLines, writer, mappedDef.modFile.name)

        debug("Processed ${context.processedLines.size} lines for ${mappedDef.modFile.name}")
    }

    /**
     * First-pass removal of #modinfo blocks: #description, #icon, #version, #domversion, etc.
     * This uses a "total quotes" approach to ensure we remove entire multiline blocks,
     * even if they span multiple lines or contain multiple quotes on a single line.
     */
    fun removeModInfoBlocks(lines: List<String>): List<String> {
        val result = mutableListOf<String>()

        var inModInfoBlock = false
        var totalQuotesInBlock = 0  // counts quotes since entering the block

        for (originalLine in lines) {
            // 1) Normalize fancy quotes
            val line = originalLine
                .replace('“', '"')
                .replace('”', '"')

            val trimmed = line.trimStart()

            if (inModInfoBlock) {
                // We're skipping lines. Count quotes (unless the line is fully commented out).
                if (!trimmed.startsWith("--")) {
                    val quotesHere = line.count { it == '"' }
                    totalQuotesInBlock += quotesHere

                    // If total quotes is back to even => we've closed the block
                    if (totalQuotesInBlock % 2 == 0) {
                        inModInfoBlock = false
                        totalQuotesInBlock = 0
                    }
                }
                // skip this line regardless
                continue
            }

            // Not currently skipping => check if this is a mod info command
            if (isModInfoLine(trimmed)) {
                // e.g. #description, #icon, #version, #domversion, #modname
                val quotesHere = line.count { it == '"' }

                // If odd => we've opened a block
                if (quotesHere % 2 == 1) {
                    inModInfoBlock = true
                    totalQuotesInBlock = quotesHere
                }
                // skip current line
                continue
            }

            // Otherwise, keep the line
            result.add(line)
        }

        return result
    }

    private fun processLines(lines: List<String>, context: ModProcessingContext, mappedDef: MappedModDefinition) {
        var index = 0
        while (index < lines.size) {
            try {
                index = processNextLine(lines, index, context, mappedDef)
            } catch (e: Exception) {
                handleLineProcessingError(e, index, lines, mappedDef)
            }
        }
    }

    private fun processNextLine(
        lines: List<String>,
        currentIndex: Int,
        context: ModProcessingContext,
        mappedDef: MappedModDefinition
    ): Int {
        val line = ModUtils.removeUnreadableCharacters(lines[currentIndex])
        trace(getLineProcessingMessage(currentIndex, line))

        return when {
            shouldSkipLine(line, currentIndex, context) -> currentIndex + 1
            isSpellBlockStart(line) -> handleSpellBlock(line, lines, currentIndex, mappedDef, context)
            else -> handleRegularLine(line, mappedDef, context, currentIndex)
        }
    }

    private fun isSpellBlockStart(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.startsWith("#newspell") ||
                trimmed.matches(Regex("""#selectspell\s+\d+.*"""))
    }

    private fun handleSpellBlock(
        line: String,
        lines: List<String>,
        currentIndex: Int,
        mappedDef: MappedModDefinition,
        context: ModProcessingContext
    ): Int {
        spellBlockProcessor.startNewBlock(line)
        context.processedLines.add(line)
        return processSpellBlock(lines, currentIndex + 1, mappedDef, context)
    }

    private fun handleRegularLine(
        line: String,
        mappedDef: MappedModDefinition,
        context: ModProcessingContext,
        currentIndex: Int
    ): Int {
        processRegularLine(line, mappedDef, context)
        return currentIndex + 1
    }

    // The rest is mostly unchanged

    private data class BlockContext(
        val startLine: Int,
        val blockType: String,
        val actualLine: String,
        val modFile: String
    )

    private fun writeProcessedLines(lines: List<String>, writer: Writer, modFileName: String) {
        // Validate open blocks
        validateBlocks(lines, modFileName)

        // Identify which #end lines are skippable
        val skippableEnds = identifySkippableEnds(lines)

        lines.forEachIndexed { index, line ->
            if (!skippableEnds.contains(index)) {
                writer.write("$line\n")
            }
        }
    }

    private fun validateBlocks(lines: List<String>, modFileName: String) {
        val blockStack = mutableListOf<BlockContext>()

        lines.forEachIndexed { index, line ->
            val trimmedLine = line.trim()

            when {
                trimmedLine.startsWith("#select") || trimmedLine.startsWith("#new") -> {
                    val blockType = if (trimmedLine.startsWith("#select")) "select" else "new"
                    blockStack.add(BlockContext(index + 1, blockType, trimmedLine, modFileName))
                }

                trimmedLine.startsWith("#end") -> {
                    if (blockStack.isNotEmpty()) {
                        blockStack.removeAt(blockStack.lastIndex)
                    }
                }

                (trimmedLine.startsWith("#select") || trimmedLine.startsWith("#new")) &&
                        blockStack.isNotEmpty() -> {
                    val lastBlock = blockStack.last()
                    warnings.add(
                        MergeWarning.GeneralWarning(
                            message = "Found new block '$trimmedLine' at line ${index + 1} while previous '${lastBlock.blockType}' block from line ${lastBlock.startLine} (${lastBlock.modFile}) is still open (missing #end)",
                            modFile = null
                        )
                    )
                }
            }
        }

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

    private fun identifySkippableEnds(lines: List<String>): Set<Int> {
        val skippableEnds = mutableSetOf<Int>()
        var lastEndIndex = -1
        var onlyWhitespaceSinceLastEnd = true

        lines.forEachIndexed { index, line ->
            val trimmedLine = line.trim()
            when {
                trimmedLine == "#end" -> {
                    if (lastEndIndex != -1 && onlyWhitespaceSinceLastEnd) {
                        skippableEnds.add(index)
                    } else {
                        lastEndIndex = index
                        onlyWhitespaceSinceLastEnd = true
                    }
                }
                trimmedLine.isEmpty() || trimmedLine.isBlank() -> {
                    onlyWhitespaceSinceLastEnd = onlyWhitespaceSinceLastEnd && lastEndIndex != -1
                }
                else -> {
                    lastEndIndex = -1
                    onlyWhitespaceSinceLastEnd = false
                }
            }
        }
        return skippableEnds
    }

    // Helper extension function
    private fun String.isBlank(): Boolean = all { it.isWhitespace() }

    private fun shouldSkipLine(line: String, index: Int, context: ModProcessingContext): Boolean {
        val trimmedLine = line.trim()
        return when {
            index == 0 && line.isEmpty() -> true
            context.inMultilineDescription -> {
                if (line.contains("\"")) context.inMultilineDescription = false
                true
            }
            isDescriptionStart(trimmedLine) -> {
                context.inMultilineDescription = !line.endsWith("\"")
                true
            }
            isModInfoLine(trimmedLine) -> true
            else -> false
        }
    }

    private fun isDescriptionStart(line: String): Boolean =
        ModPatterns.MOD_DESCRIPTION_START.matches(line) &&
                !ModPatterns.MOD_DESCRIPTION_LINE.matches(line)

    private fun isModInfoLine(trimmedLine: String): Boolean {
        val lower = trimmedLine.lowercase()
        return lower.startsWith("#description") ||
                lower.startsWith("#modname")     ||
                lower.startsWith("#icon")        ||
                lower.startsWith("#version")     ||
                lower.startsWith("#domversion")
    }

    private fun processRegularLine(
        line: String,
        mappedDef: MappedModDefinition,
        context: ModProcessingContext
    ) {
        val previousLineWasComment = context.processedLines.lastOrNull()?.startsWith("--") == true
        val needsCommentPrefix = previousLineWasComment &&
                !line.trimStart().startsWith("#") &&
                !line.trimStart().startsWith("--")

        val processedLine = if (needsCommentPrefix) {
            "-- $line"
        } else {
            line
        }

        val processed = entityProcessor.processEntity(
            line = line,
            mappedDef = mappedDef,
            remapCommentWriter = { type, oldId, newId ->
                if (oldId == -1L) {
                    "-- MOD MERGER: Assigned new ID $newId for ${type.name}"
                } else {
                    "-- MOD MERGER: Remapped ${type.name} $oldId -> $newId"
                }
            },
            modDef = context.modDefinition
        )

        processed.remapComment?.let { context.processedLines.add(it) }
        context.processedLines.add(processed.line)
    }

    private fun handleDamageMapping(
        line: String,
        block: SpellBlockProcessor.SpellBlock,
        context: ModProcessingContext
    ) {
        block.damageMapping?.let { (oldId, newId) ->
            val comment = createRemapComment(oldId, newId)
            context.processedLines.add(comment)
            val newLine = ModUtils.replaceId(line, oldId, newId)
            context.processedLines.add(newLine)
        } ?: run {
            context.processedLines.add(line)
        }
    }

    private data class SpellProcessingContext(
        var currentDamageLine: String? = null,
        var damageMappingResolved: Boolean = false,
        var damageLineIndex: Int = -1
    )

    private fun processSpellBlock(
        lines: List<String>,
        startIndex: Int,
        mappedDef: MappedModDefinition,
        context: ModProcessingContext
    ): Int {
        var currentIndex = startIndex
        val spellContext = SpellProcessingContext()

        while (currentIndex < lines.size) {
            val line = lines[currentIndex]
            val trimmedLine = line.trim()

            when {
                ModPatterns.END.matches(trimmedLine) -> {
                    return handleSpellBlockEnd(line, currentIndex, context)
                }
                trimmedLine.startsWith("#damage") -> {
                    spellContext.currentDamageLine = line
                    spellContext.damageLineIndex = context.processedLines.size
                    val block = spellBlockProcessor.processSpellLine(line, mappedDef)
                    if (block.damageMapping != null) {
                        handleDamageMapping(line, block, context)
                        spellContext.damageMappingResolved = true
                    } else {
                        context.processedLines.add(line)
                    }
                }
                trimmedLine.startsWith("#effect") -> {
                    val block = spellBlockProcessor.processSpellLine(line, mappedDef)
                    context.processedLines.add(line)

                    // If we had a pending damage line and now have a mapping
                    if (!spellContext.damageMappingResolved &&
                        spellContext.currentDamageLine != null &&
                        spellContext.damageLineIndex >= 0
                    ) {
                        val updatedBlock = spellBlockProcessor.currentBlock
                        if (updatedBlock?.damageMapping != null) {
                            context.processedLines.removeAt(spellContext.damageLineIndex)
                            handleDamageMapping(spellContext.currentDamageLine!!, updatedBlock, context)
                            spellContext.damageMappingResolved = true
                        }
                    }
                }
                else -> {
                    processRegularLine(line, mappedDef, context)
                }
            }
            currentIndex++
        }
        return currentIndex
    }

    private fun handleSpellBlockEnd(
        line: String,
        currentIndex: Int,
        context: ModProcessingContext
    ): Int {
        trace("Found spell block end at line $currentIndex")
        context.processedLines.add(line)
        spellBlockProcessor.currentBlock = null
        return currentIndex + 1
    }

    private fun createRemapComment(oldId: Long, newId: Long): String {
        val entityType = if (oldId > 0) "Monster" else "Montag"
        val absOldId = kotlin.math.abs(oldId)
        val absNewId = kotlin.math.abs(newId)
        return "-- MOD MERGER: Remapped $entityType $absOldId -> $absNewId"
    }

    private fun getLineProcessingMessage(index: Int, line: String): String =
        "Processing line $index: ${if (line.length > 50) line.take(50) + "..." else line}"

    private fun logProcessingCompletion(startTime: Long) {
        val totalTime = System.currentTimeMillis() - startTime
        info("Total mod content processing time: $totalTime ms")
        info("Successfully wrote mod content with ${warnings.size} warnings")
    }

    private fun handleProcessingError(e: Exception) {
        val errorMsg = "Failed to process mod content: ${e.message}"
        error(errorMsg, e)
        warnings.add(
            MergeWarning.GeneralWarning(
                message = errorMsg,
                modFile = null
            )
        )
        throw ModContentProcessingException("Failed to process mod content", e)
    }

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