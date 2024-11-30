// src/main/kotlin/com/dominions/modmerger/ui/components/IconRenderer.kt
package com.dominions.modmerger.ui.components

import com.dominions.modmerger.ui.util.IconCache
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.table.TableCellRenderer

class IconRenderer(
    private val targetWidth: Int,
    private val targetHeight: Int
) : TableCellRenderer {
    private val label = JLabel().apply {
        horizontalAlignment = SwingConstants.CENTER
    }

    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component = label.apply {
        icon = IconCache.getIcon(value as? String, targetWidth, targetHeight)
        background = if (isSelected) table.selectionBackground else table.background
        foreground = if (isSelected) table.selectionForeground else table.foreground
        isOpaque = true
    }
}
