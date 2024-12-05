package com.dominions.modmerger.infrastructure

import com.dominions.modmerger.domain.ModFile
import java.io.File
import java.io.IOException

class FileSystem(private val gamePathsManager: GamePathsManager) : Logging {

    companion object {
        private const val MOD_EXTENSION = "dm"
        private val INVALID_CHARS = Regex("[\\\\/:*?\"<>|\\s]")
        private const val MAX_MOD_NAME_LENGTH = 64
    }

    /**
     * Validates if the provided mod name follows Dominions 6 requirements.
     * @param name The mod name to validate
     * @return Pair<Boolean, String> where first is validity and second is error message (if any)
     */
    fun validateModName(name: String): Pair<Boolean, String?> {
        return when {
            name.isBlank() -> false to "Mod name cannot be empty"
            name.length > MAX_MOD_NAME_LENGTH -> false to "Mod name cannot exceed $MAX_MOD_NAME_LENGTH characters"
            name.contains(INVALID_CHARS) -> false to "Mod name contains invalid characters (no spaces or special characters allowed)"
            !name.matches(Regex("^[a-zA-Z0-9_-]+$")) -> false to "Mod name can only contain letters, numbers, underscores, and hyphens"
            else -> true to null
        }
    }

    /**
     * Creates a properly structured mod directory in the specified base directory.
     * @param modName Name of the mod (must be pre-validated)
     * @return The created directory File object
     * @throws IOException if directory creation fails
     */
    fun createModDirectory(modName: String): File {
        val baseDir = gamePathsManager.getLocalModPath()
        val modDir = File(baseDir, modName)

        try {
            if (!modDir.exists() && !modDir.mkdirs()) {
                throw IOException("Failed to create mod directory: ${modDir.absolutePath}")
            }
            info("Created mod directory: ${modDir.absolutePath}")
            return modDir
        } catch (e: SecurityException) {
            error("Security error creating mod directory: ${modDir.absolutePath}", e)
            throw IOException("Security error creating mod directory", e)
        }
    }

    /**
     * Gets the output file path following Dominions 6 mod structure.
     * @param modName Name of the mod (must be pre-validated)
     * @return File object representing the properly structured output file
     */
    fun getOutputFile(modName: String): File {
        val modDir = createModDirectory(modName)
        return File(modDir, "$modName.$MOD_EXTENSION")
    }

    /**
     * Ensures the mod structure is valid according to Dominions 6 requirements.
     * @param modName Name of the mod
     * @param targetDir Target directory where the mod will be created
     * @throws IOException if the structure is invalid or cannot be created
     */
    fun ensureValidModStructure(modName: String, targetDir: File) {
        val (isValid, error) = validateModName(modName)
        if (!isValid) {
            throw IOException(error)
        }

        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw IOException("Failed to create target directory: ${targetDir.absolutePath}")
        }

        val modFile = File(targetDir, "$modName.$MOD_EXTENSION")
        if (modFile.exists()) {
            warn("Mod file already exists: ${modFile.absolutePath}")
            // Let's not throw here, as we might want to overwrite
        }
    }

    /**
     * Writes content to a file in the proper mod structure.
     * @param modName Name of the mod
     * @param content Content to write
     * @throws IOException if writing fails
     */
    fun writeModFile(modName: String, content: String) {
        debug("Writing mod file for: $modName")
        try {
            val outputFile = getOutputFile(modName)
            outputFile.writeText(content)
            info("Successfully wrote mod file: ${outputFile.absolutePath}")
        } catch (e: IOException) {
            error("Failed to write mod file: $modName", e)
            throw e
        }
    }

    /**
     * Finds all mod files in a directory.
     * @param directory Directory to search in
     * @return List of ModFile objects
     */
    fun findModFiles(directory: File = File(".").absoluteFile): List<ModFile> {
        debug("Searching for .$MOD_EXTENSION files in directory: ${directory.absolutePath}")

        return try {
            directory
                .listFiles { file -> file.isFile && file.extension == MOD_EXTENSION }
                ?.mapNotNull { file ->
                    try {
                        debug("Found mod file: ${file.name}")
                        ModFile.fromFile(file)
                    } catch (e: Exception) {
                        warn("Failed to read mod file: ${file.name}")
                        null
                    }
                }
                ?: emptyList()
        } catch (e: IOException) {
            error("Error reading directory: ${directory.absolutePath}", e)
            emptyList()
        }
    }
}