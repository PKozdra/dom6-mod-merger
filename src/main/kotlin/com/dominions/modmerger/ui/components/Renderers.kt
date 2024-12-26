package com.dominions.modmerger.ui.components

import com.dominions.modmerger.infrastructure.Logging
import com.dominions.modmerger.ui.model.ModSourceType
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.image.BufferedImage
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer

class SourceTypeRenderer : DefaultTableCellRenderer(), Logging {

    init {
        horizontalAlignment = SwingConstants.CENTER
        border = BorderFactory.createEmptyBorder()
    }

    private val iconSize = 32 // Default icon size
    private val steamIcon = loadIcon("/icons/steam_icon.png", true)
    private val localIcon = loadIcon("/icons/folder_icon.png", false)

    private fun loadIcon(resourcePath: String, isSteam: Boolean): Icon? {
        return try {
            val resource = ModTablePanel::class.java.getResource(resourcePath)
            if (resource != null && resourcePath.endsWith(".png", ignoreCase = true)) {
                val image = ImageIcon(resource).image
                val scaledImage = image.getScaledInstance(iconSize, iconSize, java.awt.Image.SCALE_SMOOTH)
                ImageIcon(scaledImage)
            } else {
                warn("Resource not found or unsupported format: $resourcePath", useDispatcher = false)
                createFallbackIcon(isSteam)
            }
        } catch (e: Exception) {
            error("Error loading icon from resource: $resourcePath", e, useDispatcher = false)
            createFallbackIcon(isSteam)
        }
    }

    private fun createFallbackIcon(isSteam: Boolean): ImageIcon {
        require(iconSize > 0) { "Icon size must be greater than 0. Current size: $iconSize" }
        return ImageIcon(BufferedImage(iconSize, iconSize, BufferedImage.TYPE_INT_ARGB).apply {
            createGraphics().apply {
                color = if (isSteam) {
                    Color(0, 120, 180) // Steam blue
                } else {
                    Color(100, 100, 100) // Folder gray
                }
                fillRect(0, 0, iconSize, iconSize)
                dispose()
            }
        })
    }

    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column)

        icon = when (value) {
            ModSourceType.STEAM -> steamIcon
            ModSourceType.LOCAL -> localIcon
            else -> {
                warn("Unknown source type: $value", useDispatcher = false)
                null
            }
        }

        toolTipText = when (value) {
            ModSourceType.STEAM -> "Steam Workshop Mod"
            ModSourceType.LOCAL -> "Local Mod"
            else -> "Unknown Source"
        }

        return this
    }
}

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

class ModNameRenderer(
    private val alignment: Int = SwingConstants.LEFT
) : TableCellRenderer {
    private val panel = JPanel(BorderLayout(5, 0))
    private val label = JLabel().apply {
        border = BorderFactory.createEmptyBorder(0, 5, 0, 5)
        isOpaque = true
        horizontalAlignment = alignment
    }

    private val groupIcon = JLabel().apply {
        // Load our custom chain icon
        icon = IconLoader.loadIcon(
            resourcePath = "/icons/warhammer_icon.png",
            iconSize = 32,
            fallbackColor = Color(0, 120, 180)  // Use Steam blue as fallback
        )
        border = EmptyBorder(0, 2, 0, 2)
        isVisible = false
    }

    init {
        panel.isOpaque = true
        panel.add(groupIcon, BorderLayout.WEST)
        panel.add(label, BorderLayout.CENTER)
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

        // Convert view index to model index
        val modelRow = if (table.rowSorter != null) {
            table.rowSorter.convertRowIndexToModel(row)
        } else {
            row
        }

        val model = table.model as ModTableModel
        val mod = model.getModAt(modelRow)  // Use modelRow instead of row
        groupIcon.isVisible = mod.group != null

        if (mod.group != null) {
            panel.toolTipText = "<html><b>${mod.group.name}</b><br>${mod.group.description}</html>"
        } else {
            panel.toolTipText = null
        }

        panel.background = if (isSelected) table.selectionBackground else table.background
        panel.foreground = if (isSelected) table.selectionForeground else table.foreground
        label.background = panel.background
        label.foreground = panel.foreground

        return panel
    }
}