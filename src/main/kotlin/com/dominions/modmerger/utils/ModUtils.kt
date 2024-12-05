package com.dominions.modmerger.utils

import com.dominions.modmerger.constants.RegexWrapper
import com.dominions.modmerger.infrastructure.Logging
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

object ModUtils : Logging {
    // Cache for compiled patterns and matchers
    private val patternCache = ConcurrentHashMap<String, RegexWrapper>()
    private val numberReplaceCache = ConcurrentHashMap<String, Pattern>()

    // Reusable pattern for number validation
    private val digitPattern = Pattern.compile("\\d+")
    private val digitPredicate = digitPattern.asPredicate()


    fun extractString(line: String, pattern: Regex): String? {
        val wrapper = getOrCreateWrapper(pattern)
        return wrapper.getMatcher(line).let { matcher ->
            if (matcher.find()) matcher.group(1) else null
        }
    }

    fun extractId(line: String, pattern: Regex): Long? {
        val wrapper = getOrCreateWrapper(pattern)
        val matcher = wrapper.getMatcher(line)

        if (!matcher.find()) return null

        val idString = matcher.group(1) ?: return null

        // Fast path - check if string contains only digits
        if (!digitPredicate.test(idString)) return null

        return try {
            idString.toLong()
        } catch (e: NumberFormatException) {
            null
        }
    }

    fun extractName(line: String, pattern: Regex): String? {
        val wrapper = getOrCreateWrapper(pattern)
        return try {
            wrapper.getMatcher(line).let { matcher ->
                if (matcher.find()) {
                    try {
                        matcher.group("name")
                    } catch (e: IllegalArgumentException) {
                        warn(
                            "Found match but no 'name' group in pattern: $pattern for line: $line",
                            useDispatcher = false
                        )
                        null
                    }
                } else null
            }
        } catch (e: IllegalArgumentException) {
            error("Invalid regex pattern, missing 'name' group: $pattern", e, useDispatcher = false)
            debug("Line being processed: $line", useDispatcher = false)
            throw ModProcessingException("Failed to extract name: pattern $pattern is missing 'name' group", e)
        }
    }

    fun replaceId(line: String, oldId: Long, newId: Long): String {
        val patternString = "\\b$oldId\\b"
        val pattern = numberReplaceCache.computeIfAbsent(patternString) {
            Pattern.compile(it)
        }
        return pattern.matcher(line).replaceAll(newId.toString())
    }

    fun validateIdString(idString: String): Boolean {
        if (idString.isEmpty()) return false
        return digitPredicate.test(idString)
    }

    fun isValidEntityId(id: Long, minId: Long, maxId: Long): Boolean {
        return id in minId..maxId
    }

    fun getOrCreateWrapper(pattern: Regex): RegexWrapper {
        return patternCache.computeIfAbsent(pattern.pattern) {
            RegexWrapper.of(it)
        }
    }
}

class ModProcessingException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

// Extension function to make migration easier
fun Regex.toWrapper(): RegexWrapper = ModUtils.getOrCreateWrapper(this)