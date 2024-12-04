package com.dominions.modmerger.core.writing.config

import com.dominions.modmerger.infrastructure.FileSystem
import com.dominions.modmerger.infrastructure.GamePathsManager
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

        fun generateDefaultName(): String =
            "Merged_Mod"

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

        // Validate technical name
        if (!modName.matches(VALID_MOD_NAME_REGEX)) {
            errors.add("Technical mod name must contain only letters, numbers, underscores, or hyphens")
        }

        // Validate display name
        if (displayName.isBlank()) {
            errors.add("Display name cannot be empty")
        }

        // Validate directory
        if (!directory.exists() && !directory.canWrite()) {
            errors.add("Selected directory is not writable")
        }

        return errors.isEmpty() to errors
    }

    fun createDefaultConfig(): ModOutputConfig {
        val defaultName = generateDefaultName()
        return ModOutputConfig.Builder(defaultName, gamePathsManager)
            .setDisplayName(defaultName.replace('_', ' '))
            .build()
    }

    fun generatePreviewPath(modName: String, directory: File): String {
        return File(directory, "$modName/$modName.dm").absolutePath
    }

    fun getDefaultDirectory(): File = gamePathsManager.getLocalModPath()
}