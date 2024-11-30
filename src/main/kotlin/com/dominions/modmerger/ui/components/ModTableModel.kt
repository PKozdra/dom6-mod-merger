// ModTableModel.kt
package com.dominions.modmerger.ui.components

import com.dominions.modmerger.ui.model.ModListItem
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.table.AbstractTableModel

class ModTableModel : AbstractTableModel() {
    private val columns = TableColumn.entries.toTypedArray()
    private val mods = mutableListOf<ModListItem>()

    enum class TableColumn(val displayName: String, val type: Class<*>) {
        SELECTED("", Boolean::class.java),
        ICON("Icon", Icon::class.java),
        NAME("Name", String::class.java),
        FILENAME("File name", String::class.java),
        SIZE("Size", Long::class.java),
        LAST_MODIFIED("Last modified", String::class.java);

        companion object {
            fun fromIndex(index: Int) = values()[index]
        }
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
            fireTableCellUpdated(index, 0)
        }
    }

    fun toggleSelection(row: Int) {
        if (row >= 0 && row < mods.size) {
            setValueAt(!mods[row].isSelected, row, TableColumn.SELECTED.ordinal)
        }
    }

    fun setSelectedRows(rows: List<Int>, selected: Boolean) {
        rows.forEach { row ->
            if (row >= 0 && row < mods.size) {
                mods[row] = mods[row].copy(isSelected = selected)
                fireTableCellUpdated(row, TableColumn.SELECTED.ordinal)
            }
        }
    }

    fun getSelectedCount(): Int = mods.count { it.isSelected }


    override fun getRowCount(): Int = mods.size
    override fun getColumnCount(): Int = columns.size
    override fun getColumnName(column: Int): String = TableColumn.fromIndex(column).displayName
    override fun getColumnClass(column: Int): Class<*> = TableColumn.fromIndex(column).type
    override fun isCellEditable(row: Int, column: Int): Boolean = column == TableColumn.SELECTED.ordinal

    override fun getValueAt(row: Int, column: Int): Comparable<*>? {
        val mod = mods[row]
        return when (TableColumn.fromIndex(column)) {
            TableColumn.SELECTED -> mod.isSelected
            TableColumn.ICON -> mod.iconPath
            TableColumn.NAME -> mod.modName
            TableColumn.FILENAME -> mod.fileName
            TableColumn.SIZE -> mod.size
            TableColumn.LAST_MODIFIED -> mod.getFormattedDate()
        }
    }

    override fun setValueAt(value: Any?, row: Int, column: Int) {
        if (column == TableColumn.SELECTED.ordinal && value is Boolean) {
            mods[row] = mods[row].copy(isSelected = value)
            fireTableCellUpdated(row, column)
        }
    }

    fun getModAt(row: Int): ModListItem = mods[row]
}
