package com.dominions.modmerger.core.writing.config

import com.dominions.modmerger.infrastructure.FileSystem
import com.dominions.modmerger.infrastructure.GamePathsManager
import com.dominions.modmerger.infrastructure.PreferencesManager
import java.io.File

/**
 * Manages the creation and validation of mod output configurations.
 * Handles business logic separate from UI concerns.
 */
class ModOutputConfigManager(
    private val fileSystem: FileSystem,
    val gamePathsManager: GamePathsManager
) {
    companion object {
        private val VALID_MOD_NAME_REGEX = Regex("^[a-zA-Z0-9_-]+$")

        fun generateDefaultName(): String = "Merged_Mod"

        fun sanitizeModName(displayName: String): String =
            displayName
                .replace(" ", "_")
                .replace(Regex("[^A-Za-z0-9_-]"), "")
    }

    fun validateConfig(
        modName: String,
        displayName: String,
        directory: File
    ): Pair<Boolean, List<String>> {
        val errors = mutableListOf<String>()

        if (!modName.matches(VALID_MOD_NAME_REGEX)) {
            errors.add("Technical mod name must contain only letters, numbers, underscores, or hyphens")
        }

        if (displayName.isBlank()) {
            errors.add("Display name cannot be empty")
        }

        if (!directory.exists() && !directory.canWrite()) {
            errors.add("Selected directory is not writable")
        }

        return errors.isEmpty() to errors
    }

    fun createDefaultConfig(): ModOutputConfig {
        val defaultName = generateDefaultName()
        return PreferencesManager.loadModConfig(gamePathsManager) ?: ModOutputConfig(
            modName = defaultName,
            displayName = defaultName.replace('_', ' '),
            directory = getDefaultDirectory(),
            gamePathsManager = gamePathsManager
        )
    }

    fun saveConfig(config: ModOutputConfig) {
        PreferencesManager.saveModConfig(config)
    }

    fun generatePreviewPath(modName: String, directory: File): String =
        File(directory, "$modName/$modName.dm").absolutePath

    fun getDefaultDirectory(): File = gamePathsManager.getLocalModPath()
}