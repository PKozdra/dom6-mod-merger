package com.dominions.modmerger.ui.components

import com.dominions.modmerger.infrastructure.ApplicationConfig.logger
import com.dominions.modmerger.infrastructure.Logging
import com.dominions.modmerger.ui.model.ModListItem
import java.awt.Desktop
import java.awt.event.MouseEvent
import java.io.File
import java.net.URI
import java.util.regex.Pattern
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JTable
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener

class ModContextMenu(
    private val table: JTable,
    private val onError: (String) -> Unit
) : JPopupMenu(), Logging {
    private val desktop: Desktop? = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null

    companion object {
        private const val STEAM_WORKSHOP_URL = "https://steamcommunity.com/sharedfiles/filedetails/?id="
        private val WORKSHOP_ID_PATTERN = Pattern.compile(".+\\\\workshop\\\\content\\\\2511500\\\\(\\d+)\\\\.*")
    }

    init {
        setupListeners()
    }

    private fun setupListeners() {
        // Hide menu when clicking outside
        val popupListener = object : PopupMenuListener {
            override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) {
                debug("Context menu becoming visible", useDispatcher = false)
            }

            override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent) {
                debug("Context menu hiding", useDispatcher = false)
            }

            override fun popupMenuCanceled(e: PopupMenuEvent) {
                debug("Context menu cancelled", useDispatcher = false)
            }
        }
        addPopupMenuListener(popupListener)
    }

    fun showMenu(e: MouseEvent) {
        val point = e.point
        val row = table.rowAtPoint(point)

        if (row < 0) {
            debug("Clicked outside table rows", useDispatcher = false)
            return
        }

        // Handle selection
        if (!table.isRowSelected(row)) {
            if (!e.isControlDown && !e.isShiftDown) {
                table.selectionModel.setSelectionInterval(row, row)
                debug("Set single row selection: $row", useDispatcher = false)
            }
        }

        val selectedRows = table.selectedRows
        debug("Selected rows: ${selectedRows.joinToString()}", useDispatcher = false)

        if (selectedRows.isEmpty()) return

        createMenuItems(selectedRows)

        // Calculate proper position for the popup
        val tableLocation = table.locationOnScreen
        val screenX = tableLocation.x + point.x
        val screenY = tableLocation.y + point.y

        debug("Showing menu at screen coordinates: ($screenX, $screenY)", useDispatcher = false)
        show(table, point.x, point.y)
    }

    private fun createMenuItems(selectedRows: IntArray) {
        removeAll()

        val selectedMods = selectedRows.map { row ->
            val modelRow = table.convertRowIndexToModel(row)
            (table.model as ModTableModel).getModAt(modelRow)
        }

        debug("Creating menu for ${selectedMods.size} selected mods", useDispatcher = false)

        // Single selection options
        if (selectedMods.size == 1) {
            val mod = selectedMods.first()
            createSingleModMenuItems(mod)
        }

        // Multi-selection options
        if (selectedMods.size > 1) {
            createMultiModMenuItems(selectedMods)
        }
    }

    private fun createSingleModMenuItems(mod: ModListItem) {
        add(createMenuItem("Open in Explorer") {
            openInExplorer(mod)
        })

        getWorkshopId(mod)?.let { workshopId ->
            add(createMenuItem("Open in Steam Workshop") {
                openInSteamWorkshop(workshopId)
            })
            debug("Added Steam Workshop option for mod: ${mod.fileName} (ID: $workshopId)", useDispatcher = false)
        }

        add(createMenuItem("Open with Associated Application") {
            openDmFile(mod)
        })
    }

    private fun openDmFile(mod: ModListItem) {
        val file = mod.modFile.file ?: throw IllegalStateException("Mod file not found")
        val dmFile = File(file.parentFile, mod.modFile.file.name)

        if (!dmFile.exists()) {
            throw IllegalStateException(".dm file not found: ${dmFile.absolutePath}")
        }

        debug("Opening .dm file: ${dmFile.absolutePath}", useDispatcher = false)
        desktop?.let {
            if (it.isSupported(Desktop.Action.OPEN)) {
                try {
                    it.open(dmFile)
                } catch (e: Exception) {
                    warn("Failed to open file with default program. Falling back to Notepad: ${e.message}", useDispatcher = false)
                    openWithNotepad(dmFile)
                }
            } else {
                openWithNotepad(dmFile)
            }
        } ?: openWithNotepad(dmFile)
    }

    private fun openWithNotepad(file: File) {
        try {
            val runtime = Runtime.getRuntime()
            runtime.exec(arrayOf("notepad", file.absolutePath))
            debug("Opened .dm file in Notepad: ${file.absolutePath}", useDispatcher = false)
        } catch (e: Exception) {
            error("Failed to open .dm file in Notepad: ${file.absolutePath}", e, useDispatcher = false)
            onError("Failed to open .dm file: ${e.message}")
        }
    }

    private fun createMultiModMenuItems(mods: List<ModListItem>) {
        add(createMenuItem("Open All in Explorer") {
            mods.forEach { openInExplorer(it) }
        })

        // Get all valid workshop IDs
        val workshopMods = mods.mapNotNull { mod ->
            getWorkshopId(mod)?.let { id -> mod to id }
        }

        if (workshopMods.isNotEmpty()) {
            add(createMenuItem("Open All in Steam Workshop") {
                workshopMods.forEach { (_, id) -> openInSteamWorkshop(id) }
            })
            debug("Added Steam Workshop option for ${workshopMods.size} workshop mods", useDispatcher = false)
        }
    }

    private fun createMenuItem(text: String, action: () -> Unit): JMenuItem {
        return JMenuItem(text).apply {
            addActionListener {
                try {
                    action()
                    debug("Executed menu action: $text", useDispatcher = false)
                } catch (e: Exception) {
                    error("Error executing action: $text", e, useDispatcher = false)
                    onError("Failed to $text: ${e.message}")
                }
            }
        }
    }

    private fun openInExplorer(mod: ModListItem) {
        val file = mod.modFile.file ?: throw IllegalStateException("Mod file not found")
        val folder = file.parentFile ?: throw IllegalStateException("Parent folder not found")

        debug("Opening explorer for: ${folder.absolutePath}", useDispatcher = false)

        desktop?.let {
            if (it.isSupported(Desktop.Action.OPEN)) {
                it.open(folder)
            } else {
                throw UnsupportedOperationException("Cannot open folder on this platform")
            }
        } ?: throw UnsupportedOperationException("Desktop operations not supported")
    }

    private fun openInSteamWorkshop(workshopId: String) {
        val uri = URI(STEAM_WORKSHOP_URL + workshopId)
        debug("Opening Steam Workshop URL: $uri", useDispatcher = false)

        desktop?.let {
            if (it.isSupported(Desktop.Action.BROWSE)) {
                it.browse(uri)
            } else {
                throw UnsupportedOperationException("Cannot open browser on this platform")
            }
        } ?: throw UnsupportedOperationException("Desktop operations not supported")
    }

    private fun getWorkshopId(mod: ModListItem): String? {
        val path = mod.modFile.file?.absolutePath ?: return null
        return WORKSHOP_ID_PATTERN.matcher(path).let { matcher ->
            if (matcher.matches()) {
                matcher.group(1).also {
                    debug("Extracted workshop ID $it from path: $path", useDispatcher = false)
                }
            } else {
                debug("No workshop ID found in path: $path", useDispatcher = false)
                null
            }
        }
    }
}