package com.dominions.modmerger.ui.components

import com.dominions.modmerger.infrastructure.ApplicationConfig.logger
import com.dominions.modmerger.infrastructure.Logging
import com.dominions.modmerger.ui.model.ModListItem
import com.sun.java.accessibility.util.AWTEventMonitor.addComponentListener
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.*
import javax.swing.event.HyperlinkEvent
import javax.swing.text.html.HTMLDocument

class StatusPanel(
    private val onModSelected: (ModListItem) -> Unit
) : JPanel(BorderLayout()), Logging {

    private val statusLabel = JEditorPane("text/html", "").apply {
        isEditable = false
        isOpaque = false
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
        border = BorderFactory.createEmptyBorder(5, 5, 5, 5)

        // In StatusPanel.kt
        addHyperlinkListener { event ->
            if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                debug("Hyperlink clicked: '${event.description}'", useDispatcher = false)
                debug("Available mods: ${currentMods.map { "'${it.modName}'" }}" , useDispatcher = false)

                val clickedMod = currentMods.find { mod ->
                    trace("Comparing '${mod.modName}' with '${event.description}'", useDispatcher = false)
                    mod.modName == event.description
                }

                if (clickedMod == null) {
                    warn("No matching mod found for '${event.description}'", useDispatcher = false)
                } else {
                    debug("Found matching mod: '${clickedMod.modName}'", useDispatcher = false)
                    onModSelected(clickedMod)
                }
            }
        }
    }
    private var currentMods: List<ModListItem> = emptyList()

    init {
        setupComponents()
        setupResizeListener()
    }

    private fun setupComponents() {
        val scrollPane = JScrollPane(statusLabel).apply {
            border = BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY)
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            preferredSize = Dimension(0, 60)
        }

        add(scrollPane, BorderLayout.CENTER)
    }

    private fun setupResizeListener() {
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                SwingUtilities.invokeLater { updateSelectionText() }
            }
        })
    }

    fun updateStatus(selectedMods: List<ModListItem>) {
        currentMods = selectedMods
        updateSelectionText()
    }

    private fun updateSelectionText() {
        if (currentMods.isEmpty()) {
            statusLabel.text = " "
            return
        }

        val parentWidth = (statusLabel.parent?.width ?: width) - 20 // Account for padding
        statusLabel.text = buildHtmlContent(parentWidth)

        // Update link style in the HTML document
        (statusLabel.document as? HTMLDocument)?.styleSheet?.addRule(
            """
                    a { color: #000000; text-decoration: underline; }
                    a:hover { cursor: pointer; }
                """.trimIndent()
        )
    }

    private fun buildHtmlContent(width: Int): String {
        // Create temporary label to measure text width
        val tempLabel = JLabel()
        val metrics = tempLabel.getFontMetrics(tempLabel.font)

        // Calculate the width of the prefix
        val prefix = createSelectionPrefix()
        val prefixWidth = metrics.stringWidth(prefix.replace(Regex("<[^>]*>"), ""))

        // Organize mods into lines
        val lines = mutableListOf<MutableList<ModListItem>>()
        var currentLine = mutableListOf<ModListItem>()
        var currentLineWidth = prefixWidth

        currentMods.forEach { mod ->
            // Calculate width of this mod element (including separator)
            val modText = "${mod.modName} | "
            val modWidth = metrics.stringWidth(modText)

            if (currentLineWidth + modWidth > width && currentLine.isNotEmpty()) {
                // Start new line if this mod won't fit
                lines.add(currentLine)
                currentLine = mutableListOf(mod)
                currentLineWidth = prefixWidth + modWidth
            } else {
                // Add to current line
                currentLine.add(mod)
                currentLineWidth += modWidth
            }
        }

        // Add the last line if it's not empty
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }

        // Build the final HTML content
        return buildString {
            append(
                """
                <html>
                <body style='font-family: ${statusLabel.font.family}; font-size: ${statusLabel.font.size}pt;'>
            """.trimIndent()
            )

            lines.forEachIndexed { index, lineItems ->
                if (index > 0) append("<br>")
                if (index == 0) {
                    append(prefix)
                } else {
                    // Add proper indentation for continuation lines
                    append("&nbsp;".repeat(prefix.length - prefix.count { it == '>' } * 4))
                }
                append(createModLine(lineItems))
            }
            append("</body></html>")
        }
    }

    private fun createSelectionPrefix(): String {
        val suffix = if (currentMods.size != 1) "s" else ""
        return "Selected ${currentMods.size} mod$suffix: "
    }

    private fun createModLine(mods: List<ModListItem>): String = buildString {
        mods.forEachIndexed { index, mod ->
            val color = ColorUtils.getHexColor(ColorUtils.getColorForModName(mod.modName))
            append("""<a style='color: $color' href="${mod.modName}">${mod.modName}</a>""")
            if (index < mods.size - 1) {
                append(" | ")
            }
        }
    }

    private object ColorUtils {
        private val colorCache = mutableMapOf<String, Color>()

        fun getColorForModName(modName: String): Color {
            val baseName = modName.lowercase().replace(Regex("[^a-z]"), "")
            return colorCache.getOrPut(baseName) {
                generateColorFromHash(baseName.hashCode())
            }
        }

        private fun generateColorFromHash(hash: Int): Color = Color.getHSBColor(
            (hash and 0xFF) / 255f,
            0.4f + ((hash shr 8) and 0xFF) / 512f,
            0.8f + ((hash shr 16) and 0xFF) / 512f
        )

        fun getHexColor(color: Color): String =
            String.format("#%02x%02x%02x", color.red, color.green, color.blue)
    }
}