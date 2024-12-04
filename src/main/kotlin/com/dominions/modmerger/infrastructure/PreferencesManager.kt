package com.dominions.modmerger.infrastructure

import com.dominions.modmerger.domain.LogLevel
import java.util.prefs.Preferences

object PreferencesManager {
    private val preferences = Preferences.userRoot().node("com/dominions/modmerger")

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

    fun clearAll() {
        preferences.clear()
    }
}