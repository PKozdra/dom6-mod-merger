// src/main/kotlin/com/dominions/modmerger/ui/ModMergerGui.kt
package com.dominions.modmerger.ui

import com.dominions.modmerger.core.ModMergerService
import com.dominions.modmerger.domain.LogDispatcher
import com.dominions.modmerger.domain.LogLevel
import com.dominions.modmerger.domain.LogListener
import com.dominions.modmerger.domain.ModFile
import com.dominions.modmerger.infrastructure.FileSystem
import com.dominions.modmerger.ui.components.ModTablePanel
import com.dominions.modmerger.ui.model.ModListItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.awt.*
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import javax.swing.*
import javax.swing.border.EmptyBorder

@OptIn(kotlin.io.path.ExperimentalPathApi::class)
class ModMergerGui(
    private val modMergerService: ModMergerService,
    private val fileSystem: FileSystem,
    private val logDispatcher: LogDispatcher
) : LogListener {
    private val logger = KotlinLogging.logger {}
    private val frame = JFrame("Dominions 6 Mod Merger")
    private val modTable = ModTablePanel()
    private val outputArea = JTextArea()
    private val mergeButton = JButton("Merge Selected Mods")
    private val customPathField = JTextField()
    private val refreshButton = JButton("Refresh")
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    init {
        logDispatcher.addListener(this)
        setupWindow()
        setupModTable()
        setupControls()
        setupOutputArea()
        loadMods()
    }

    override fun onLogMessage(level: LogLevel, message: String) {
        SwingUtilities.invokeLater {
            val formattedMessage = "[${level.name}] $message"
            outputArea.append("$formattedMessage\n")
            outputArea.caretPosition = outputArea.document.length
        }
    }

    private fun setupWindow() {
        frame.apply {
            defaultCloseOperation = JFrame.EXIT_ON_CLOSE
            minimumSize = Dimension(1024, 768)
            setLocationRelativeTo(null)

            addWindowListener(object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent) {
                    dispose()
                }
            })
        }
    }

    private fun setupModTable() {
        val scrollPane = JScrollPane(modTable).apply {
            border = BorderFactory.createTitledBorder("Available Mods")
        }
        frame.add(scrollPane, BorderLayout.CENTER)
    }

    private fun setupControls() {
        val controlPanel = JPanel(BorderLayout()).apply {
            border = EmptyBorder(10, 10, 10, 10)
        }

        val buttonPanel = JPanel(FlowLayout(FlowLayout.CENTER)).apply {
            add(mergeButton)
        }

        controlPanel.add(buttonPanel, BorderLayout.NORTH)

        frame.add(controlPanel, BorderLayout.NORTH)

        setupButtonActions()
    }

    private fun setupOutputArea() {
        outputArea.apply {
            isEditable = false
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            border = BorderFactory.createTitledBorder("Output")
        }

        val scrollPane = JScrollPane(outputArea)
        frame.add(scrollPane, BorderLayout.SOUTH)
    }

    private fun setupButtonActions() {
        mergeButton.addActionListener {
            val selectedMods = modTable.getSelectedMods().map { it.modFile }

            if (selectedMods.isEmpty()) {
                JOptionPane.showMessageDialog(
                    frame,
                    "Please select at least one mod to merge",
                    "No Mods Selected",
                    JOptionPane.WARNING_MESSAGE
                )
                return@addActionListener
            }

            mergeMods(selectedMods)
        }

        refreshButton.addActionListener {
            loadMods()
        }
    }

    private fun loadMods() {
        outputArea.text = ""

        val steamPath = getSteamModPath()
        val customPath = customPathField.text.takeIf { it.isNotBlank() }?.let { File(it) }

        val paths = buildList {
            if (steamPath != null) {
                add(steamPath)
                log("Found Steam workshop path: $steamPath")
            }
            if (customPath != null) {
                add(customPath)
                log("Using custom path: $customPath")
            }
        }

        if (paths.isEmpty()) {
            log("No valid mod paths found. Please specify a custom path.")
            return
        }

        val modItems = mutableListOf<ModListItem>()
        var totalMods = 0

        paths.forEach { path ->
            try {
                val mods = findModFiles(path)
                mods.forEach { modFile ->
                    modItems.add(ModListItem(modFile))
                    totalMods++
                }
            } catch (e: Exception) {
                logger.error(e) { "Error loading mods from $path" }
                log("Error loading mods from $path: ${e.message}")
            }
        }

        modTable.updateMods(modItems)
        log("Found $totalMods total mods")
    }

    private fun getSteamModPath(): File? {
        val steamPath = when {
            System.getProperty("os.name").contains("Windows") ->
                File("""C:\Program Files (x86)\Steam\steamapps\workshop\content\2511500""")

            System.getProperty("os.name").contains("Mac") ->
                File(
                    System.getProperty("user.home"),
                    "Library/Application Support/Steam/steamapps/workshop/content/2511500"
                )

            else ->
                File(System.getProperty("user.home"), ".steam/steam/steamapps/workshop/content/2511500")
        }

        return if (steamPath.exists()) steamPath else null
    }

    private fun findModFiles(path: File): List<ModFile> {
        return path.walkTopDown()
            .filter { it.isFile && it.extension == "dm" }
            .map { ModFile.fromFile(it) }
            .toList()
    }

    private fun mergeMods(mods: List<ModFile>) {
        mergeButton.isEnabled = false
        log("Starting merge of ${mods.size} mods...")

        coroutineScope.launch {
            try {
                log("Processing mods: ${mods.joinToString { it.name }}")
                val result = modMergerService.processMods(mods)
                SwingUtilities.invokeLater {
                    when (result) {
                        is com.dominions.modmerger.MergeResult.Success -> {
                            log("Merge completed successfully!")
                            if (result.warnings.isNotEmpty()) {
                                log("Warnings encountered during merge:")
                                result.warnings.forEach { warning ->
                                    log("- $warning")
                                }
                            }
                            // Log where the merged mod was saved
                            log("Merged mod saved to: ${fileSystem.getOutputFile().absolutePath}")
                        }

                        is com.dominions.modmerger.MergeResult.Failure -> {
                            log("Merge failed: ${result.error}")
                        }
                    }
                    mergeButton.isEnabled = true
                }
            } catch (e: Exception) {
                logger.error(e) { "Error during merge" }
                SwingUtilities.invokeLater {
                    log("Error during merge: ${e.message}")
                    mergeButton.isEnabled = true
                }
            }
        }
    }

    private fun log(message: String) {
        SwingUtilities.invokeLater {
            outputArea.append("$message\n")
            outputArea.caretPosition = outputArea.document.length
        }
    }

    fun show() {
        frame.pack()
        frame.isVisible = true
    }

    private fun dispose() {
        frame.dispose()
    }
}