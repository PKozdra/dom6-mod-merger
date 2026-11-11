package com.dominions.modmerger.core.parsing

data class TrackedLine(
    val content: String,
    val originalLineNumber: Int,
    val fileName: String
)

private val COMMAND = Regex("""#[A-Za-z0-9]""")

// the lines of a mod that the scan and the rewrite both work on
// mod info is removed, loose text is dropped, a text that runs over several lines is kept whole
fun commandLines(content: String, fileName: String): List<TrackedLine> =
    filterAndKeepLines(removeModInfoBlocks(content.removePrefix("\uFEFF").lines(), fileName))

fun String.isModInfoLine(): Boolean {
    val lower = this.lowercase()
    return lower.startsWith("#description") ||
            lower.startsWith("#modname") ||
            lower.startsWith("#icon") ||
            lower.startsWith("#version") ||
            lower.startsWith("#domversion")
}

/**
 * Removes mod info blocks with proper multiline quote handling.
 */
private fun removeModInfoBlocks(lines: List<String>, fileName: String): List<TrackedLine> {
    val result = mutableListOf<TrackedLine>()
    var inModInfoBlock = false
    var totalQuotesInBlock = 0

    lines.forEachIndexed { index, line ->
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

    lines.forEach { original ->
        val trackedLine =
            if (inMultilineBlock) original else original.copy(content = fromFirstCommand(original.content))
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

// the game runs a command wherever its # is, so "-#newmonster 8036" still makes a monster
// a line with a quote in front of the # stays loose text, the # may be inside a quoted text
private fun fromFirstCommand(line: String): String {
    val trimmed = line.trimStart()
    if (trimmed.startsWith("#") || trimmed.startsWith("--")) return line
    val command = COMMAND.find(line.substringBefore("--")) ?: return line
    val textInFront = line.substring(0, command.range.first)
    return if ('"' in textInFront) line else line.substring(command.range.first)
}
