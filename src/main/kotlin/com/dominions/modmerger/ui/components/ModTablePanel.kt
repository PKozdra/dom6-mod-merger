package com.dominions.modmerger.ui.components

import com.dominions.modmerger.ui.model.ModListItem
import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableRowSorter
import java.util.Timer
import java.util.TimerTask

/**
 * Panel that displays and manages a table of mods with search and selection capabilities.
 */
class ModTablePanel : JPanel() {
    private val model = ModTableModel()
    private val table = createTable()
    private val rowSorter = createRowSorter()
    private val searchField = createSearchField()
    private val statusLabel = JLabel()
    private val searchTimer = Timer()
    private var searchTask: TimerTask? = null

    private val modCountLabel = JLabel()
    private val selectionCountLabel = JLabel()
    private val statusPanel = createStatusPanel()

    private val contextMenu = ModContextMenu(table) { message -> updateStatusMessage(message) }

    companion object {
        private const val ICON_WIDTH = 256
        private const val ICON_HEIGHT = 64
        private const val ROW_PADDING = 4
        private const val SEARCH_DELAY_MS = 300L
        private val SEARCH_COLUMNS = listOf(
            ModTableModel.TableColumn.NAME.ordinal,
            ModTableModel.TableColumn.FILENAME.ordinal
        )
    }

    init {
        setupPanel()
        setupTableListeners()
        model.addTableModelListener {
            updateStatusLabels()
        }
    }

    private fun setupPanel() {
        layout = BorderLayout(10, 10)
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        add(createControlPanel(), BorderLayout.NORTH)
        add(JScrollPane(table).apply {
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
        }, BorderLayout.CENTER)
        add(createStatusPanel(), BorderLayout.SOUTH)

        table.rowSorter = rowSorter
    }

    private fun createControlPanel() = JPanel(BorderLayout(5, 5)).apply {
        add(createSearchPanel(), BorderLayout.CENTER)
        add(createSelectionPanel(), BorderLayout.EAST)
    }

    private fun createSearchPanel() = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
        val searchLabel = JLabel("Search:").apply {
            font = font.deriveFont(Font.BOLD)
        }

        val searchBox = JPanel(BorderLayout()).apply {
            add(searchField, BorderLayout.CENTER)
            add(createClearButton(), BorderLayout.EAST)
            border = CompoundBorder(
                BorderFactory.createLineBorder(Color.LIGHT_GRAY),
                EmptyBorder(2, 5, 2, 5)
            )
        }

