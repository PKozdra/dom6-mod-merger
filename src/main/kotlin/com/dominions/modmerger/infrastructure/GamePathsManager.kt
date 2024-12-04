// src/main/kotlin/com/dominions/modmerger/infrastructure/GamePathsManager.kt
package com.dominions.modmerger.infrastructure

import com.dominions.modmerger.constants.GameConstants
import mu.KotlinLogging
import java.io.File
import java.nio.file.Paths

open class GamePathsManager {
    private val logger = KotlinLogging.logger {}

    open fun findSteamModPath(): File? {
        logger.debug { "Searching for Steam workshop path" }
        return findSteamInstallation()?.let { steamPath ->
            File(steamPath, "steamapps/workshop/content/${GameConstants.GAME_ID}")
                .takeIf { it.exists() }
                .also { path ->
                    logger.debug { "Found Steam workshop path: $path" }
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
                findWindowsSteamPath()
            }

            System.getProperty("os.name").contains("Mac") -> {
                val userHome = System.getProperty("user.home")
                File("$userHome/Library/Application Support/Steam")
                    .takeIf { it.exists() }
            }

            else -> {
                val userHome = System.getProperty("user.home")
                File("$userHome/.steam/steam")
                    .takeIf { it.exists() }
            }
        }
    }

    private fun findWindowsSteamPath(): File? {
        val possiblePaths = listOf(
            "C:/Program Files/Steam",
            "C:/Program Files (x86)/Steam",
            "D:/Program Files/Steam",
            "D:/Program Files (x86)/Steam",
            "E:/Program Files/Steam",
            "E:/Program Files (x86)/Steam"
        )

        return possiblePaths
            .map { File(it) }
            .firstOrNull { it.exists() }
    }
}