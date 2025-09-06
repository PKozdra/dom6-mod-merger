package com.dominions.modmerger.infrastructure

import com.dominions.modmerger.core.writing.config.ModOutputConfig
import com.dominions.modmerger.domain.ModPreset
import java.io.File
import java.util.prefs.Preferences

object PreferencesManager : Logging {
    private val preferences = Preferences.userRoot().node("com/dominions/modmerger")
    private const val MAX_VALUE_LENGTH = 8000  // Safe limit for most systems
    private const val PATHS_DELIMITER = "||"
    const val DEFAULT_FONT_SIZE = 12
    const val MIN_FONT_SIZE = 6
    const val MAX_FONT_SIZE = 48
    // Mod Output Config Preferences
    private const val CONFIG_MOD_NAME = "config.modName"
    private const val CONFIG_DISPLAY_NAME = "config.displayName"
    private const val CONFIG_DIRECTORY = "config.directory"
    private const val CUSTOM_STEAM_PATHS = "paths.customSteamPaths"

    private const val PRESET_COUNT_KEY = "presets.count"
    private const val PRESET_NAME_PREFIX = "presets.name."
    private const val PRESET_PATHS_PREFIX = "presets.paths."
    private const val PRESET_DATE_PREFIX = "presets.date."
    private const val MAX_PRESETS = 99

