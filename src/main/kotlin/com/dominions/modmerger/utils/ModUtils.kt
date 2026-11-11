package com.dominions.modmerger.utils

import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

object ModUtils {
    private val numberReplaceCache = ConcurrentHashMap<String, Pattern>()

    fun extractString(line: String, pattern: Regex): String? =
        pattern.find(line)?.groupValues?.getOrNull(1)

    fun extractId(line: String, pattern: Regex): Long? =
        extractString(line, pattern)?.toLongOrNull()

    fun replaceId(line: String, oldId: Long, newId: Long): String {
        // First check if this is a command-style line (starts with #)
        if (line.trimStart().startsWith("#")) {
            // Extract the command part (#damage, #monster, etc.)
            val command = line.substringBefore(" ")

            // Create pattern that matches command followed by ID and potentially comments
            // Note the \\s* to handle optional space before comment
            val patternString = "${Pattern.quote(command)}\\s+$oldId(\\s*.*)?$"
            val pattern = numberReplaceCache.computeIfAbsent(patternString) {
                Pattern.compile(it)
            }

            return pattern.matcher(line).replaceAll { matchResult ->
                val rest = matchResult.group(1) ?: ""
                "$command $newId$rest"
            }
        } else {
            // For non-command lines (like comments), use word boundary matching
            val patternString = "\\b$oldId\\b"
            val pattern = numberReplaceCache.computeIfAbsent(patternString) {
                Pattern.compile(it)
            }
            return pattern.matcher(line).replaceAll(newId.toString())
        }
    }

    fun isValidEntityId(id: Long, minId: Long, maxId: Long): Boolean {
        return id in minId..maxId
    }

    fun removeUnreadableCharacters(line: String): String =
        line.trim().removePrefix("\uFEFF")
}
