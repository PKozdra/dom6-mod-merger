package com.dominions.modmerger.utils

import com.dominions.modmerger.infrastructure.Logging

object ModUtils : Logging {

    fun extractId(line: String, pattern: Regex): Long? {
        return pattern.find(line)?.groupValues?.get(1)?.let { idString ->
            // Only attempt conversion if the string contains only digits
            if (idString.all { it.isDigit() }) {
                try {
                    idString.toLong()
                } catch (e: NumberFormatException) {
                    null
                }
            } else {
                null
            }
        }
    }

    fun extractString(line: String, pattern: Regex): String? {
        return pattern.find(line)?.groupValues?.get(1)
    }

    fun extractName(line: String, pattern: Regex): String? {
        return try {
            pattern.find(line)?.let { matchResult ->
                matchResult.groups["name"]?.value ?: run {
                    warn("Found match but no 'name' group in pattern: $pattern for line: $line", useDispatcher = false)
                    null
                }
            }
        } catch (e: IllegalArgumentException) {
            error("Invalid regex pattern, missing 'name' group: $pattern", e, useDispatcher = false)
            debug("Line being processed: $line", useDispatcher = false)
            throw ModProcessingException("Failed to extract name: pattern $pattern is missing 'name' group", e)
        }
    }

    fun replaceId(line: String, oldId: Long, newId: Long): String {
        return line.replace("\\b$oldId\\b".toRegex(), newId.toString())
    }

    fun validateIdString(idString: String): Boolean {
        return idString.isNotEmpty() && idString.all { it.isDigit() }
    }

    fun isValidEntityId(id: Long, minId: Long, maxId: Long): Boolean {
        return id in minId..maxId
    }
}

class ModProcessingException(message: String, cause: Throwable? = null) :
    Exception(message, cause)