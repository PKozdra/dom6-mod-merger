package com.dominions.modmerger.ui.components

import com.dominions.modmerger.domain.ModOutputConfig
import com.dominions.modmerger.infrastructure.FileSystem
import com.dominions.modmerger.infrastructure.GamePathsManager
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import java.awt.Color
import java.io.File
import mu.KotlinLogging

class ModOutputConfigPanel(
    private val fileSystem: FileSystem,
    private val gamePathsManager: GamePathsManager
) : JPanel() {
    private val logger = KotlinLogging.logger {}

    // UI Components
    private val modNameField = JTextField(20)
    private val versionField = JTextField("1.0", 5)
    private val descriptionArea = JTextArea(3, 20)
    private val directoryField = JTextField(30)
    private val directoryButton = JButton("Browse...")
    private val previewLabel = JLabel()
    private val validationLabel = JLabel()

    // Validation state
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

        // Create main form panel
        val formPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = Insets(5, 5, 5, 5)
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
        }

        // Mod Name
        gbc.apply {
            gridx = 0; gridy = 0
            gridwidth = 1
        }
        formPanel.add(JLabel("Mod Name:"), gbc)

        gbc.apply {
            gridx = 1
            gridwidth = 2
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
        }
        formPanel.add(modNameField, gbc)

        // Version
        gbc.apply {
            gridx = 0; gridy = 1
            gridwidth = 1
            weightx = 0.0
        }
        formPanel.add(JLabel("Version:"), gbc)

        gbc.apply {
            gridx = 1
            gridwidth = 2
        }
        formPanel.add(versionField, gbc)

        // Description
        gbc.apply {
            gridx = 0; gridy = 2
            gridwidth = 1
        }
        formPanel.add(JLabel("Description:"), gbc)

        gbc.apply {
            gridx = 1
            gridwidth = 2
        }
        descriptionArea.apply {
            lineWrap = true
            wrapStyleWord = true
        }
        formPanel.add(JScrollPane(descriptionArea), gbc)

        // Directory
        gbc.apply {
            gridx = 0; gridy = 3
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
            gridx = 0; gridy = 4
            gridwidth = 3
        }
        formPanel.add(previewLabel, gbc)

        // Validation
        gbc.apply {
            gridx = 0; gridy = 5
            gridwidth = 3
        }
        formPanel.add(validationLabel, gbc)

        // Add form to panel
        add(formPanel, BorderLayout.CENTER)

        // Setup directory button action
        directoryButton.addActionListener {
            chooseDirectory()
        }
    }

    private fun setupValidation() {
        val documentListener = object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = validateInput()
            override fun removeUpdate(e: DocumentEvent) = validateInput()
            override fun changedUpdate(e: DocumentEvent) = validateInput()
        }

        modNameField.document.addDocumentListener(documentListener)
        versionField.document.addDocumentListener(documentListener)
        directoryField.document.addDocumentListener(documentListener)
    }

    private fun setupInitialState() {
        // Set default directory to local mods path
        directoryField.text = gamePathsManager.getLocalModPath().absolutePath
        validateInput()
    }

    private fun validateInput() {
        val errors = mutableListOf<String>()

        // Validate mod name
        modNameField.text.let { name ->
            val (valid, error) = fileSystem.validateModName(name)
            if (!valid) {
                errors.add(error ?: "Invalid mod name")
            }
        }

        // Validate version
        if (!versionField.text.matches(Regex("^\\d+\\.\\d+$"))) {
            errors.add("Version must be in format X.YY")
        }

        // Validate directory
        val directory = File(directoryField.text)
        if (!directory.exists() && !directory.canWrite()) {
            errors.add("Selected directory is not writable")
        }

        // Update UI
        if (errors.isEmpty()) {
            validationLabel.text = "✓ Configuration is valid"
            validationLabel.foreground = Color(0, 150, 0)
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
        val modName = modNameField.text
        val directory = File(directoryField.text)
        val expectedPath = File(directory, "$modName/$modName.dm").absolutePath
        previewLabel.text = "<html>Output will be created at:<br><font color='gray'>$expectedPath</font></html>"
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

    fun getConfiguration(): ModOutputConfig? {
        if (!isValid) return null

        return ModOutputConfig.Builder(modNameField.text, gamePathsManager)
            .setDirectory(File(directoryField.text))
            .setDescription(descriptionArea.text)
            .setVersion(versionField.text)
            .build()
    }

    fun addValidationListener(listener: (Boolean) -> Unit) {
        validationListeners.add(listener)
    }

    private fun notifyValidationListeners(isValid: Boolean) {
        validationListeners.forEach { it(isValid) }
    }
}