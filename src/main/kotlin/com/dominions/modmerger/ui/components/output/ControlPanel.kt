package com.dominions.modmerger.ui.components.output

import com.dominions.modmerger.infrastructure.PreferencesManager
import java.awt.FlowLayout
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.*

class ControlPanel(private val outputPanel: OutputPanel) : JPanel(FlowLayout(FlowLayout.LEFT, 5, 5)) {

    init {
        add(createWordWrapToggle())
        add(createClearButton())
        add(createExportButton())
        add(createLineLimitField())  // Added line limit adjustment
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
}