    var fontSize: Int
        get() = preferences.getInt("output.fontSize", DEFAULT_FONT_SIZE)
        set(value) = preferences.putInt("output.fontSize", value.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE))

    fun saveModConfig(config: ModOutputConfig) {
        preferences.put(CONFIG_MOD_NAME, config.modName)
        preferences.put(CONFIG_DISPLAY_NAME, config.displayName)
        preferences.put(CONFIG_DIRECTORY, config.directory.absolutePath)
        //debug("Saved mod configuration to preferences")
    }

    fun loadModConfig(gamePathsManager: GamePathsManager): ModOutputConfig? {
        val modName = preferences.get(CONFIG_MOD_NAME, null) ?: return null

        return try {
            ModOutputConfig(
                modName = modName,
                displayName = preferences.get(CONFIG_DISPLAY_NAME, modName),
                directory = File(preferences.get(CONFIG_DIRECTORY,
                    gamePathsManager.getLocalModPath().absolutePath)),
                gamePathsManager = gamePathsManager
            )
        } catch (e: Exception) {
            error("Failed to load mod configuration from preferences", e)
            null
        }
    }

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

    fun saveCustomSteamPaths(paths: List<String>) {
        if (paths.isEmpty()) {
            preferences.remove(CUSTOM_STEAM_PATHS)
            return
        }

        val pathString = paths.joinToString(PATHS_DELIMITER)
        preferences.put(CUSTOM_STEAM_PATHS, pathString)
        debug("Saved ${paths.size} custom Steam paths", useDispatcher = false)
    }

    fun getCustomSteamPaths(): List<String> {
        val pathString = preferences.get(CUSTOM_STEAM_PATHS, "")
        if (pathString.isEmpty()) return emptyList()

        return pathString.split(PATHS_DELIMITER).filter { it.isNotEmpty() }
    }

    /**
     * Saves a mod selection preset with the given name and mod paths
     */
    fun savePreset(name: String, modPaths: Set<String>): Boolean {
        if (name.isBlank() || modPaths.isEmpty()) {
            warn("Cannot save preset: Empty name or no mods selected")
            return false
        }

        try {
            // Check if preset with this name already exists and update it
            val existingIndex = getPresetIndexByName(name)
            if (existingIndex >= 0) {
                savePresetAtIndex(existingIndex, name, modPaths)
                debug("Updated existing preset: $name with ${modPaths.size} mods")
                return true
            }

            // Enforce maximum number of presets
            val presetCount = preferences.getInt(PRESET_COUNT_KEY, 0)
            if (presetCount >= MAX_PRESETS) {
                warn("Maximum number of presets ($MAX_PRESETS) reached")
                return false
            }

            // Save as new preset
            savePresetAtIndex(presetCount, name, modPaths)
            preferences.putInt(PRESET_COUNT_KEY, presetCount + 1)
            debug("Saved new preset: $name with ${modPaths.size} mods", useDispatcher = false)
            return true
        } catch (e: Exception) {
            error("Failed to save preset", e)
            return false
        }
    }

    /**
     * Loads all saved presets
     */
    fun getPresets(): List<ModPreset> {
        val presetCount = preferences.getInt(PRESET_COUNT_KEY, 0)
        if (presetCount == 0) return emptyList()

        return (0 until presetCount).mapNotNull { index ->
            try {
                val name = preferences.get(PRESET_NAME_PREFIX + index, null) ?: return@mapNotNull null
                val pathsString = preferences.get(PRESET_PATHS_PREFIX + index, "")
                val paths = if (pathsString.isEmpty()) emptySet() else
                    pathsString.split(PATHS_DELIMITER).toSet()
                val date = preferences.getLong(PRESET_DATE_PREFIX + index, System.currentTimeMillis())

                ModPreset(name, paths, date)
            } catch (e: Exception) {
                error("Failed to load preset at index $index", e)
                null
            }
        }.sortedBy { it.name }
    }

    /**
     * Deletes a preset by name
     */
    fun deletePreset(name: String): Boolean {
        val index = getPresetIndexByName(name)
        if (index < 0) return false

        return deletePresetAtIndex(index)
    }

    private fun getPresetIndexByName(name: String): Int {
        val presetCount = preferences.getInt(PRESET_COUNT_KEY, 0)
        for (i in 0 until presetCount) {
            val presetName = preferences.get(PRESET_NAME_PREFIX + i, null)
            if (presetName == name) return i
        }
        return -1
    }

    private fun savePresetAtIndex(index: Int, name: String, modPaths: Set<String>) {
        preferences.put(PRESET_NAME_PREFIX + index, name)
        preferences.put(PRESET_PATHS_PREFIX + index, modPaths.joinToString(PATHS_DELIMITER))
        preferences.putLong(PRESET_DATE_PREFIX + index, System.currentTimeMillis())
    }

    private fun deletePresetAtIndex(index: Int): Boolean {
        try {
            val presetCount = preferences.getInt(PRESET_COUNT_KEY, 0)
            if (index >= presetCount) return false

            // Shift all presets after this one up one position
            for (i in index until presetCount - 1) {
                preferences.put(PRESET_NAME_PREFIX + i,
                    preferences.get(PRESET_NAME_PREFIX + (i + 1), ""))

                preferences.put(PRESET_PATHS_PREFIX + i,
                    preferences.get(PRESET_PATHS_PREFIX + (i + 1), ""))

                preferences.putLong(PRESET_DATE_PREFIX + i,
                    preferences.getLong(PRESET_DATE_PREFIX + (i + 1), 0))
            }

            // Remove the last preset
            val lastIndex = presetCount - 1
            preferences.remove(PRESET_NAME_PREFIX + lastIndex)
            preferences.remove(PRESET_PATHS_PREFIX + lastIndex)
            preferences.remove(PRESET_DATE_PREFIX + lastIndex)

            // Update total count
            preferences.putInt(PRESET_COUNT_KEY, presetCount - 1)
            return true
        } catch (e: Exception) {
            error("Failed to delete preset at index $index", e)
            return false
        }
    }

    /**
     * Clears all presets
     */
    fun clearAllPresets() {
        val presetCount = preferences.getInt(PRESET_COUNT_KEY, 0)
        for (i in 0 until presetCount) {
            preferences.remove(PRESET_NAME_PREFIX + i)
            preferences.remove(PRESET_PATHS_PREFIX + i)
            preferences.remove(PRESET_DATE_PREFIX + i)
        }
        preferences.putInt(PRESET_COUNT_KEY, 0)
        debug("Cleared all presets", useDispatcher = false)
    }

    fun clearAll() {
        preferences.clear()
    }
}