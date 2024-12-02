package com.dominions.modmerger.ui

import com.dominions.modmerger.core.ModMerger
import com.dominions.modmerger.domain.LogDispatcher
import com.dominions.modmerger.domain.LogLevel
import com.dominions.modmerger.domain.LogListener
import com.dominions.modmerger.infrastructure.FileSystem
import com.dominions.modmerger.infrastructure.GamePathsManager
import com.dominions.modmerger.ui.components.ModTablePanel
import com.dominions.modmerger.ui.components.OutputPanel
import mu.KotlinLogging
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Color
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
    modMerger: ModMerger,
    fileSystem: FileSystem,
    gamePathsManager: GamePathsManager,
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
    private val controller = ModMergerController(modMerger, fileSystem, gamePathsManager, logDispatcher)

    // UI Components
    private val mergeButton = JButton("Merge Selected Mods")
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
                    dispose()
                }
            })
        }
    }

    private fun setupComponents() {
        frame.contentPane.apply {
            layout = BorderLayout(10, 10)
            add(createControlPanel(), BorderLayout.NORTH)
            add(createMainContent(), BorderLayout.CENTER)
            add(createFooter(), BorderLayout.SOUTH)  // Add this line
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

            // Setup button actions
            mergeButton.addActionListener { handleMergeButton() }
            refreshButton.addActionListener {
                controller.loadMods()
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

    companion object {
        const val APP_VERSION = "0.0.1"  // Update this when version changes
    }

    private fun createFooter(): JPanel {
        return JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
            border = EmptyBorder(5, 10, 5, 10)

            val attributionText = JLabel("v$APP_VERSION • Created by Druwski").apply {
                font = Font(font.family, Font.PLAIN, 12)
            }

            add(attributionText)
        }
    }

    private fun setupController() {
        controller.setModLoadListener { modItems ->
            modTable.updateMods(modItems)
        }
    }

    private fun loadInitialState() {
        controller.loadMods()
    }

    private fun handleMergeButton() {
        val selectedMods = modTable.getSelectedMods().map { it.modFile }

        if (selectedMods.isEmpty()) {
            showWarningDialog(
                "Please select at least two mods to merge",
                "No Mods Selected"
            )
            return
        }

        if (selectedMods.size < 2) {
            showWarningDialog(
                "Please select at least two mods to merge",
                "Not Enough Mods Selected"
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

    fun show() {
        frame.pack()
        frame.isVisible = true
    }

    private fun dispose() {
        frame.dispose()
    }
}