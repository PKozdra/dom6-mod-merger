package com.dominions.modmerger.ui.components

import com.dominions.modmerger.ui.model.ModListItem
import mu.KotlinLogging
import java.awt.Desktop
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.net.URI
import java.util.regex.Pattern
import javax.swing.*
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import javax.swing.SwingUtilities

class ModContextMenu(
    private val table: JTable,
    private val onError: (String) -> Unit
) : JPopupMenu() {
    private val logger = KotlinLogging.logger {}
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
                logger.debug { "Context menu becoming visible" }
            }

            override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent) {
                logger.debug { "Context menu hiding" }
            }

            override fun popupMenuCanceled(e: PopupMenuEvent) {
                logger.debug { "Context menu cancelled" }
            }
        }
        addPopupMenuListener(popupListener)
    }

    fun showMenu(e: MouseEvent) {
        val point = e.point
        val row = table.rowAtPoint(point)

        if (row < 0) {
            logger.debug { "Clicked outside table rows" }
            return
        }

        // Handle selection
        if (!table.isRowSelected(row)) {
            if (!e.isControlDown && !e.isShiftDown) {
                table.selectionModel.setSelectionInterval(row, row)
                logger.debug { "Set single row selection: $row" }
            }
        }

        val selectedRows = table.selectedRows
        logger.debug { "Selected rows: ${selectedRows.joinToString()}" }

        if (selectedRows.isEmpty()) return

        createMenuItems(selectedRows)

        // Calculate proper position for the popup
        val tableLocation = table.locationOnScreen
        val screenX = tableLocation.x + point.x
        val screenY = tableLocation.y + point.y

        logger.debug { "Showing menu at screen coordinates: ($screenX, $screenY)" }
        show(table, point.x, point.y)
    }

    private fun createMenuItems(selectedRows: IntArray) {
        removeAll()

        val selectedMods = selectedRows.map { row ->
            val modelRow = table.convertRowIndexToModel(row)
            (table.model as ModTableModel).getModAt(modelRow)
        }

        logger.debug { "Creating menu for ${selectedMods.size} selected mods" }

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
            logger.debug { "Added Steam Workshop option for mod: ${mod.fileName} (ID: $workshopId)" }
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
            logger.debug { "Added Steam Workshop option for ${workshopMods.size} workshop mods" }
        }
    }

    private fun createMenuItem(text: String, action: () -> Unit): JMenuItem {
        return JMenuItem(text).apply {
            addActionListener {
                try {
                    action()
                    logger.debug { "Executed menu action: $text" }
                } catch (e: Exception) {
                    logger.error(e) { "Error executing action: $text" }
                    onError("Failed to $text: ${e.message}")
                }
            }
        }
    }

    private fun openInExplorer(mod: ModListItem) {
        val file = mod.modFile.file ?: throw IllegalStateException("Mod file not found")
        val folder = file.parentFile ?: throw IllegalStateException("Parent folder not found")

        logger.debug { "Opening explorer for: ${folder.absolutePath}" }

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
        logger.debug { "Opening Steam Workshop URL: $uri" }

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
                    logger.debug { "Extracted workshop ID $it from path: $path" }
                }
            } else {
                logger.debug { "No workshop ID found in path: $path" }
                null
            }
        }
    }
}