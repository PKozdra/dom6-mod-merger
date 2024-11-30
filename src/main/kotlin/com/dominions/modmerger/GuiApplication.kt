// src/main/kotlin/com/dominions/modmerger/GuiApplication.kt
package com.dominions.modmerger

import com.dominions.modmerger.core.ModMergerService
import com.dominions.modmerger.domain.LogDispatcher
import com.dominions.modmerger.infrastructure.FileSystem
import com.dominions.modmerger.infrastructure.GamePathsManager
import com.dominions.modmerger.ui.ModMergerGui
import javax.swing.SwingUtilities

class GuiApplication(
    modMergerService: ModMergerService,
    fileSystem: FileSystem,
    private val gamePathsManager: GamePathsManager,
    private val logDispatcher: LogDispatcher
) : Application(modMergerService, fileSystem) {

    override fun run() {
        SwingUtilities.invokeLater {
            ModMergerGui(modMergerService, fileSystem, gamePathsManager, logDispatcher).show()
        }
    }
}