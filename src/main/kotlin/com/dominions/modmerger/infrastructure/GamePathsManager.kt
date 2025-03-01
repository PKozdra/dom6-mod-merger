package com.dominions.modmerger.infrastructure

import com.dominions.modmerger.constants.GameConstants
import java.io.File
import java.nio.file.Paths

/**
 * Manages paths related to game installations and mods
 */
open class GamePathsManager : Logging {

    /**
     * Finds the Steam workshop path containing mods for the game
     * Searches in default and custom locations
     */
    open fun findSteamModPath(): File? {
        debug("Searching for Steam workshop path", useDispatcher=false)

        // Try to find main Steam installation
        val mainSteamPath = findSteamInstallation()

        if (mainSteamPath == null) {
            // If main Steam installation not found, try custom paths as fallback
            return findWorkshopInCustomPaths()
        }

        return findWorkshopInCustomPaths()

        // Check default workshop location in main Steam installation
        val defaultWorkshopPath = File(mainSteamPath, "steamapps/workshop/content/${GameConstants.GAME_ID}")
        if (defaultWorkshopPath.exists()) {
            debug("Found Steam workshop path: $defaultWorkshopPath", useDispatcher=false)
            return defaultWorkshopPath
        }

        // Check additional library folders configured in Steam
        val workshopInLibrary = findWorkshopInSteamLibraries(mainSteamPath)
        if (workshopInLibrary != null) {
            return workshopInLibrary
        }

        // As last resort, check custom paths
        return findWorkshopInCustomPaths()
    }

    /**
     * Gets the local mods directory for the game
     */
    open fun getLocalModPath(): File {
        val path = when {
            System.getProperty("os.name").contains("Windows") -> {
                val appData = System.getenv("APPDATA")
                Paths.get(appData, "Dominions6", "mods").toFile()
            }
            System.getProperty("os.name").contains("Mac") -> {
                val userHome = System.getProperty("user.home")
                Paths.get(userHome, "Library", "Application Support", "Dominions6", "mods").toFile()
            }
            else -> {
                val userHome = System.getProperty("user.home")
                Paths.get(userHome, ".local", "share", "Dominions6", "mods").toFile()
            }
        }

        if (!path.exists()) {
            path.mkdirs()
        }

        return path
    }

    /**
     * Returns list of custom Steam paths configured by the user
     */
    fun getCustomSteamPaths(): List<File> {
        return PreferencesManager.getCustomSteamPaths()
            .map { File(it) }
            .filter { it.exists() }
    }

    /**
     * Attempts to locate the main Steam installation
     */
    private fun findSteamInstallation(): File? {
        // First try to find the main Steam installation based on OS
        return when {
            System.getProperty("os.name").contains("Windows") -> {
                // On Windows, check registry first, then filesystem
                findSteamFromRegistry() ?: findSteamFromFileSystem()
            }
            System.getProperty("os.name").contains("Mac") -> {
                // On Mac, check standard location
                val userHome = System.getProperty("user.home")
                File("$userHome/Library/Application Support/Steam")
                    .takeIf { it.exists() }
            }
            else -> {
                // On Linux, check common locations
                val userHome = System.getProperty("user.home")
                listOf(
                    "$userHome/.steam/steam",
                    "$userHome/.steam/root",
                    "$userHome/.local/share/Steam"
                )
                    .map { File(it) }
                    .firstOrNull { it.exists() }
            }
        }
    }

    /**
     * Finds workshop content in user-configured custom Steam paths
     */
    private fun findWorkshopInCustomPaths(): File? {
        val customPaths = getCustomSteamPaths()
        for (customPath in customPaths) {
            val workshopPath = File(customPath, "steamapps/workshop/content/${GameConstants.GAME_ID}")
            if (workshopPath.exists()) {
                debug("Found Steam workshop in custom path: $workshopPath")
                return workshopPath
            }
        }
        return null
    }

    /**
     * Searches for workshop content in Steam library folders
     */
    private fun findWorkshopInSteamLibraries(mainSteamPath: File): File? {
        val libraryFoldersFile = File(mainSteamPath, "steamapps/libraryfolders.vdf")
        if (!libraryFoldersFile.exists()) {
            return null
        }

        try {
            val content = libraryFoldersFile.readText()
            val pathRegex = "\"path\"\\s+\"([^\"]+)\"".toRegex()
            val paths = pathRegex.findAll(content)
                .map { it.groupValues[1].replace("\\\\", "/") }
                .toList()

            for (path in paths) {
                val workshopPath = File("$path/steamapps/workshop/content/${GameConstants.GAME_ID}")
                if (workshopPath.exists()) {
                    debug("Found Steam workshop in library: $workshopPath")
                    return workshopPath
                }
            }
        } catch (e: Exception) {
            debug("Failed to parse Steam library folders: ${e.message}")
        }

        return null
    }

    /**
     * Attempts to find Steam installation from Windows registry
     */
    private fun findSteamFromRegistry(): File? {
        try {
            // Try 64-bit registry
            var process = Runtime.getRuntime().exec(arrayOf(
                "reg", "query",
                "HKEY_LOCAL_MACHINE\\SOFTWARE\\Valve\\Steam",
                "/v", "InstallPath"
            ))

            var path = process.inputStream.bufferedReader()
                .readText()
                .lines()
                .filter { it.contains("InstallPath") && it.contains("REG_SZ") }
                .map { it.substringAfter("REG_SZ").trim() }
                .firstOrNull()

            if (path == null) {
                // Try 32-bit registry
                process = Runtime.getRuntime().exec(arrayOf(
                    "reg", "query",
                    "HKEY_LOCAL_MACHINE\\SOFTWARE\\Wow6432Node\\Valve\\Steam",
                    "/v", "InstallPath"
                ))

                path = process.inputStream.bufferedReader()
                    .readText()
                    .lines()
                    .filter { it.contains("InstallPath") && it.contains("REG_SZ") }
                    .map { it.substringAfter("REG_SZ").trim() }
                    .firstOrNull()
            }

            return path?.let { File(it) }?.takeIf { it.exists() }
        } catch (e: Exception) {
            debug("Failed to read Steam path from registry: ${e.message}")
            return null
        }
    }

    /**
     * Searches for Steam installation in common filesystem locations
     */
    private fun findSteamFromFileSystem(): File? {
        // Get available drives
        val drives = ('C'..'Z')
            .map { "${it}:" }
            .map { File(it) }
            .filter { it.exists() }

        val possiblePaths = drives.flatMap { drive ->
            listOf(
                File(drive, "Program Files/Steam"),
                File(drive, "Program Files (x86)/Steam"),
                File(drive, "Steam")
            )
        }

        return possiblePaths.firstOrNull { it.exists() }
    }
}