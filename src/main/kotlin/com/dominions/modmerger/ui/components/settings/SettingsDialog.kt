package com.dominions.modmerger.ui.components.settings

import com.dominions.modmerger.infrastructure.GamePathsManager
import com.dominions.modmerger.infrastructure.PreferencesManager
import java.awt.*
import java.io.File
import javax.swing.*
import javax.swing.border.EmptyBorder

class SettingsDialog(
    parent: Window,
    private val onPathsChanged: () -> Unit = {}
) : JDialog(parent, "Settings", Dialog.ModalityType.APPLICATION_MODAL) {

    private val gamePathsManager = GamePathsManager()
    private val customPathsList = DefaultListModel<String>()
    private var pathsChanged = false

    init {
        createUI()
        loadCustomPaths()
        pack()
        setLocationRelativeTo(parent)  // Center dialog relative to parent window
    }

    private fun loadCustomPaths() {
        customPathsList.clear()
        PreferencesManager.getCustomSteamPaths().forEach { path ->
            customPathsList.addElement(path)
        }
    }

    private fun saveCustomPaths() {
        val oldPaths = PreferencesManager.getCustomSteamPaths().toSet()
        val newPaths = (0 until customPathsList.size()).map { customPathsList.get(it) }.toSet()

        // Check if paths have changed
        if (oldPaths != newPaths) {
            pathsChanged = true
            PreferencesManager.saveCustomSteamPaths(newPaths.toList())
        }
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

        // Add Steam paths section
        settingsPanel.add(createSteamPathsPanel())

        // Add more sections here as needed...

        // Add main panel and buttons
        panel.add(settingsPanel, BorderLayout.CENTER)
        panel.add(createButtonPanel(), BorderLayout.SOUTH)

        contentPane = panel
        minimumSize = Dimension(500, 400)
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

    private fun createSteamPathsPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("Steam Library Paths")

            // Create instructions label
            add(JLabel("<html>Add custom Steam library paths if your mods are not being detected automatically.<br>The path should be to the Steam folder (e.g. F:\\SteamLibrary or E:\\Steam)</html>").apply {
                border = EmptyBorder(5, 5, 5, 5)
            }, BorderLayout.NORTH)

            // Create list of paths
            val pathList = JList(customPathsList)
            val scrollPane = JScrollPane(pathList)

            // Create buttons for adding/removing paths
            val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT))
            buttonPanel.add(JButton("Add Path...").apply {
                addActionListener {
                    addCustomSteamPath()
                }
            })

            buttonPanel.add(JButton("Remove Selected").apply {
                addActionListener {
                    val selectedIndex = pathList.selectedIndex
                    if (selectedIndex != -1) {
                        customPathsList.remove(selectedIndex)
                        saveCustomPaths()
                    }
                }
            })

            // Add components to panel
            add(scrollPane, BorderLayout.CENTER)
            add(buttonPanel, BorderLayout.SOUTH)

            // Set preferred size
            preferredSize = Dimension(450, 200)
        }
    }

    private fun addCustomSteamPath() {
        val fileChooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "Select Steam Library Folder"
            approveButtonText = "Select"

            // Try to start in a reasonable location
            val drives = gamePathsManager.findSteamModPath()?.parentFile?.parentFile?.parentFile
            currentDirectory = drives ?: File(System.getProperty("user.home"))
        }

        val result = fileChooser.showOpenDialog(this)
        if (result == JFileChooser.APPROVE_OPTION) {
            val selectedPath = fileChooser.selectedFile.absolutePath

            // Check if the path is valid (contains steamapps folder)
            val steamappsFolder = File(selectedPath, "steamapps")
            if (!steamappsFolder.exists()) {
                JOptionPane.showMessageDialog(
                    this,
                    "The selected folder doesn't appear to be a valid Steam library folder.\n" +
                            "It should contain a 'steamapps' folder.",
                    "Invalid Steam Library",
                    JOptionPane.WARNING_MESSAGE
                )
                return
            }

            // Don't add duplicates
            if ((0 until customPathsList.size()).none { customPathsList.get(it) == selectedPath }) {
                customPathsList.addElement(selectedPath)
                saveCustomPaths()
            }
        }
    }

    private fun createButtonPanel(): JPanel {
        return JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
            add(JButton("Close").apply {
                addActionListener {
                    if (pathsChanged) {
                        onPathsChanged()
                    }
                    dispose()
                }
            })
        }
    }
}