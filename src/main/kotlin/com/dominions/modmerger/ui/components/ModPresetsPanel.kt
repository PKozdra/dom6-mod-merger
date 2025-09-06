package com.dominions.modmerger.ui.components

import com.dominions.modmerger.domain.ModPreset
import com.dominions.modmerger.infrastructure.Logging
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.*
import javax.swing.border.EmptyBorder

class ModPresetsPanel : JPanel(), Logging {
    private val presetComboBox = JComboBox<String>()
    private val saveButton = JButton("Save")
    private val loadButton = JButton("Load")
    private val deleteButton = JButton("Delete")

    private var savePresetListener: ((String, Boolean) -> Unit)? = null
    private var loadPresetListener: ((String) -> Unit)? = null
    private var deletePresetListener: ((String) -> Unit)? = null

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm")

    init {
        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        layout = FlowLayout(FlowLayout.LEFT, 5, 0)
        border = EmptyBorder(0, 0, 0, 5)

        add(JLabel("Presets:"))

        presetComboBox.apply {
            preferredSize = Dimension(180, 25)
            isEditable = false
            maximumRowCount = 12
        }
        add(presetComboBox)

        saveButton.preferredSize = Dimension(60, 25)
        loadButton.preferredSize = Dimension(60, 25)
        deleteButton.preferredSize = Dimension(70, 25)

        add(saveButton)
        add(loadButton)
        add(deleteButton)

        // Initially disable buttons if no presets
        updateButtonStates()
    }

    private fun setupListeners() {
        saveButton.addActionListener {
            showSavePresetDialog()
        }

        loadButton.addActionListener {
            val selectedPreset = presetComboBox.selectedItem as? String ?: return@addActionListener
            loadPresetListener?.invoke(selectedPreset)
        }

        deleteButton.addActionListener {
            val selectedPreset = presetComboBox.selectedItem as? String ?: return@addActionListener
            showDeleteConfirmation(selectedPreset)
        }

        presetComboBox.addActionListener {
            updateButtonStates()
        }
    }

    fun updatePresets(presets: List<ModPreset>) {
        presetComboBox.removeAllItems()

        presets.forEach { preset ->
            presetComboBox.addItem(preset.name)
        }

        updateButtonStates()
    }

    private fun updateButtonStates() {
        val hasSelection = presetComboBox.selectedItem != null
        loadButton.isEnabled = hasSelection
        deleteButton.isEnabled = hasSelection
    }

    private fun showSavePresetDialog() {
        val panel = JPanel(BorderLayout())
        val nameField = JTextField(20)
        val overwriteCheckbox = JCheckBox("Overwrite if exists")

        panel.add(JLabel("Preset Name:"), BorderLayout.NORTH)
        panel.add(nameField, BorderLayout.CENTER)
        panel.add(overwriteCheckbox, BorderLayout.SOUTH)

        // Pre-fill with selected preset if any
        val selectedPreset = presetComboBox.selectedItem as? String
        if (selectedPreset != null) {
            nameField.text = selectedPreset
            overwriteCheckbox.isSelected = true
        }

        val result = JOptionPane.showConfirmDialog(
            this,
            panel,
            "Save Mod Selection Preset",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        )

        if (result == JOptionPane.OK_OPTION) {
            val name = nameField.text.trim()
            if (name.isNotEmpty()) {
                savePresetListener?.invoke(name, overwriteCheckbox.isSelected)
            } else {
                JOptionPane.showMessageDialog(
                    this,
                    "Please enter a valid preset name",
                    "Invalid Name",
                    JOptionPane.WARNING_MESSAGE
                )
            }
        }
    }

    private fun showDeleteConfirmation(presetName: String) {
        val result = JOptionPane.showConfirmDialog(
            this,
            "Are you sure you want to delete the preset '$presetName'?",
            "Delete Preset",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        )

        if (result == JOptionPane.YES_OPTION) {
            deletePresetListener?.invoke(presetName)
        }
    }

    fun setOnSavePreset(listener: (name: String, overwrite: Boolean) -> Unit) {
        savePresetListener = listener
    }

    fun setOnLoadPreset(listener: (name: String) -> Unit) {
        loadPresetListener = listener
    }

    fun setOnDeletePreset(listener: (name: String) -> Unit) {
        deletePresetListener = listener
    }

    fun selectPreset(name: String) {
        for (i in 0 until presetComboBox.itemCount) {
            if (presetComboBox.getItemAt(i) == name) {
                presetComboBox.selectedIndex = i
                break
            }
        }
    }
}