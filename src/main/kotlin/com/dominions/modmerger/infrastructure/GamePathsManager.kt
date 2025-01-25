package com.dominions.modmerger.infrastructure

import com.dominions.modmerger.constants.GameConstants
import java.io.File
import java.nio.file.Paths

open class GamePathsManager : Logging {
    open fun findSteamModPath(): File? {
        debug("Searching for Steam workshop path")
        return findSteamInstallation()?.let { steamPath ->
            File(steamPath, "steamapps/workshop/content/${GameConstants.GAME_ID}")
                .takeIf { it.exists() }
                .also { path ->
                    debug("Found Steam workshop path: $path")
                }
        }
    }

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

    private fun findSteamInstallation(): File? {
        return when {
            System.getProperty("os.name").contains("Windows") -> {
                // Try registry first
                findSteamFromRegistry() ?: findSteamFromFileSystem()
            }
            System.getProperty("os.name").contains("Mac") -> {
                val userHome = System.getProperty("user.home")
                File("$userHome/Library/Application Support/Steam")
                    .takeIf { it.exists() }
            }
            else -> {
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