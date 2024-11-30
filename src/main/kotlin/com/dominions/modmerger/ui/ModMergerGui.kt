// src/main/kotlin/com/dominions/modmerger/ui/ModMergerGui.kt
package com.dominions.modmerger.ui

import com.dominions.modmerger.core.ModMergerService
import com.dominions.modmerger.domain.LogDispatcher
import com.dominions.modmerger.domain.LogLevel
import com.dominions.modmerger.domain.LogListener
import com.dominions.modmerger.infrastructure.FileSystem
import com.dominions.modmerger.ui.components.ModTablePanel
import com.dominions.modmerger.ui.components.OutputPanel
import mu.KotlinLogging
import java.awt.*
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.prefs.Preferences
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * Main GUI component for the Mod Merger application.
 * Provides interface for mod selection, merging, and output monitoring.
 */
class ModMergerGui(
    modMergerService: ModMergerService,
    fileSystem: FileSystem,
    private val logDispatcher: LogDispatcher
) : LogListener {
    private val logger = KotlinLogging.logger {}
    private val preferences = Preferences.userRoot().node("com/dominions/modmerger")

    private val frame = JFrame("Dominions 6 Mod Merger").apply {
        defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        minimumSize = Dimension(1024, 768)
    }

    private val modTable = ModTablePanel()
    private val outputPanel = OutputPanel()
    private val controller = ModMergerController(modMergerService, fileSystem, logDispatcher)

    // UI Components
    private val mergeButton = JButton("Merge Selected Mods")
    private val customPathField = JTextField(20)
    private val refreshButton = JButton("Refresh")
    private val configButton = JButton("Config")

    init {
        logDispatcher.addListener(this)
        setupWindow()
        setupComponents()
        setupController()
        loadInitialState()
    }

    override fun onLogMessage(level: LogLevel, message: String) {
        outputPanel.appendLogMessage(level, message)
    }

    private fun setupWindow() {
        frame.apply {
            setLocationRelativeTo(null)
            addWindowListener(object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent) {
                    saveConfig()
                    dispose()
                }
            })
        }
    }

    private fun setupComponents() {
        // Main content layout
        frame.contentPane.apply {
            layout = BorderLayout(10, 10)
            add(createControlPanel(), BorderLayout.NORTH)
            add(createMainContent(), BorderLayout.CENTER)
        }
    }

    private fun createControlPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            border = EmptyBorder(10, 10, 5, 10)

            // Top row with main action buttons
            add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                add(mergeButton)
                add(refreshButton)
                add(configButton)
            }, BorderLayout.NORTH)

            // Bottom row with custom path input
            add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                add(JLabel("Custom Mod Path:"))
                add(customPathField)
            }, BorderLayout.CENTER)

            // Setup button actions
            mergeButton.addActionListener { handleMergeButton() }
            refreshButton.addActionListener {
                controller.loadMods(customPathField.text)
            }
            configButton.addActionListener { handleConfigButton() }
        }
    }

    private fun createMainContent(): JSplitPane {
        return JSplitPane(JSplitPane.VERTICAL_SPLIT).apply {
            topComponent = createModTablePanel()
            bottomComponent = outputPanel
            resizeWeight = 0.7 // Gives 70% of space to mod table by default
            border = null // Remove border to prevent double borders
        }
    }

    private fun createModTablePanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("Available Mods")
            add(modTable, BorderLayout.CENTER)
        }
    }

    private fun setupController() {
        controller.setModLoadListener { modItems ->
            modTable.updateMods(modItems)
        }
    }

    private fun loadInitialState() {
        customPathField.text = preferences.get("customPath", "")
        controller.loadMods(customPathField.text)
    }

    private fun handleMergeButton() {
        val selectedMods = modTable.getSelectedMods().map { it.modFile }

        if (selectedMods.isEmpty()) {
            showWarningDialog(
                "Please select at least one mod to merge",
                "No Mods Selected"
            )
            return
        }

        mergeButton.isEnabled = false
        controller.mergeMods(selectedMods) {
            mergeButton.isEnabled = true
        }
    }

    private fun handleConfigButton() {
        val result = showConfirmDialog(
            "Are you sure you want to clear all settings?",
            "Clear Configuration"
        )

        if (result == JOptionPane.YES_OPTION) {
            preferences.clear()
            customPathField.text = ""
            showInfoDialog("Configuration cleared!", "Config")
        }
    }

    private fun showWarningDialog(message: String, title: String) {
        JOptionPane.showMessageDialog(
            frame,
            message,
            title,
            JOptionPane.WARNING_MESSAGE
        )
    }

    private fun showInfoDialog(message: String, title: String) {
        JOptionPane.showMessageDialog(
            frame,
            message,
            title,
            JOptionPane.INFORMATION_MESSAGE
        )
    }

    private fun showConfirmDialog(message: String, title: String): Int {
        return JOptionPane.showConfirmDialog(
            frame,
            message,
            title,
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        )
    }

    private fun saveConfig() {
        preferences.put("customPath", customPathField.text)
    }

    fun show() {
        frame.pack()
        frame.isVisible = true
    }

    private fun dispose() {
        frame.dispose()
    }
}