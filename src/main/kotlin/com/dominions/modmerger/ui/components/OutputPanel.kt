// src/main/kotlin/com/dominions/modmerger/ui/components/OutputPanel.kt
package com.dominions.modmerger.ui.components

import com.dominions.modmerger.domain.LogLevel
import com.dominions.modmerger.ui.util.NoWrapEditorKit
import java.awt.*
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.text.StyleConstants
import javax.swing.text.StyledDocument

/**
 * A panel that displays log output with configurable log levels and text display options.
 * Features include:
 * - Filterable log levels with clear visual indicators
 * - Word wrap toggle
 * - Color-coded log messages
 * - Log export functionality
 */
class OutputPanel : JPanel(BorderLayout()) {
    private val outputPane = JTextPane()
    private val scrollPane = JScrollPane(outputPane)
    private val controlPanel = ControlPanel()
    private val logLevelPanel = LogLevelPanel()

    private var isWordWrapEnabled = true
    private val activeLogLevels = mutableSetOf<LogLevel>()
    private val logBuffer = StringBuilder()

    init {
        setupPanel()
        setupOutputPane()
        setupScrollPane()
        setupLogLevels()
        setupControlPanel()
    }

    private fun setupPanel() {
        border = EmptyBorder(10, 10, 10, 10)
        preferredSize = Dimension(800, 300)
    }

    private fun setupOutputPane() {
        outputPane.apply {
            isEditable = false
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            updateWordWrap(true)
        }
    }

    private fun setupScrollPane() {
        scrollPane.apply {
            border = BorderFactory.createTitledBorder("Output")
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
        }
        add(scrollPane, BorderLayout.CENTER)
    }

    private fun setupLogLevels() {
        activeLogLevels.addAll(LogLevel.entries)
        add(logLevelPanel, BorderLayout.NORTH)
    }

    private fun setupControlPanel() {
        add(controlPanel, BorderLayout.SOUTH)
    }

    private fun updateWordWrap(enabled: Boolean) {
        isWordWrapEnabled = enabled
        val content = outputPane.document.getText(0, outputPane.document.length)
        outputPane.editorKit = if (enabled) {
            javax.swing.text.StyledEditorKit()
        } else {
            NoWrapEditorKit()
        }
        outputPane.document.remove(0, outputPane.document.length)

        // Reapply the content with appropriate styling
        content.split('\n').forEach { line ->
            val level = when {
                line.contains("[INFO]") -> LogLevel.INFO
                line.contains("[WARN]") -> LogLevel.WARN
                line.contains("[ERROR]") -> LogLevel.ERROR
                line.contains("[DEBUG]") -> LogLevel.DEBUG
                else -> LogLevel.INFO
            }
            appendColoredText(line + "\n", level)
        }
    }

    fun appendLogMessage(level: LogLevel, message: String) {
        if (level !in activeLogLevels) return

        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        val formattedMessage = "[$timestamp][${level.name}] $message\n"

        SwingUtilities.invokeLater {
            appendColoredText(formattedMessage, level)
            logBuffer.append(formattedMessage)
            outputPane.caretPosition = outputPane.document.length
        }
    }

    private fun appendColoredText(text: String, level: LogLevel) {
        val style = outputPane.addStyle(level.name, null)
        StyleConstants.setForeground(style, getColorForLogLevel(level))

        val doc = outputPane.document as StyledDocument
        doc.insertString(doc.length, text, style)
    }

    private fun getColorForLogLevel(level: LogLevel): Color = when (level) {
        LogLevel.INFO -> Color.BLACK
        LogLevel.WARN -> Color(255, 140, 0)  // Darker orange for better visibility
        LogLevel.ERROR -> Color(180, 0, 0)   // Darker red for better visibility
        LogLevel.DEBUG -> Color(100, 100, 100)
    }

    private inner class ControlPanel : JPanel(FlowLayout(FlowLayout.LEFT, 5, 5)) {
        init {
            add(createWordWrapToggle())
            add(createClearButton())
            add(createExportButton())
        }

        private fun createWordWrapToggle() = JToggleButton("Word Wrap").apply {
            isSelected = isWordWrapEnabled
            addActionListener {
                updateWordWrap(isSelected)
                outputPane.revalidate()
            }
        }

        private fun createClearButton() = JButton("Clear").apply {
            addActionListener {
                outputPane.text = ""
                logBuffer.clear()
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

                if (fileChooser.showSaveDialog(this@OutputPanel) == JFileChooser.APPROVE_OPTION) {
                    try {
                        fileChooser.selectedFile.writeText(logBuffer.toString())
                        JOptionPane.showMessageDialog(
                            this@OutputPanel,
                            "Logs exported successfully!",
                            "Export Success",
                            JOptionPane.INFORMATION_MESSAGE
                        )
                    } catch (e: Exception) {
                        JOptionPane.showMessageDialog(
                            this@OutputPanel,
                            "Failed to export logs: ${e.message}",
                            "Export Error",
                            JOptionPane.ERROR_MESSAGE
                        )
                    }
                }
            }
        }
    }

    private inner class LogLevelPanel : JPanel(FlowLayout(FlowLayout.LEFT, 5, 5)) {
        private val logLevelButtons = mutableMapOf<LogLevel, JToggleButton>()

        init {
            add(JLabel("Log Levels:"))
            setupLogLevelButtons()
            add(createEnableAllButton())
        }

        private fun setupLogLevelButtons() {
            LogLevel.entries.forEach { level ->
                val button = createLogLevelButton(level)
                logLevelButtons[level] = button
                add(button)
            }
        }

        private fun createLogLevelButton(level: LogLevel) = JToggleButton().apply {
            val enabledColor = when (level) {
                LogLevel.INFO -> Color(200, 255, 200)  // Light green
                LogLevel.WARN -> Color(255, 240, 200)  // Light orange
                LogLevel.ERROR -> Color(255, 200, 200) // Light red
                LogLevel.DEBUG -> Color(200, 200, 255) // Light blue
            }

            text = level.name
            isSelected = true
            background = enabledColor

            addActionListener {
                if (isSelected) {
                    activeLogLevels.add(level)
                    background = enabledColor
                } else {
                    activeLogLevels.remove(level)
                    background = UIManager.getColor("Button.background")
                }
            }
        }

        private fun createEnableAllButton() = JButton("Enable All").apply {
            addActionListener {
                // Always enable everything
                logLevelButtons.forEach { (level, button) ->
                    button.isSelected = true
                    activeLogLevels.add(level)
                    button.background = when (level) {
                        LogLevel.INFO -> Color(200, 255, 200)
                        LogLevel.WARN -> Color(255, 240, 200)
                        LogLevel.ERROR -> Color(255, 200, 200)
                        LogLevel.DEBUG -> Color(200, 200, 255)
                    }
                }
            }
        }
    }
}