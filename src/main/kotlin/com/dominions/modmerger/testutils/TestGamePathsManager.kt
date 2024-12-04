// src/test/kotlin/com/dominions/modmerger/testutils/TestGamePathsManager.kt
package com.dominions.modmerger.testutils

import com.dominions.modmerger.infrastructure.GamePathsManager
import java.io.File

class TestGamePathsManager(private val testResourcesPath: File) : GamePathsManager() {
    override fun findSteamModPath(): File? = null  // We don't need Steam workshop path for tests

    override fun getLocalModPath(): File {
        val modsPath = File(testResourcesPath, "mods")
        if (!modsPath.exists()) {
            modsPath.mkdirs()
        }
        return modsPath
    }
}