        add(searchLabel)
        add(Box.createHorizontalStrut(5))
        add(searchBox)
        add(Box.createHorizontalStrut(10))
        add(modCountLabel)
    }

    private fun createClearButton() = JButton("×").apply {
        toolTipText = "Clear search"
        preferredSize = Dimension(24, 24)
        isFocusPainted = false
        isContentAreaFilled = false
        addActionListener { clearSearch() }
    }

    private fun createSelectionPanel() = JPanel(FlowLayout(FlowLayout.RIGHT, 5, 0)).apply {
        add(createSelectionButton("Select All") { selectAllVisible() })
        add(createSelectionButton("Select None") { deselectAllVisible() })
    }

    private fun createSelectionButton(text: String, action: () -> Unit) = JButton(text).apply {
        addActionListener { action() }
    }

    private fun selectAllVisible() {
        val visibleRows = (0 until table.rowCount).map {
            table.convertRowIndexToModel(it)
        }
        model.setSelectedRows(visibleRows, true)
        updateStatusLabels()
    }

    private fun deselectAllVisible() {
        val visibleRows = (0 until table.rowCount).map {
            table.convertRowIndexToModel(it)
        }
        model.setSelectedRows(visibleRows, false)
        updateStatusLabels()
    }

    private object ColorUtils {
        private val colorCache = mutableMapOf<String, Color>()

        fun getColorForModName(modName: String): Color {
            // Get base name (remove numbers and special characters)
            val baseName = modName.lowercase().replace(Regex("[^a-z]"), "")

            return colorCache.getOrPut(baseName) {
                // Generate consistent color based on the mod name
                val hash = baseName.hashCode()
                // Use HSB color model for better control over color generation
                Color.getHSBColor(
                    (hash and 0xFF) / 255f, // Hue
                    0.4f + (hash shr 8 and 0xFF) / 512f, // Saturation (0.4-0.6)
                    0.8f + (hash shr 16 and 0xFF) / 512f  // Brightness (0.8-1.0)
                )
            }
        }

        fun getHexColor(color: Color): String {
            return String.format("#%02x%02x%02x", color.red, color.green, color.blue)
        }
    }

    private fun updateStatusLabels() {
        val totalMods = model.rowCount
        val visibleMods = table.rowCount
        val selectedMods = model.getSelectedMods()
        val selectedModsSize = selectedMods.size

        modCountLabel.text = when {
            totalMods == 0 -> "No mods found"
            visibleMods < totalMods -> "Showing $visibleMods of $totalMods mods"
            else -> "Showing all $totalMods mods"
        }

        selectionCountLabel.apply {
            maximumSize = Dimension(parent?.width ?: 600, Short.MAX_VALUE.toInt())
            text = when (selectedModsSize) {
                0 -> " "
                1 -> buildWrappingText("Selected 1 mod", selectedMods)
                else -> buildWrappingText("Selected $selectedModsSize mods", selectedMods)
            }
        }

        // Force the panel to recalculate its size and layout
        selectionCountLabel.parent?.invalidate()
        selectionCountLabel.parent?.revalidate()
        selectionCountLabel.parent?.repaint()
    }

    private fun buildWrappingText(prefix: String, selectedMods: List<ModListItem>): String {
        // Force a fixed width that will cause wrapping regardless of how mods are selected
        val forcedWidth = (selectionCountLabel.parent?.width ?: 600).coerceAtMost(600)

        return """<html>
        <body>
            <div style='width: ${forcedWidth}px; white-space: normal; display: block;'>
                $prefix: ${
            selectedMods.joinToString(" | ") { mod ->
                val color = ColorUtils.getColorForModName(mod.modName)
                val hexColor = ColorUtils.getHexColor(color)
                "<span style='color: $hexColor; text-decoration: underline; cursor: pointer'>${mod.modName}</span>"
            }
        }
            </div>
        </body>
    </html>"""
    }

    private fun createStatusPanel() = JPanel(BorderLayout()).apply {
        val textPanel = JPanel().apply {
            layout = FlowLayout(FlowLayout.LEFT, 5, 5)
            add(selectionCountLabel)
        }

        val scrollPane = JScrollPane(textPanel).apply {
            border = BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY)
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            minimumSize = Dimension(100, 40)
            preferredSize = Dimension(100, 40)
        }

        add(scrollPane, BorderLayout.CENTER)

        // Add a component listener to handle resize events
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                // Force update of the label when panel is resized
                updateStatusLabels()
            }
        })
    }

    private fun createSearchField() = JTextField(20).apply {
        document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = scheduleSearch()
            override fun removeUpdate(e: DocumentEvent) = scheduleSearch()
            override fun changedUpdate(e: DocumentEvent) = scheduleSearch()
        })

        addKeyListener(object : KeyListener {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_ESCAPE -> clearSearch()
                    KeyEvent.VK_ENTER -> performSearch()
                }
            }
            override fun keyTyped(e: KeyEvent) {}
            override fun keyReleased(e: KeyEvent) {}
        })

        toolTipText = "Type to search mod names or filenames (ESC to clear)"
    }

    private fun createTable() = JTable(model).apply {
        setupTableProperties()
        setupTableColumns()
    }

    private fun JTable.setupTableProperties() {
        rowHeight = ICON_HEIGHT + (ROW_PADDING * 2)
        fillsViewportHeight = true
        selectionBackground = Color(240, 245, 255)
        selectionForeground = Color.BLACK
        gridColor = Color(230, 230, 230)
        tableHeader.reorderingAllowed = false
    }

    private fun JTable.setupTableColumns() {
        columnModel.apply {
            getColumn(0).apply {
                width = 30
                maxWidth = 30
                cellRenderer = CheckBoxRenderer()
                cellEditor = DefaultCellEditor(JCheckBox())
            }
            getColumn(1).apply {
                width = ICON_WIDTH
                minWidth = ICON_WIDTH
                preferredWidth = ICON_WIDTH
                cellRenderer = IconRenderer(ICON_WIDTH, ICON_HEIGHT)
            }
            getColumn(2).apply {
                preferredWidth = 300
                cellRenderer = ModNameRenderer()
            }
            getColumn(3).apply {
                preferredWidth = 200
                cellRenderer = createDefaultRenderer()
            }
            getColumn(4).apply {
                preferredWidth = 80
                cellRenderer = SizeRenderer()
            }
            getColumn(5).apply {
                preferredWidth = 150
                cellRenderer = createDefaultRenderer().apply {
                    horizontalAlignment = SwingConstants.CENTER
                }
            }
        }
    }

    private fun createDefaultRenderer() = DefaultTableCellRenderer().apply {
        border = BorderFactory.createEmptyBorder(0, 5, 0, 5)
    }

    private fun setupTableListeners() {
        table.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    handleContextMenu(e)
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    handleContextMenu(e)
                }
            }

            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && e.button == MouseEvent.BUTTON1) {
                    handleDoubleClick(e)
                }
            }
        })
    }

    private fun handleContextMenu(e: MouseEvent) {
        contextMenu.showMenu(e)
    }

    private fun handleDoubleClick(e: MouseEvent) {
        val row = table.rowAtPoint(e.point)
        if (row >= 0) {
            val modelRow = table.convertRowIndexToModel(row)
            model.toggleSelection(modelRow)
        }
    }

    private fun createRowSorter() = TableRowSorter(model).apply {
        sortKeys = listOf(RowSorter.SortKey(2, SortOrder.ASCENDING))
        setSortable(0, false)
        setSortable(1, false)
    }

    private fun scheduleSearch() {
        searchTask?.cancel()
        searchTask = object : TimerTask() {
            override fun run() {
                SwingUtilities.invokeLater { performSearch() }
            }
        }
        searchTimer.schedule(searchTask, SEARCH_DELAY_MS)
    }

    private fun performSearch() {
        val searchText = searchField.text.trim()

        if (searchText.isEmpty()) {
            rowSorter.rowFilter = null
        } else {
            try {
                val filter = createSearchFilter(searchText)
                rowSorter.rowFilter = filter
            } catch (e: Exception) {
                updateStatusMessage("Invalid search query")
                return
            }
        }
        updateStatusLabels()
    }

    private fun createSearchFilter(searchText: String): RowFilter<ModTableModel, Int> {
        val searchTerms = searchText.toLowerCase().split(" ").filter { it.isNotEmpty() }

        return object : RowFilter<ModTableModel, Int>() {
            override fun include(entry: Entry<out ModTableModel, out Int>): Boolean {
                return searchTerms.all { term ->
                    SEARCH_COLUMNS.any { column ->
                        entry.getValue(column).toString().toLowerCase().contains(term)
                    }
                }
            }
        }
    }

    private fun clearSearch() {
        searchField.text = ""
        searchField.requestFocus()
        rowSorter.rowFilter = null
        updateStatusMessage(null)
    }

    private fun updateSearchResults() {
        val totalRows = model.rowCount
        val filteredRows = rowSorter.viewRowCount

        when {
            filteredRows == 0 -> updateStatusMessage("No matches found")
            filteredRows < totalRows -> updateStatusMessage("Showing $filteredRows of $totalRows mods")
            else -> updateStatusMessage(null)
        }
    }

    private fun updateStatusMessage(message: String?) {
        statusLabel.text = message ?: " "
    }

    fun updateMods(mods: List<ModListItem>) {
        model.updateMods(mods)
        clearSearch()
        updateStatusLabels()
    }

    fun getSelectedMods(): List<ModListItem> = model.getSelectedMods()
}