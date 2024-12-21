package com.dominions.modmerger.core.writing

import com.dominions.modmerger.constants.ModPatterns
import com.dominions.modmerger.core.processing.EntityProcessor
import com.dominions.modmerger.core.processing.SpellBlockProcessor
import com.dominions.modmerger.domain.MappedModDefinition
import com.dominions.modmerger.domain.MergeWarning
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
        var processedLines: MutableList<String> = mutableListOf()
    )

    fun processModContent(
        mappedDefinitions: Map<String, MappedModDefinition>,
        writer: Writer
    ): List<MergeWarning> {
        warnings.clear()
        try {
            debug("Starting to process ${mappedDefinitions.size} mod definitions")
            val totalStartTime = System.currentTimeMillis()

            mappedDefinitions.forEach { (name, mappedDef) ->
                processModDefinition(name, mappedDef, writer)
            }

            writer.write("\n-- End merged content\n")
            logProcessingCompletion(totalStartTime)
        } catch (e: Exception) {
            handleProcessingError(e)
        }
        return warnings
    }

    private fun processModDefinition(name: String, mappedDef: MappedModDefinition, writer: Writer) {
        val modStartTime = System.currentTimeMillis()
        debug("Processing mod: $name with ${mappedDef.modFile.content.lines().size} lines")
        writer.write("\n-- Begin content from mod: $name\n")
        processModFile(mappedDef, writer)
        info("Processed mod '$name' in ${System.currentTimeMillis() - modStartTime} ms")
    }

    private fun processModFile(mappedDef: MappedModDefinition, writer: Writer) {
        val lines = mappedDef.modFile.content.lines()
        val context = ModProcessingContext()

        debug("Starting to process mod file: ${mappedDef.modFile.name}")
        trace("File has ${lines.size} lines")

        processLines(lines, context, mappedDef)
        // Log assignments before writing the processed lines
        entityProcessor.logIdAssignmentsForMod(mappedDef.modFile.name)
        writeProcessedLines(context.processedLines, writer, mappedDef.modFile.name)

        debug("Processed ${context.processedLines.size} lines for ${mappedDef.modFile.name}")
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
        // debug("Found spell block start at line $currentIndex: $line")
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

    // Add these as class properties at the top of ModContentWriter
    private data class BlockContext(
        val startLine: Int,
        val blockType: String,
        val actualLine: String,
        val modFile: String
    )

    private fun writeProcessedLines(lines: List<String>, writer: Writer, modFileName: String) {
        // First validate blocks and collect warnings
        validateBlocks(lines, modFileName)

        // Then proceed with normal line writing
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
                // Check for block starts
                trimmedLine.startsWith("#select") ||
                        trimmedLine.startsWith("#new") -> {
                    val blockType = when {
                        trimmedLine.startsWith("#select") -> "select"
                        else -> "new"
                    }
                    blockStack.add(BlockContext(index + 1, blockType, trimmedLine, modFileName))
                }

                trimmedLine.startsWith("#end") -> {
                    if (blockStack.isNotEmpty()) {
                        blockStack.removeAt(blockStack.lastIndex)
                    }
                }

                // Check for new blocks while previous are still open
                (trimmedLine.startsWith("#select") || trimmedLine.startsWith("#new")) &&
                        blockStack.isNotEmpty() -> {
                    val lastBlock = blockStack.last()
                    warnings.add(
                        MergeWarning.GeneralWarning(
                            message = "Found new block '${trimmedLine}' at line ${index + 1} while previous " +
                                    "'${lastBlock.blockType}' block from line ${lastBlock.startLine} " +
                                    "(${lastBlock.modFile}) is still open (missing #end)",
                            modFile = null
                        )
                    )
                }
            }
        }

        blockStack.forEach { context ->
            warnings.add(
                MergeWarning.GeneralWarning(
                    message = "Unclosed '${context.blockType}' block from line ${context.startLine}: " + context.actualLine +
                            " (${context.modFile})",
                    modFile = modFileName  // Use the actual mod file here
                )
            )
        }
    }

    private fun identifySkippableEnds(lines: List<String>): Set<Int> {
        val skippableEnds = mutableSetOf<Int>()

        // Keep track of the last seen #end and what separates it from current position
        var lastEndIndex = -1
        var onlyWhitespaceOrEmptySinceLastEnd = true

        lines.forEachIndexed { index, line ->
            val trimmedLine = line.trim()

            when {
                trimmedLine == "#end" -> {
                    if (lastEndIndex != -1 && onlyWhitespaceOrEmptySinceLastEnd) {
                        // If we've only seen whitespace or empty lines since the last #end,
                        // this #end should be removed
                        skippableEnds.add(index)
                    } else {
                        // This is either the first #end or there was other content since the last one
                        lastEndIndex = index
                        onlyWhitespaceOrEmptySinceLastEnd = true
                    }
                }
                trimmedLine.isEmpty() || trimmedLine.isBlank() -> {
                    // Keep tracking that we're only seeing whitespace/empty lines
                    onlyWhitespaceOrEmptySinceLastEnd = onlyWhitespaceOrEmptySinceLastEnd && lastEndIndex != -1
                }
                else -> {
                    // Any other content means the next #end will be preserved
                    lastEndIndex = -1
                    onlyWhitespaceOrEmptySinceLastEnd = false
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
            line.isEmpty() && index == 0 -> true
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
        ModPatterns.MOD_DESCRIPTION_START.matches(line) && !ModPatterns.MOD_DESCRIPTION_LINE.matches(line)

    private fun isModInfoLine(line: String): Boolean =
        line.matches(ModPatterns.MOD_NAME) ||
                line.matches(ModPatterns.MOD_DESCRIPTION_LINE) ||
                line.matches(ModPatterns.MOD_ICON_LINE) ||
                line.matches(ModPatterns.MOD_VERSION_LINE) ||
                line.matches(ModPatterns.MOD_DOMVERSION_LINE)

    private fun processRegularLine(line: String, mappedDef: MappedModDefinition, context: ModProcessingContext) {
        // Check if the previous processed line was a comment (starts with --)
        val previousLineWasComment = context.processedLines.lastOrNull()?.startsWith("--") == true

        // Check if the current line does NOT start with a special command
        val needsCommentPrefix = previousLineWasComment &&
                !line.trimStart().startsWith("#") &&
                !line.trimStart().startsWith("--")

        val processedLine = if (needsCommentPrefix) {
            "-- $line"
        } else {
            line
        }

        val processed = entityProcessor.processEntity(
            line = processedLine,
            mappedDef = mappedDef,
            remapCommentWriter = { type, oldId, newId ->
                if (oldId == -1L) {
                    "-- MOD MERGER: Assigned new ID $newId for ${type.name}, previously unassigned"
                } else {
                    "-- MOD MERGER: Remapped ${type.name} $oldId -> $newId"
                }
            },
            modName = mappedDef.modFile.name
        )

        processed.remapComment?.let { context.processedLines.add(it) }
        context.processedLines.add(processed.line)
    }

    private fun handleDamageMapping(
        line: String,
        block: SpellBlockProcessor.SpellBlock,
        context: ModProcessingContext
    ) {
        // debug("Processing damage mapping for line: $line")
        // debug("Block state: effect=${block.effect}, damageMapping=${block.damageMapping}")

        block.damageMapping?.let { (oldId, newId) ->
            // debug("Found mapping $oldId -> $newId")
            val comment = createRemapComment(oldId, newId)
            // debug("Created comment: $comment")
            context.processedLines.add(comment)

            val newLine = ModUtils.replaceId(line, oldId, newId)
            // debug("Created new line: $newLine")
            context.processedLines.add(newLine)
        } ?: run {
            // debug("No mapping needed, using original line")
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
        // debug("Processing spell block starting at line $startIndex")

        val spellContext = SpellProcessingContext()

        while (currentIndex < lines.size) {
            val line = lines[currentIndex]
            val trimmedLine = line.trim()
            // debug("Processing spell block line: $trimmedLine")

            when {
                ModPatterns.END.matches(trimmedLine) -> {
                    return handleSpellBlockEnd(line, currentIndex, context)
                }
                trimmedLine.startsWith("#damage") -> {
                    spellContext.currentDamageLine = line
                    spellContext.damageLineIndex = context.processedLines.size
                    // Process to store the damage value
                    val block = spellBlockProcessor.processSpellLine(line, mappedDef)
                    if (block.damageMapping != null) {
                        // If we already have an effect, handle the mapping now
                        handleDamageMapping(line, block, context)
                        spellContext.damageMappingResolved = true
                    } else {
                        // Store line for now, might be remapped later
                        context.processedLines.add(line)
                    }
                }
                trimmedLine.startsWith("#effect") -> {
                    // Process effect line
                    val block = spellBlockProcessor.processSpellLine(line, mappedDef)
                    // Add effect line as-is
                    context.processedLines.add(line)

                    // If we had a pending damage line and now have a mapping
                    if (!spellContext.damageMappingResolved &&
                        spellContext.currentDamageLine != null &&
                        spellContext.damageLineIndex >= 0) {
                        // Get the updated block state after the effect was processed
                        val updatedBlock = spellBlockProcessor.currentBlock
                        if (updatedBlock?.damageMapping != null) {
                            // Remove the original damage line
                            context.processedLines.removeAt(spellContext.damageLineIndex)
                            // Add the remapped version
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

    private fun handleSpellBlockEnd(line: String, currentIndex: Int, context: ModProcessingContext): Int {
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

    private fun handleLineProcessingError(e: Exception, index: Int, lines: List<String>, mappedDef: MappedModDefinition) {
        val errorMsg = "Error processing line $index in ${mappedDef.modFile.name}: ${e.message}"
        error(errorMsg, e)
        debug("Problematic line content: ${lines.getOrNull(index)}")
        throw ModContentProcessingException(errorMsg, e)
    }
}

class ModContentProcessingException(message: String, cause: Throwable? = null) : Exception(message, cause)