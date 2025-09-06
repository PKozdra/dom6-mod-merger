// ModTableModel.kt
package com.dominions.modmerger.ui.components

import com.dominions.modmerger.ui.model.ModListItem
import javax.swing.Icon
import javax.swing.table.AbstractTableModel

class ModTableModel : AbstractTableModel() {
    // Expose columns as a read-only list
    val columns: List<TableColumn> = TableColumn.entries
    private val columnIndexMap = columns.mapIndexed { index, column -> column to index }.toMap()

    private val mods = mutableListOf<ModListItem>()

    enum class TableColumn(val displayName: String, val type: Class<*>, val width: Int? = null) {
        SELECTED("", Boolean::class.java, 30),
        ICON("Icon", Icon::class.java, 256),
        NAME("Name", String::class.java),
        FILENAME("File name", String::class.java),
        SIZE("Size", String::class.java),
        LAST_MODIFIED("Last modified", String::class.java),
        SOURCE("Source", Icon::class.java, 64),
    }

    // Helper method to get column index from TableColumn enum
    fun getColumnIndex(column: TableColumn): Int {
        return columnIndexMap[column] ?: -1
    }

    fun updateMods(newMods: List<ModListItem>) {
        mods.clear()
        mods.addAll(newMods)
        fireTableDataChanged()
    }

    fun getSelectedMods(): List<ModListItem> = mods.filter { it.isSelected }

    fun setAllSelected(selected: Boolean) {
        mods.forEachIndexed { index, mod ->
            mods[index] = mod.copy(isSelected = selected)
            fireTableCellUpdated(index, getColumnIndex(TableColumn.SELECTED))
        }
    }

    fun toggleSelection(row: Int) {
        if (row >= 0 && row < mods.size) {
            val currentValue = mods[row].isSelected
            setValueAt(!currentValue, row, getColumnIndex(TableColumn.SELECTED))
        }
    }

    fun setSelectedRows(rows: List<Int>, selected: Boolean) {
        val selectedColumnIndex = getColumnIndex(TableColumn.SELECTED)
        rows.forEach { row ->
            if (row >= 0 && row < mods.size) {
                mods[row] = mods[row].copy(isSelected = selected)
                fireTableCellUpdated(row, selectedColumnIndex)
            }
        }
    }

    fun getSelectedCount(): Int = mods.count { it.isSelected }

    override fun getRowCount(): Int = mods.size
    override fun getColumnCount(): Int = columns.size
    override fun getColumnName(column: Int): String = columns[column].displayName
    override fun getColumnClass(column: Int): Class<*> = columns[column].type
    override fun isCellEditable(row: Int, column: Int): Boolean = columns[column] == TableColumn.SELECTED

    override fun getValueAt(row: Int, column: Int): Any? {
        val mod = mods[row]
        return when (columns[column]) {
            TableColumn.SELECTED -> mod.isSelected
            TableColumn.ICON -> mod.iconPath
            TableColumn.SOURCE -> mod.sourceType
            TableColumn.NAME -> mod.modName
            TableColumn.FILENAME -> mod.fileName
            TableColumn.SIZE -> mod.getFormattedSize()
            TableColumn.LAST_MODIFIED -> mod.getFormattedDate()
        }
    }

    override fun setValueAt(value: Any?, row: Int, column: Int) {
        if (columns[column] == TableColumn.SELECTED && value is Boolean) {
            mods[row] = mods[row].copy(isSelected = value)
            fireTableCellUpdated(row, column)
        }
    }

    fun getModAt(row: Int): ModListItem = mods[row]

    /**
     * Returns all mods in the model regardless of selection or filter state
     */
    fun getAllMods(): List<ModListItem> {
        return mods.toList()
    }

    /**
     * Updates only the selection states without affecting other data or firing full table change
     */
    fun updateSelectionStates(selectedPaths: Set<String>) {
        val selectedColumnIndex = getColumnIndex(TableColumn.SELECTED)

        for (i in mods.indices) {
            val path = mods[i].modFile.file?.absolutePath
            val shouldBeSelected = path in selectedPaths

            if (mods[i].isSelected != shouldBeSelected) {
                mods[i] = mods[i].copy(isSelected = shouldBeSelected)
                fireTableCellUpdated(i, selectedColumnIndex)
            }
        }
    }
}