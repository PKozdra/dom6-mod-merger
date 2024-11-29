package com.dominions.modmerger.ui.components

import java.awt.*
import java.awt.image.BufferedImage
import java.util.*
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

class IconRenderer(private val targetWidth: Int, private val targetHeight: Int) : TableCellRenderer {
    private val label = JLabel().apply {
        horizontalAlignment = SwingConstants.CENTER
    }
    private val iconCache = WeakHashMap<Icon, Icon>()
    private val unknownIcon by lazy { createUnknownIcon() }

    private fun createUnknownIcon(): Icon {
        val image = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB)
        val g2d = image.createGraphics()

        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

            g2d.color = Color(240, 240, 240)
            g2d.fillRect(0, 0, targetWidth, targetHeight)
            g2d.color = Color(220, 220, 220)
            g2d.drawRect(0, 0, targetWidth - 1, targetHeight - 1)

            val fontSize = (targetHeight * 0.4).toInt()
            g2d.font = Font("SansSerif", Font.BOLD, fontSize)

            val text = "NO ICON"
            val metrics = g2d.fontMetrics
            val x = (targetWidth - metrics.stringWidth(text)) / 2
            val y = (targetHeight - metrics.height) / 2 + metrics.ascent

            g2d.color = Color(200, 200, 200)
            g2d.drawString(text, x + 1, y + 1)
            g2d.color = Color(150, 150, 150)
            g2d.drawString(text, x, y)
        } finally {
            g2d.dispose()
        }

        return ImageIcon(image)
    }

    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component = label.apply {
        icon = getOrCreateIcon(value)
        background = if (isSelected) table.selectionBackground else table.background
        foreground = if (isSelected) table.selectionForeground else table.foreground
        isOpaque = true
    }

    private fun getOrCreateIcon(value: Any?): Icon {
        if (value == null || value !is Icon || value.iconWidth <= 0 || value.iconHeight <= 0) {
            return unknownIcon
        }

        if (value.iconWidth == targetWidth && value.iconHeight == targetHeight) {
            return value
        }

        return iconCache.getOrPut(value) {
            try {
                scaleIcon(value)
            } catch (e: Exception) {
                unknownIcon
            }
        }
    }

    private fun scaleIcon(originalIcon: Icon): Icon {
        val bufferedImage = when (originalIcon) {
            is ImageIcon -> {
                val image = originalIcon.image
                if (image is BufferedImage) image
                else {
                    val bi = BufferedImage(originalIcon.iconWidth, originalIcon.iconHeight, BufferedImage.TYPE_INT_ARGB)
                    val g = bi.createGraphics()
                    originalIcon.paintIcon(null, g, 0, 0)
                    g.dispose()
                    bi
                }
            }

            else -> {
                val bi = BufferedImage(originalIcon.iconWidth, originalIcon.iconHeight, BufferedImage.TYPE_INT_ARGB)
                val g = bi.createGraphics()
                originalIcon.paintIcon(null, g, 0, 0)
                g.dispose()
                bi
            }
        }

        val scaled = bufferedImage.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH)
        return ImageIcon(scaled)
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