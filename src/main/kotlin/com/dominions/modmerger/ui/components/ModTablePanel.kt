package com.dominions.modmerger.ui.components

import com.dominions.modmerger.ui.model.ModListItem
import mu.KotlinLogging
import java.awt.*
import java.awt.event.*
import java.util.*
import java.util.Timer
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableRowSorter

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

    private val logger = KotlinLogging.logger {}

    private val modCountLabel = JLabel()
    private lateinit var statusPanel: StatusPanel

    private val contextMenu = ModContextMenu(table) { message -> updateStatusMessage(message) }

    companion object {
        private const val ICON_WIDTH = 256
        private const val ICON_HEIGHT = 64
        private const val ROW_PADDING = 4
        private const val SEARCH_DELAY_MS = 200L
        private val SEARCH_COLUMNS = listOf(
            ModTableModel.TableColumn.NAME.ordinal,
            ModTableModel.TableColumn.FILENAME.ordinal
        )
    }

    init {
        setupPanel()
        setupTableListeners()
        setupKeyBindings()
        model.addTableModelListener {
            updateStatusLabels()
        }
    }

    fun addSelectionListener(listener: () -> Unit) {
        model.addSelectionListener(listener)
    }

    private fun setupPanel() {
        layout = BorderLayout(10, 10)
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        add(createControlPanel(), BorderLayout.NORTH)
        add(JScrollPane(table).apply {
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
        }, BorderLayout.CENTER)

        statusPanel = StatusPanel { mod ->
            focusOnMod(mod)
        }
        add(statusPanel, BorderLayout.SOUTH)

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

    private fun updateStatusLabels() {
        val totalMods = model.rowCount
        val visibleMods = table.rowCount

        // Update mod count label
        modCountLabel.text = when {
            totalMods == 0 -> "No mods found"
            visibleMods < totalMods -> "Showing $visibleMods of $totalMods mods"
            else -> "Showing all $totalMods mods"
        }

        // Update status panel with only selected mods
        statusPanel.updateStatus(model.getSelectedMods())
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

    private fun focusOnMod(mod: ModListItem) {
        logger.debug { "Focusing on mod: ${mod.modName}" }
        logger.debug { "Clearing the search bar" }
        clearSearch()

        logger.debug { "Searching for mod in model with ${model.rowCount} total rows" }
        val modelRow = (0 until model.rowCount).find { row ->
            val currentMod = model.getModAt(row)
            logger.trace { "Checking row $row: ${currentMod.modName}" }
            currentMod == mod
        }

        if (modelRow == null) {
            logger.warn { "Could not find mod ${mod.modName} in the model" }
            return
        }

        logger.debug { "Found mod at model row: $modelRow" }
        val viewRow = table.convertRowIndexToView(modelRow)
        logger.debug { "Converted to view row: $viewRow" }

        logger.debug { "Clearing selection and setting new selection interval" }
        table.clearSelection()
        table.setRowSelectionInterval(viewRow, viewRow)

        logger.debug { "Scrolling table to make selected row visible" }
        val cellRect = table.getCellRect(viewRow, 0, true)
        table.scrollRectToVisible(cellRect)
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

    private fun setupKeyBindings() {
        val inputMap = this.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        val actionMap = this.actionMap

        // Bind CTRL+F to focus the search bar
        val keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_F, Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx)
        inputMap.put(keyStroke, "focusSearchBar")
        actionMap.put("focusSearchBar", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                searchField.requestFocusInWindow()
            }
        })
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
        val searchTerms = searchText.lowercase(Locale.getDefault()).split(" ").filter { it.isNotEmpty() }

        return object : RowFilter<ModTableModel, Int>() {
            override fun include(entry: Entry<out ModTableModel, out Int>): Boolean {
                return searchTerms.all { term ->
                    SEARCH_COLUMNS.any { column ->
                        entry.getValue(column).toString().lowercase(Locale.getDefault()).contains(term)
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