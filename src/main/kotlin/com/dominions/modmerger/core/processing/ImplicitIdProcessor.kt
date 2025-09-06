package com.dominions.modmerger.core.processing

import com.dominions.modmerger.constants.ModPatterns
import com.dominions.modmerger.domain.EntityType
import com.dominions.modmerger.domain.MappedModDefinition
import com.dominions.modmerger.domain.ModDefinition
import com.dominions.modmerger.infrastructure.Logging

class ImplicitIdProcessor : Logging {
    private val nextImplicitIndex = mutableMapOf<Pair<EntityType, String>, Int>()  // (type, modName) -> next index

    data class ProcessedLine(
        val newLine: String,
        val remapComment: String?,
        val newId: Long
    )

    fun cleanIndices() {
        nextImplicitIndex.clear()
    }

    fun processImplicitDefinition(
        line: String,
        type: EntityType,
        modDef: ModDefinition,
        mappedDef: MappedModDefinition
    ): ProcessedLine? {
        // Get next implicit index for this (type, mod) pair
        val key = type to modDef.name
        val index = nextImplicitIndex.getOrDefault(key, 0)
        nextImplicitIndex[key] = index + 1

        // Get assigned ID for this implicit index
        val entityDef = modDef.getDefinition(type)
        val assignedId = entityDef.getAssignedIdForImplicitIndex(index) ?: run {
            debug("No assigned ID found for $type implicit index=$index in mod=${modDef.name}")
            return null
        }

        // Get potentially remapped ID
        val newId = mappedDef.getMapping(type, assignedId)

        // Clean the line before processing
        val cleanedLine = cleanUnnumberedLine(line)

        // Process based on entity type
        val (newLine, comment) = when (type) {
            EntityType.SPELL -> processSpellLine(cleanedLine, newId)
            else -> processRegularEntity(cleanedLine, newId, type)
        }

        debug("Processed line: $line -> $newLine", useDispatcher = false)

        return ProcessedLine(
            newLine = newLine,
            remapComment = comment,
            newId = newId
        )
    }

    private fun processSpellLine(line: String, newId: Long): Pair<String, String> {
        val matchResult = ModPatterns.NEW_UNNUMBERED_SPELL.find(line.trim())
        val remainingContent = matchResult?.groupValues?.get(1) ?: ""

        return Pair(
            "#selectspell $newId$remainingContent",
            "-- MOD MERGER: Converted #newspell to #selectspell with assigned ID $newId"
        )
    }

    private fun processRegularEntity(line: String, newId: Long, type: EntityType): Pair<String, String> {
        val trimmedLine = line.trim()

        // Check if transformation from #new to #select occurs
        val wasTransformed = trimmedLine.startsWith("#new") &&
                !trimmedLine.startsWith("#newmonster") &&
                !trimmedLine.startsWith("#newsite")

        // Transform #new commands to #select commands (excluding specific exceptions)
        val transformedLine = if (wasTransformed) {
            val command = trimmedLine.split(" ")[0]
            val selectCommand = "#select${command.substring(4)}"
            line.replace(command, selectCommand)
        } else {
            line
        }

        // Insert the ID after the command
        val hashIndex = transformedLine.indexOf('#')
        val commandEndIndex = transformedLine.indexOf(' ', hashIndex)
        val finalLine = if (commandEndIndex != -1) {
            "${transformedLine.substring(0, commandEndIndex)} $newId${transformedLine.substring(commandEndIndex)}"
        } else {
            "$transformedLine $newId"
        }

        // Generate accurate comment based on what actually happened
        val typeName = type.name.lowercase()
        val comment = if (wasTransformed) {
            "-- MOD MERGER: Converted #new$typeName to #select$typeName with assigned ID $newId"
        } else {
            "-- MOD MERGER: Added ID $newId to #new$typeName"
        }

        return Pair(finalLine, comment)
    }


    /**
     * Cleans unnumbered definition lines by removing extraneous content after the command
     * while preserving comments that start with "--".
     *
     * @param line The original line to clean
     * @return Cleaned line with only the command and optional comment
     *
     * Examples:
     * - `#newmonster 3152` → `#newmonster`
     * - `#newspell "Test" -- comment` → `#newspell -- comment`
     * - `#newitem` → `#newitem` (unchanged)
     */
    private fun cleanUnnumberedLine(line: String): String {
        // Early return for lines that don't need cleaning
        if (line.isBlank() || !line.contains('#')) return line

        val leadingWhitespace = line.takeWhile { it.isWhitespace() }
        val trimmed = line.trimStart()

        if (!trimmed.startsWith('#')) return line

        // Find command end (first whitespace after #command)
        val commandEndIndex = trimmed.indexOfFirst { it.isWhitespace() }
        if (commandEndIndex == -1) return line // Already clean

        val command = trimmed.substring(0, commandEndIndex)
        val remainder = trimmed.substring(commandEndIndex)

        // Find comment start, accounting for quotes and escaping
        val commentIndex = findCommentStart(remainder)

        val cleanedLine = if (commentIndex != -1) {
            // Preserve comment: "#command -- comment"
            "$leadingWhitespace$command ${remainder.substring(commentIndex)}"
        } else {
            // No comment: "#command"
            "$leadingWhitespace$command"
        }

        // Debug log if we actually removed non-whitespace content
        if (cleanedLine != line) {
            val removedContent = if (commentIndex != -1) {
                remainder.substring(0, commentIndex).trim()
            } else {
                remainder.trim()
            }

            if (removedContent.isNotEmpty()) {
                debug("Cleaned unnumbered line: removed '$removedContent' from '$line' -> '$cleanedLine'", useDispatcher = false)
            }
        }

        return cleanedLine
    }

    /**
     * Finds the start position of a comment marker "--" that is not inside quotes.
     * Properly handles escaped quotes within strings.
     *
     * @param text The text to search within
     * @return Index of comment start, or -1 if no comment found
     *
     * Examples:
     * - `123 -- comment` → 4
     * - `"quoted -- text" -- comment` → 17
     * - `no comment here` → -1
     */
    private fun findCommentStart(text: String): Int {
        var insideQuotes = false
        var escapeNext = false

        for (i in 0 until text.length - 1) {
            val char = text[i]
            when (char) {
                '\\' -> escapeNext = !escapeNext
                '"' -> if (!escapeNext) insideQuotes = !insideQuotes
                '-' -> {
                    if (!insideQuotes && !escapeNext &&
                        i < text.length - 1 && text[i + 1] == '-') {
                        return i
                    }
                }
            }

            // Reset escape flag for non-backslash characters
            if (char != '\\') escapeNext = false
        }

        return -1
    }
}