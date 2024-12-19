package com.dominions.modmerger.infrastructure

import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle.index
import java.util.prefs.Preferences

object PreferencesManager : Logging {
    private val preferences = Preferences.userRoot().node("com/dominions/modmerger")
    private const val MAX_VALUE_LENGTH = 8000  // Safe limit for most systems
    private const val PATHS_DELIMITER = "||"
    // In PreferencesManager
    const val DEFAULT_FONT_SIZE = 12
    const val MIN_FONT_SIZE = 6
    const val MAX_FONT_SIZE = 48

    var fontSize: Int
        get() = preferences.getInt("output.fontSize", DEFAULT_FONT_SIZE)
        set(value) = preferences.putInt("output.fontSize", value.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE))

    // Log Level Preferences
    fun isLogLevelEnabled(level: LogLevel): Boolean {
        return preferences.getBoolean(
            "loglevel.${level.name}",
            level !in listOf(LogLevel.DEBUG, LogLevel.TRACE)
        )
    }

    fun setLogLevelEnabled(level: LogLevel, enabled: Boolean) {
        preferences.putBoolean("loglevel.${level.name}", enabled)
    }

    // Word Wrap Preference
    var isWordWrapEnabled: Boolean
        get() = preferences.getBoolean("output.wordWrap", true)
        set(value) = preferences.putBoolean("output.wordWrap", value)

    // Auto-restore selections preference
    var isAutoRestoreEnabled: Boolean
        get() = preferences.getBoolean("mod.autoRestore", true)
        set(value) = preferences.putBoolean("mod.autoRestore", value)

    fun saveSelectedModPaths(paths: Set<String>) {
        if (paths.isEmpty()) {
            clearSelectedPaths()
            return
        }

        // Convert paths to chunks
        val pathString = paths.joinToString(PATHS_DELIMITER)
        val chunks = pathString.chunked(MAX_VALUE_LENGTH)

        preferences.putInt("mod.selectedPaths.count", chunks.size)
        chunks.forEachIndexed { i, chunk ->
            preferences.put("mod.selectedPaths.$i", chunk)
        }

        debug("Stored ${paths.size} mod selections", useDispatcher = false)
    }

    fun getSelectedModPaths(): Set<String> {
        val chunkCount = preferences.getInt("mod.selectedPaths.count", 0)
        if (chunkCount == 0) return emptySet()

        val pathString = buildString {
            for (i in 0 until chunkCount) {
                append(preferences.get("mod.selectedPaths.$i", ""))
            }
        }

        return pathString.split(PATHS_DELIMITER).toSet().also {
            debug("Loaded ${it.size} stored selections", useDispatcher = false)
        }
    }

    private fun clearSelectedPaths() {
        val chunkCount = preferences.getInt("mod.selectedPaths.count", 0)
        if (chunkCount > 0) {
            for (i in 0 until chunkCount) {
                preferences.remove("mod.selectedPaths.$i")
            }
            preferences.remove("mod.selectedPaths.count")
            debug("Cleared stored selections", useDispatcher = false)
        }
    }

    fun clearAll() {
        preferences.clear()
    }
}