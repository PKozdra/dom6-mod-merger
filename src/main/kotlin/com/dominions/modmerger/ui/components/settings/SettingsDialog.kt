package com.dominions.modmerger.ui.components.settings

import com.dominions.modmerger.infrastructure.PreferencesManager
import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder

class SettingsDialog(parent: Window) : JDialog(parent, "Settings", Dialog.ModalityType.APPLICATION_MODAL) {
    init {
        createUI()
        pack()
        setLocationRelativeTo(parent)  // Center dialog relative to parent window
    }

    private fun createUI() {
        val panel = JPanel(BorderLayout(10, 10)).apply {
            border = EmptyBorder(10, 10, 10, 10)
        }

        // Main settings panel with vertical BoxLayout
        val settingsPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = EmptyBorder(0, 0, 10, 0)
        }

        // Add mod settings section
        settingsPanel.add(createModSettingsPanel())

        // Add more sections here as needed...

        // Add main panel and buttons
        panel.add(settingsPanel, BorderLayout.CENTER)
        panel.add(createButtonPanel(), BorderLayout.SOUTH)

        contentPane = panel
        minimumSize = Dimension(300, 200)
    }

    private fun createModSettingsPanel(): JPanel {
        return JPanel(GridBagLayout()).apply {
            border = BorderFactory.createTitledBorder("Mod Settings")

            val gbc = GridBagConstraints().apply {
                anchor = GridBagConstraints.WEST
                insets = Insets(5, 5, 5, 5)
                gridx = 0
                gridy = 0
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
            }

            add(JCheckBox("Remember mod selections").apply {
                isSelected = PreferencesManager.isAutoRestoreEnabled
                addActionListener {
                    PreferencesManager.isAutoRestoreEnabled = isSelected
                }
            }, gbc)
        }
    }

    private fun createButtonPanel(): JPanel {
        return JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
            add(JButton("Close").apply {
                addActionListener { dispose() }
            })
        }
    }
}