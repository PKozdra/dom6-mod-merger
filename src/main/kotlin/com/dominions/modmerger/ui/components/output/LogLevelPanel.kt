package com.dominions.modmerger.ui.components.output

import com.dominions.modmerger.infrastructure.LogLevel
import com.dominions.modmerger.infrastructure.PreferencesManager
import java.awt.Color
import java.awt.FlowLayout
import javax.swing.*

class LogLevelPanel(private val outputPanel: OutputPanel) : JPanel(FlowLayout(FlowLayout.LEFT, 5, 5)) {

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
            LogLevel.INFO -> Color(200, 255, 200)
            LogLevel.WARN -> Color(255, 240, 200)
            LogLevel.ERROR -> Color(255, 200, 200)
            LogLevel.DEBUG -> Color(200, 200, 255)
            LogLevel.TRACE -> Color(200, 200, 200)
        }
        text = level.name

        // Load the saved state from preferences
        isSelected = PreferencesManager.isLogLevelEnabled(level)
        background = if (isSelected) enabledColor else UIManager.getColor("Button.background")

        // Update activeLogLevels based on the initial selection
        if (isSelected) {
            outputPanel.activeLogLevels.add(level)
        }

        addActionListener {
            if (isSelected) {
                outputPanel.activeLogLevels.add(level)
                background = enabledColor
            } else {
                outputPanel.activeLogLevels.remove(level)
                background = UIManager.getColor("Button.background")
            }
            PreferencesManager.setLogLevelEnabled(level, isSelected)
        }
    }

    private fun createEnableAllButton() = JButton("Enable All").apply {
        addActionListener {
            logLevelButtons.forEach { (level, button) ->
                button.isSelected = true
                outputPanel.activeLogLevels.add(level)
                button.background = when (level) {
                    LogLevel.INFO -> Color(200, 255, 200)
                    LogLevel.WARN -> Color(255, 240, 200)
                    LogLevel.ERROR -> Color(255, 200, 200)
                    LogLevel.DEBUG -> Color(200, 200, 255)
                    LogLevel.TRACE -> Color(200, 200, 200)
                }
                PreferencesManager.setLogLevelEnabled(level, true)
            }
        }
    }
}
