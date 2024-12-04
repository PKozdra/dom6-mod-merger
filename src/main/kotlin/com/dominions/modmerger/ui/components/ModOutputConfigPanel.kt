package com.dominions.modmerger.ui.components

import com.dominions.modmerger.core.writing.config.ModOutputConfig
import com.dominions.modmerger.core.writing.config.ModOutputConfigManager
import com.dominions.modmerger.infrastructure.Logging
import java.awt.*
import java.io.File
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class ModOutputConfigPanel(
    private val configManager: ModOutputConfigManager,
) : JPanel(), Logging {

    // UI Components - simplified to core requirements
    private val displayNameField = JTextField(20)
    private val modNameField = JTextField(20)
    private val directoryField = JTextField(30)
    private val directoryButton = JButton("Browse...")
    private val previewLabel = JLabel()
    private val validationLabel = JLabel()

    private var isValid = false
        private set(value) {
            field = value
            notifyValidationListeners(value)
        }

    private val validationListeners = mutableListOf<(Boolean) -> Unit>()

    init {
        setupUI()
        setupValidation()
        setupInitialState()
    }

    private fun setupUI() {
        layout = BorderLayout(10, 10)
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        val formPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = Insets(5, 5, 5, 5)
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
        }

        // Display Name (allows spaces)
        gbc.apply {
            gridx = 0; gridy = 0
            gridwidth = 1
        }
        formPanel.add(JLabel("Mod Display Name:"), gbc)

        gbc.apply {
            gridx = 1
            gridwidth = 2
            weightx = 1.0
        }
        formPanel.add(displayNameField, gbc)

        // Technical Mod Name (no spaces)
        gbc.apply {
            gridx = 0; gridy = 1
            gridwidth = 1
            weightx = 0.0
        }
        formPanel.add(JLabel("Technical Name:"), gbc)

        gbc.apply {
            gridx = 1
            gridwidth = 2
        }
        formPanel.add(modNameField, gbc)

        // Directory
        gbc.apply {
            gridx = 0; gridy = 2
            gridwidth = 1
        }
        formPanel.add(JLabel("Directory:"), gbc)

        gbc.apply {
            gridx = 1
            gridwidth = 1
            weightx = 1.0
        }
        formPanel.add(directoryField, gbc)

        gbc.apply {
            gridx = 2
            gridwidth = 1
            weightx = 0.0
        }
        formPanel.add(directoryButton, gbc)

        // Preview
        gbc.apply {
            gridx = 0; gridy = 3
            gridwidth = 3
        }
        formPanel.add(previewLabel, gbc)

        // Validation
        gbc.apply {
            gridx = 0; gridy = 4
            gridwidth = 3
        }
        formPanel.add(validationLabel, gbc)

        add(formPanel, BorderLayout.CENTER)

        // Help tooltips
        displayNameField.toolTipText = "The name that will appear in-game (spaces allowed)"
        modNameField.toolTipText = "Technical name for files and folders (no spaces, underscores allowed)"

        directoryButton.addActionListener { chooseDirectory() }

        // Auto-generate technical name from display name
        displayNameField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = updateTechnicalName()
            override fun removeUpdate(e: DocumentEvent) = updateTechnicalName()
            override fun changedUpdate(e: DocumentEvent) = updateTechnicalName()
        })
    }

    private fun updateTechnicalName() {
        if (!modNameField.hasFocus()) {
            modNameField.text = ModOutputConfigManager.sanitizeModName(displayNameField.text)
        }
    }

    private fun setupInitialState() {
        directoryField.text = configManager.getDefaultDirectory().absolutePath
        val defaultConfig = configManager.createDefaultConfig()
        displayNameField.text = defaultConfig.displayName
        modNameField.text = defaultConfig.modName
        validateInput()
    }

    private fun validateInput() {
        val (valid, errors) = configManager.validateConfig(
            modName = modNameField.text,
            displayName = displayNameField.text,
            directory = File(directoryField.text)
        )

        if (valid) {
            validationLabel.text = ""
            isValid = true
            updatePreview()
        } else {
            validationLabel.text = "⚠ ${errors.first()}"
            validationLabel.foreground = Color(150, 0, 0)
            isValid = false
            previewLabel.text = ""
        }
    }

    private fun updatePreview() {
        val previewPath = configManager.generatePreviewPath(
            modName = modNameField.text,
            directory = File(directoryField.text)
        )
        previewLabel.text = "<html>Output will be created at:<br><font color='gray'>$previewPath</font></html>"
    }

    fun getConfiguration(): ModOutputConfig? {
        if (!isValid) return null

        return ModOutputConfig.Builder(modNameField.text, configManager.gamePathsManager)
            .setDisplayName(displayNameField.text)
            .setDirectory(File(directoryField.text))
            .build()
    }

    private fun setupValidation() {
        val documentListener = object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = validateInput()
            override fun removeUpdate(e: DocumentEvent) = validateInput()
            override fun changedUpdate(e: DocumentEvent) = validateInput()
        }

        displayNameField.document.addDocumentListener(documentListener)
        modNameField.document.addDocumentListener(documentListener)
        directoryField.document.addDocumentListener(documentListener)
    }

    private fun chooseDirectory() {
        val fileChooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "Select Output Directory"
            currentDirectory = File(directoryField.text)
        }

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            directoryField.text = fileChooser.selectedFile.absolutePath
            validateInput()
        }
    }

    fun addValidationListener(listener: (Boolean) -> Unit) {
        validationListeners.add(listener)
    }

    private fun notifyValidationListeners(isValid: Boolean) {
        validationListeners.forEach { it(isValid) }
    }
}