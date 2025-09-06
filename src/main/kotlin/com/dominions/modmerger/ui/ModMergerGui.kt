package com.dominions.modmerger.ui

import com.dominions.modmerger.core.ModMerger
import com.dominions.modmerger.core.writing.config.ModOutputConfigManager
import com.dominions.modmerger.domain.ModGroupRegistry
import com.dominions.modmerger.gamedata.GameDataProvider
import com.dominions.modmerger.infrastructure.ApplicationConfig
import com.dominions.modmerger.infrastructure.FileSystem
import com.dominions.modmerger.infrastructure.GamePathsManager
import com.dominions.modmerger.infrastructure.PreferencesManager
import com.dominions.modmerger.ui.components.ModOutputConfigPanel
import com.dominions.modmerger.ui.components.ModPresetsPanel
import com.dominions.modmerger.ui.components.ModTablePanel
import com.dominions.modmerger.ui.components.output.OutputPanel
import com.dominions.modmerger.ui.components.settings.SettingsDialog
import java.awt.*
import java.util.prefs.Preferences
import javax.swing.*
import javax.swing.border.EmptyBorder


class ModMergerGui(
    modMerger: ModMerger,
    fileSystem: FileSystem,
    gamePathsManager: GamePathsManager,
    groupRegistry: ModGroupRegistry,
    gameDataProvider: GameDataProvider
) {
    private val preferences = Preferences.userRoot().node("com/dominions/modmerger")

    private val frame = JFrame(ApplicationConfig.APP_NAME).apply {
        defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        minimumSize = Dimension(1024, 768)
        setLocationRelativeTo(null)  // Center the window on screen
    }

    private val modTable = ModTablePanel()
    private val outputPanel = OutputPanel()
    private val presetsPanel = ModPresetsPanel()
    private val controller = ModMergerController(modMerger, gamePathsManager, groupRegistry, gameDataProvider)

    // UI Components
    private val mergeButton = createStyledMergeButton()

    // Add to class properties
    private val settingsButton = JButton("Settings").apply {
        addActionListener { showSettings() }
    }

    private val resetConfigButton = JButton("Reset Config")
    private var processingTimer: Timer? = null
    private var processingDots = 0

    private val configManager = ModOutputConfigManager(fileSystem, gamePathsManager)
    private val outputConfigPanel = ModOutputConfigPanel(configManager).apply {
        addConfigChangeListener { config ->
            config?.let { configManager.saveConfig(it) }
        }
    }
    private val outputConfigToggle = JButton("Output Settings ▼").apply {
        addActionListener { toggleOutputConfig() }
    }
    private var isOutputConfigVisible = false

    init {
        setupWindow()
        setupComponents()
        setupPresetPanel()
        setupController()
        loadInitialState()

        // Add selection change listener
        modTable.setSelectionChangeListener { selectedMods ->
            controller.saveSelections(selectedMods)
        }
    }

    private fun setupWindow() {
        frame.apply {
            setLocationRelativeTo(null)  // Center on screen
        }
    }

    private fun setupComponents() {
        frame.contentPane.apply {
            layout = BorderLayout(10, 10)
            add(createControlPanel(), BorderLayout.NORTH)
            add(createMainContent(), BorderLayout.CENTER)
            add(createFooter(), BorderLayout.SOUTH)
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

    private fun createFooter(): JPanel {
        return JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
            border = EmptyBorder(5, 10, 5, 10)
            val version = ApplicationConfig.APP_VERSION
            val attributionText = JLabel("v$version • Created by Druwski").apply {
                font = Font(font.family, Font.PLAIN, 12)
            }
            add(attributionText)
        }
    }

    private fun setupController() {
        controller.setModLoadListener { modItems ->
            modTable.updateMods(modItems)
        }

        controller.setOutputConfigProvider {
            outputConfigPanel.getConfiguration()
        }

        controller.setSelectedModsProvider {
            modTable.getSelectedMods()
        }

        controller.setUpdateSelectionStatesCallback { selectedPaths ->
            modTable.updateSelectionStates(selectedPaths)
        }

        controller.setAllAvailableModsProvider {
            modTable.getAllMods()
        }

        controller.setMissingModsCallback { missingMods ->
            if (missingMods.isNotEmpty()) {
                showWarningDialog(
                    "The following mods from the preset could not be found and were not selected:\n" +
                            missingMods.joinToString("\n"),
                    "Missing Mods in Preset"
                )
            }
        }

        outputConfigPanel.addValidationListener { isValid ->
            mergeButton.isEnabled = isValid
        }
    }

    private fun loadInitialState() {
        controller.loadMods()
        updatePresetsList()
    }

    private fun toggleOutputConfig() {
        isOutputConfigVisible = !isOutputConfigVisible
        outputConfigToggle.text = if (isOutputConfigVisible) "Output Settings ▲" else "Output Settings ▼"
        (outputConfigPanel.parent as? JPanel)?.isVisible = isOutputConfigVisible
        frame.revalidate()
        frame.repaint()
    }

    private fun showSettings() {
        SettingsDialog(
            frame,
            onPathsChanged = { handleRefreshButton() }
        ).isVisible = true
    }

    private fun createStyledMergeButton(): JButton {
        return JButton("Merge Selected Mods").apply {
            font = Font(font.family, Font.BOLD, 14)
            preferredSize = Dimension(200, 40)
            background = Color(52, 152, 219) // Nice blue color
            foreground = Color.WHITE
            isFocusPainted = false
            isContentAreaFilled = true
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color(41, 128, 185), 1),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)
            )

            // Add hover effect
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseEntered(e: java.awt.event.MouseEvent) {
                    if (isEnabled) background = Color(41, 128, 185)
                }

                override fun mouseExited(e: java.awt.event.MouseEvent) {
                    if (isEnabled) background = Color(52, 152, 219)
                }
            })
        }
    }

    private fun createControlPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            border = EmptyBorder(10, 10, 5, 10)

            // Create a two-row layout
            val topSection = JPanel(BorderLayout()).apply {
                // First row - Main action button and settings
                add(JPanel(BorderLayout()).apply {
                    // Left side - Merge button (standalone for prominence)
                    add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                        add(mergeButton)
                    }, BorderLayout.WEST)

                    // Right side - Settings buttons
                    add(JPanel(FlowLayout(FlowLayout.RIGHT, 5, 0)).apply {
                        add(settingsButton)
                        add(JButton("Refresh Mods").apply {
                            addActionListener { handleRefreshButton() }
                        })
                        add(resetConfigButton)
                        add(outputConfigToggle)
                    }, BorderLayout.EAST)
                }, BorderLayout.NORTH)

                // Second row - Presets panel (dedicated row for better visibility)
                add(JPanel(BorderLayout()).apply {
                    border = EmptyBorder(8, 0, 0, 0) // Add some spacing from the top row
                    add(JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
                        add(presetsPanel)
                    }, BorderLayout.EAST)
                }, BorderLayout.SOUTH)
            }

            add(topSection, BorderLayout.NORTH)

            // Hidden output config panel
            add(JPanel(BorderLayout()).apply {
                add(outputConfigPanel)
                isVisible = false
            }, BorderLayout.CENTER)

            // Setup button actions
            mergeButton.addActionListener { handleMergeButton() }
            resetConfigButton.addActionListener { handleResetConfigButton() }
        }
    }

    private fun handleRefreshButton() {
        val selectedPaths = modTable.getSelectedMods()
            .mapNotNull { it.modFile.file?.absolutePath }
            .toSet()

        controller.loadMods { newMods ->
            if (PreferencesManager.isAutoRestoreEnabled) {
                modTable.updateModsPreservingSelections(newMods, selectedPaths)
            } else {
                modTable.updateMods(newMods)
            }
        }
    }

    private fun startProcessingAnimation() {
        processingTimer?.stop()
        processingTimer = Timer(500) { _ ->
            processingDots = (processingDots + 1) % 4
            val dots = ".".repeat(processingDots)
            mergeButton.text = "Processing$dots"
        }
        processingTimer?.start()
    }

    private fun stopProcessingAnimation() {
        processingTimer?.stop()
        processingTimer = null
        mergeButton.text = "Merge Selected Mods"
    }

    private fun handleMergeButton() {
        val selectedMods = modTable.getSelectedMods().map { it.modFile }

        if (selectedMods.size < 2) {
            showWarningDialog(
                "Please select at least two mods to merge",
                "Not Enough Mods Selected"
            )
            return
        }

        val config = outputConfigPanel.getConfiguration() ?: return

        // Check for existing files
        val existingFiles = controller.checkExistingFiles(config)
        if (existingFiles > 0) {
            val result = showConfirmDialog(
                "Directory '${config.directory.absolutePath}\\${config.modName}' already has $existingFiles files in it. " +
                        "These files might be overwritten. Are you sure you want to continue?",
                "Existing Files Warning"
            )

            if (result != JOptionPane.YES_OPTION) {
                return
            }
        }

        mergeButton.isEnabled = false
        startProcessingAnimation()
        outputPanel.pauseSearchUpdates = true

        controller.mergeMods(selectedMods, config) {
            outputPanel.pauseSearchUpdates = false
            mergeButton.isEnabled = true
            stopProcessingAnimation()
        }
    }

    private fun handleResetConfigButton() {
        val result = showConfirmDialog(
            "Are you sure you want to clear all settings?\n" +
                    "This will remove all saved mod selections, presets, and output settings.",
            "Clear Configuration"
        )

        if (result == JOptionPane.YES_OPTION) {
            preferences.clear()
            updatePresetsList()
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


    private fun setupPresetPanel() {
        // Set up a callback for missing mods
        controller.setMissingModsCallback { missingMods ->
            if (missingMods.isNotEmpty()) {
                showWarningDialog(
                    "The following mods from the preset could not be found and were not selected:\n" +
                            "${missingMods.joinToString("\n")}",
                    "Missing Mods in Preset"
                )
            }
        }

        presetsPanel.setOnSavePreset { name, overwrite ->
            val success = controller.savePreset(name, overwrite)
            if (success) {
                updatePresetsList()
                presetsPanel.selectPreset(name)
            } else {
                showWarningDialog(
                    "Failed to save preset. Make sure you have selected mods.",
                    "Save Preset Failed"
                )
            }
        }

        presetsPanel.setOnLoadPreset { name ->
            val success = controller.loadPreset(name)
            if (!success) {
                    showWarningDialog(
                        "Failed to load preset. The preset may be missing or corrupted.",
                        "Load Preset Failed"
                    )
            }
        }

        presetsPanel.setOnDeletePreset { name ->
            val success = controller.deletePreset(name)
            if (success) {
                updatePresetsList()
            } else {
                showWarningDialog(
                    "Failed to delete preset.",
                    "Delete Preset Failed"
                )
            }
        }

        updatePresetsList()
    }

    private fun updatePresetsList() {
        val presets = controller.getAllPresets()
        presetsPanel.updatePresets(presets)
    }


    fun show() {
        frame.pack()
        frame.isVisible = true
    }

    private fun dispose() {
        outputConfigPanel.cleanup() // Clean up debounce timer
        frame.dispose()
    }
}