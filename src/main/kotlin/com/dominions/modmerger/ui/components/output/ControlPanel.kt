package com.dominions.modmerger.ui.components.output

import com.dominions.modmerger.infrastructure.PreferencesManager
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.*

class ControlPanel(private val outputPanel: OutputPanel) : JPanel(FlowLayout(FlowLayout.LEFT, 5, 5)) {

    private val fontSizeLabel = JLabel()

    init {
        add(createWordWrapToggle())
        add(createClearButton())
        add(createExportButton())
        add(createHelpLabel())
        add(createLineLimitField())
        add(createFontSizeLabel())
    }

    private fun createWordWrapToggle() = JToggleButton("Word Wrap").apply {
        isSelected = PreferencesManager.isWordWrapEnabled
        outputPanel.updateWordWrap(isSelected) // Initialize with saved preference

        addActionListener {
            PreferencesManager.isWordWrapEnabled = isSelected
            outputPanel.updateWordWrap(isSelected)
            outputPanel.outputPane.revalidate()
        }
    }

    private fun createClearButton() = JButton("Clear").apply {
        addActionListener {
            outputPanel.outputPane.text = ""
            outputPanel.logBuffer.clear()
            outputPanel.searchPanel.documentUpdated()
        }
    }

    private fun createExportButton() = JButton("Export Logs").apply {
        addActionListener {
            val fileChooser = JFileChooser().apply {
                dialogTitle = "Save Log File"
                fileSelectionMode = JFileChooser.FILES_ONLY
                selectedFile = File(
                    "mod_merger_log_${
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                    }.log"
                )
            }
            if (fileChooser.showSaveDialog(outputPanel) == JFileChooser.APPROVE_OPTION) {
                try {
                    // Write logs from the buffer
                    val logs = outputPanel.logBuffer.joinToString("") { it.first }
                    fileChooser.selectedFile.writeText(logs)
                    JOptionPane.showMessageDialog(
                        outputPanel,
                        "Logs exported successfully!",
                        "Export Success",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                } catch (e: Exception) {
                    JOptionPane.showMessageDialog(
                        outputPanel,
                        "Failed to export logs: ${e.message}",
                        "Export Error",
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            }
        }
    }

    private fun createHelpDialog(): JDialog {
        val parentFrame = SwingUtilities.getWindowAncestor(this) as? JFrame
        return JDialog(parentFrame, "Output Panel Help", true).apply {
            val content = JPanel(BorderLayout(5, 5)).apply {
                border = BorderFactory.createEmptyBorder(5, 5, 5, 5)

                add(JPanel(BorderLayout()).apply {
                    add(JTextPane().apply {
                        contentType = "text/html"
                        isEditable = false
                        text = """
                        <html>
                        <h3>Font Size Control</h3>
                        <ul>
                            <li><b>Ctrl + Mouse Wheel</b> to zoom in/out</li>
                            <li><b>Ctrl + Plus</b> to increase size</li>
                            <li><b>Ctrl + Minus</b> to decrease size</li>
                            <li><b>Ctrl + 0</b> to reset to default size</li>
                        </ul>
                        
                        <h3>Search Functionality</h3>
                        <ul>
                            <li><b>Ctrl + F</b> to open search bar</li>
                            <li><b>Enter</b> to find next match</li>
                            <li><b>Shift + Enter</b> to find previous match</li>
                            <li>Toggle <b>Case Sensitive</b> and <b>Whole Word</b> search options</li>
                            <li>Search highlighting updates as you type</li>
                        </ul>
                        
                        <h3>Panel Controls</h3>
                        <ul>
                            <li><b>Word Wrap</b> - Toggle line wrapping for better readability</li>
                            <li><b>Clear</b> - Remove all displayed log content</li>
                            <li><b>Export</b> - Save current log content to a file</li>
                            <li><b>Max Lines</b> - Control how many lines are kept in memory</li>
                        </ul>
                        </html>
                        """.trimIndent()
                        border = BorderFactory.createEmptyBorder(0, 10, 0, 10)
                    }, BorderLayout.CENTER)
                }, BorderLayout.CENTER)

                add(JButton("Close").apply {
                    addActionListener { dispose() }
                }, BorderLayout.SOUTH)
            }

            add(content)
            preferredSize = Dimension(400, 500)
            pack()
            setLocationRelativeTo(parentFrame)
        }
    }

    private fun createHelpLabel() = JLabel(UIManager.getIcon("OptionPane.questionIcon")).apply {
        border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
        cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)

        addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                createHelpDialog().isVisible = true
            }
        })
    }


    private fun createLineLimitField() = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0)).apply {
        add(JLabel("Max Lines:"))
        val lineLimitField = JTextField(5).apply {
            text = outputPanel.maxLogBufferSize.toString()
            addActionListener {
                val newLimit = text.toIntOrNull()
                if (newLimit != null && newLimit > 0) {
                    // Update the buffer size
                    outputPanel.maxLogBufferSize = newLimit
                    synchronized(outputPanel.logBuffer) {
                        while (outputPanel.logBuffer.size > newLimit) {
                            outputPanel.logBuffer.removeFirst()
                        }
                    }
                } else {
                    text = outputPanel.maxLogBufferSize.toString()
                }
            }
        }
        add(lineLimitField)
    }

    private fun createFontSizeLabel() = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0)).apply {
        add(fontSizeLabel.apply {
            updateFontSizeStatus(PreferencesManager.fontSize)
        })
    }

    fun updateFontSizeStatus(size: Int) {
        fontSizeLabel.text = "Font Size: $size"
    }
}
