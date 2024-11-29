// ModTablePanel.kt
package com.dominions.modmerger.ui.components

import com.dominions.modmerger.ui.model.ModListItem
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableRowSorter

// ModTablePanel.kt
class ModTablePanel : JPanel() {
    private val model = ModTableModel()
    private val table = createTable()
    private val rowSorter = createRowSorter()
    private val searchField = createSearchField()
    private val pathField = JTextField()
    private val browseButton = JButton("Browse").apply {
        addActionListener { showDirectoryChooser() }
    }

    companion object {
        private const val ICON_WIDTH = 256
        private const val ICON_HEIGHT = 64
        private const val ROW_PADDING = 4
        private const val SEARCH_FIELD_WIDTH = 320
        private const val SEARCH_FIELD_HEIGHT = 32
    }

    init {
        setupPanel()
        setupTableListeners()
    }

    private fun setupPanel() {
        layout = BorderLayout(10, 10)
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        add(createControlPanel(), BorderLayout.NORTH)
        add(JScrollPane(table), BorderLayout.CENTER)
    }

    private fun createControlPanel() = JPanel(BorderLayout(5, 5)).apply {
        add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(JLabel("Search:"))
            add(searchField)
            add(Box.createHorizontalStrut(10))
            add(createSelectionButton("Select All") { model.setAllSelected(true) })
            add(createSelectionButton("Select None") { model.setAllSelected(false) })
        }, BorderLayout.CENTER)
    }

    private fun createSelectionButton(text: String, action: () -> Unit) =
        JButton(text).apply { addActionListener { action() } }

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

    private fun createDefaultRenderer() = DefaultTableCellRenderer().apply {
        border = BorderFactory.createEmptyBorder(0, 5, 0, 5)
    }

    private fun setupTableListeners() {
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val row = table.rowAtPoint(e.point)
                    if (row >= 0) {
                        val modelRow = table.convertRowIndexToModel(row)
                        model.toggleSelection(modelRow)
                    }
                }
            }
        })
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

    private fun createRowSorter() = TableRowSorter(model).apply {
        sortKeys = listOf(RowSorter.SortKey(2, SortOrder.ASCENDING))
        setSortable(0, false)
        setSortable(1, false)
    }

    private fun createSearchField() = JTextField().apply {
        preferredSize = Dimension(SEARCH_FIELD_WIDTH, SEARCH_FIELD_HEIGHT)
        document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = updateFilter()
            override fun removeUpdate(e: DocumentEvent) = updateFilter()
            override fun changedUpdate(e: DocumentEvent) = updateFilter()
        })
    }

    private fun updateFilter() {
        val text = searchField.text
        rowSorter.rowFilter = if (text.isEmpty()) null else RowFilter.regexFilter("(?i)$text")
    }

    private fun showDirectoryChooser() {
        val fileChooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "Select Mods Directory"
            currentDirectory = File(System.getProperty("user.home"))
        }

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            pathField.text = fileChooser.selectedFile.absolutePath
            firePropertyChange("customPath", null, pathField.text)
        }
    }

    fun getCustomPath(): String = pathField.text
    fun updateMods(mods: List<ModListItem>) = model.updateMods(mods)
    fun getSelectedMods(): List<ModListItem> = model.getSelectedMods()
}