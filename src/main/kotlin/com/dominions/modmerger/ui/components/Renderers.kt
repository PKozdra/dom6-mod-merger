package com.dominions.modmerger.ui.components

import java.awt.Component
import javax.swing.*
import javax.swing.table.TableCellRenderer

class CheckBoxRenderer : TableCellRenderer {
    private val checkBox = JCheckBox().apply {
        horizontalAlignment = SwingConstants.CENTER
        border = BorderFactory.createEmptyBorder()
    }

    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component = checkBox.apply {
        this.isSelected = value as? Boolean ?: false
        background = if (isSelected) table.selectionBackground else table.background
        foreground = if (isSelected) table.selectionForeground else table.foreground
        isOpaque = true
    }
}

class ModNameRenderer : TableCellRenderer {
    // Cache the component instead of creating new ones
    private val label = JLabel().apply {
        border = BorderFactory.createEmptyBorder(0, 5, 0, 5)
        isOpaque = true  // Set this once instead of every render
    }

    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        label.text = value as? String ?: ""
        label.background = if (isSelected) table.selectionBackground else table.background
        label.foreground = if (isSelected) table.selectionForeground else table.foreground
        return label
    }
}

class SizeRenderer : TableCellRenderer {
    private val label = JLabel().apply {
        horizontalAlignment = SwingConstants.RIGHT
        border = BorderFactory.createEmptyBorder(0, 5, 0, 5)
    }

    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component = label.apply {
        text = when (value) {
            is Long -> formatSize(value)
            else -> value?.toString() ?: ""
        }
        background = if (isSelected) table.selectionBackground else table.background
        foreground = if (isSelected) table.selectionForeground else table.foreground
        isOpaque = true
    }

    private fun formatSize(size: Long): String = when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        else -> String.format("%.2f MB", size / (1024.0 * 1024.0))
    }
}