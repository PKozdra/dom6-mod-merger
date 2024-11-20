package com.dominions.modmerger.utils

object ModUtils {
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