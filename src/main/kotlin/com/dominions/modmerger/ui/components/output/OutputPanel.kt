package com.dominions.modmerger.ui.components.output


import com.dominions.modmerger.infrastructure.*
import com.dominions.modmerger.ui.util.NoWrapEditorKit
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.TimerTask
import java.util.concurrent.LinkedBlockingQueue
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.text.BadLocationException
import javax.swing.text.StyleConstants
import javax.swing.text.StyledEditorKit
import kotlin.concurrent.schedule

/**
 * A panel that displays log output with configurable log levels and text display options.
 * Features include:
 * - Filterable log levels with clear visual indicators
 * - Word wrap toggle
 * - Color-coded log messages
 * - Log export functionality
 * - Search functionality with highlights and navigation
 */
class OutputPanel(
    private val logDispatcher: LogDispatcher = GlobalLogDispatcher
) : JPanel(BorderLayout()), LogListener, Logging {

    internal val logQueue = LinkedBlockingQueue<Pair<String, LogLevel>>()
    internal val logBuffer = ArrayDeque<Pair<String, LogLevel>>()
    internal var maxLogBufferSize = 2500  // Maximum number of log messages to keep

    internal val activeLogLevels = mutableSetOf<LogLevel>()

    internal val outputPane = JTextPane()
    private val scrollPane = JScrollPane(outputPane)
    private val controlPanel = ControlPanel(this)
    private val logLevelPanel = LogLevelPanel(this) // Depends on activeLogLevels
    internal val searchPanel = SearchPanel(this)

    private var isWordWrapEnabled = true
    private val logWorker = LogWorker()
    internal var pauseSearchUpdates = false  // Added to control search updates

    private var currentFontSize = PreferencesManager.fontSize

    private var shouldAutoScroll = true

    init {
        setupPanel()
        setupOutputPane()
        setupScrollPane()
        setupLogLevels()
        setupControlPanel()
        setupKeyBindings()
        setupFontSizeControls()
        logDispatcher.addListener(this)
        logWorker.execute()

        // Add initial logging
        debug("OutputPanel initialized", useDispatcher = false)
    }

    private fun setupPanel() {
        border = EmptyBorder(10, 10, 10, 10)
        preferredSize = Dimension(800, 300)
    }

    private fun setupOutputPane() {
        outputPane.apply {
            isEditable = false
            font = Font(Font.MONOSPACED, Font.PLAIN, currentFontSize)
            editorKit = if (isWordWrapEnabled) StyledEditorKit() else NoWrapEditorKit()

            // Enable drag selection even though pane is not editable
            enableInputMethods(true)

            // Add context menu
            componentPopupMenu = createContextMenu()
        }
    }

    private fun setupScrollPane() {
        scrollPane.apply {
            border = BorderFactory.createTitledBorder("Output")
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS

            // Track scrolling to determine auto-scroll behavior
            verticalScrollBar.addAdjustmentListener { e ->
                val extent = verticalScrollBar.model.extent
                val maximum = verticalScrollBar.maximum
                val value = e.value
                shouldAutoScroll = (value + extent) >= maximum
            }
        }

        val layeredPane = JPanel(BorderLayout())
        layeredPane.add(scrollPane, BorderLayout.CENTER)
        layeredPane.add(searchPanel, BorderLayout.NORTH)
        add(layeredPane, BorderLayout.CENTER)
    }

    private fun createContextMenu() = JPopupMenu().apply {
        add(JMenuItem("Copy").apply {
            accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_C, Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx)
            addActionListener {
                outputPane.copy()
            }
        })

        add(JMenuItem("Select All").apply {
            accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_A, Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx)
            addActionListener {
                outputPane.selectAll()
            }
        })

        add(JMenuItem("Search").apply {
            accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_F, Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx)
            // if not open, show search bar, otherwise close it
            addActionListener {
                searchPanel.toggleSearchBar()
            }
        })
    }

    private fun setupLogLevels() {
        add(logLevelPanel, BorderLayout.NORTH)
    }

    private fun setupControlPanel() {
        add(controlPanel, BorderLayout.SOUTH)
    }

    private fun setupKeyBindings() {
        val ctrlFStroke = KeyStroke.getKeyStroke(KeyEvent.VK_F, Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx)
        outputPane.inputMap.put(ctrlFStroke, "showSearchBar")
        outputPane.actionMap.put("showSearchBar", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                searchPanel.showSearchBar()
            }
        })
    }

    internal fun updateWordWrap(enabled: Boolean) {
        isWordWrapEnabled = enabled

        // Save current view position
        val viewRect = scrollPane.viewport.viewRect
        val caretPosition = outputPane.caretPosition

        // Update editor kit
        outputPane.editorKit = if (enabled) {
            StyledEditorKit()
        } else {
            NoWrapEditorKit()
        }

        // Reapply content by redrawing from buffer
        try {
            val doc = outputPane.styledDocument
            doc.remove(0, doc.length)

            for ((message, level) in logBuffer) {
                val style = outputPane.addStyle(level.name, null)
                StyleConstants.setForeground(style, getColorForLogLevel(level))
                doc.insertString(doc.length, message, style)
            }

            // Restore view position
            SwingUtilities.invokeLater {
                outputPane.caretPosition = caretPosition
                scrollPane.viewport.viewPosition = viewRect.location
            }
        } catch (e: BadLocationException) {
            e.printStackTrace()
        }

        outputPane.revalidate()
    }

    private fun setupFontSizeControls() {
        // Add mouse wheel listener
        scrollPane.addMouseWheelListener { e ->
            if (e.isControlDown) {
                val increment = if (e.wheelRotation < 0) 1 else -1
                val newSize = (currentFontSize + increment).coerceIn(
                    PreferencesManager.MIN_FONT_SIZE,
                    PreferencesManager.MAX_FONT_SIZE
                )

                if (newSize != currentFontSize) {
                    // Immediate UI update
                    currentFontSize = newSize
                    outputPane.font = Font(Font.MONOSPACED, Font.PLAIN, newSize)
                    controlPanel.updateFontSizeStatus(newSize)

                    // Debounced preference update using SwingUtilities
                    SwingUtilities.invokeLater {
                        fontSizeUpdateTimer?.stop()
                        fontSizeUpdateTimer = javax.swing.Timer(500) {
                            PreferencesManager.fontSize = newSize
                            fontSizeUpdateTimer?.stop()
                        }.apply {
                            isRepeats = false
                            start()
                        }
                    }
                }

                e.consume()
            } else {
                // Forward the event to the default scroll pane UI handler
                scrollPane.parent.dispatchEvent(e)
            }
        }

        // Add keyboard shortcuts
        val inputMap = outputPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        val actionMap = outputPane.actionMap

        // Ctrl + Plus
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx), "increaseFontSize")
        actionMap.put("increaseFontSize", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                updateFontSize(currentFontSize + 1)
            }
        })

        // Ctrl + Minus
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx), "decreaseFontSize")
        actionMap.put("decreaseFontSize", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                updateFontSize(currentFontSize - 1)
            }
        })

        // Ctrl + 0 (Reset to default)
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_0, Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx), "resetFontSize")
        actionMap.put("resetFontSize", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                updateFontSize(PreferencesManager.DEFAULT_FONT_SIZE)
            }
        })
    }

    private var fontSizeUpdateTimer: javax.swing.Timer? = null

    private fun updateFontSize(newSize: Int) {
        val size = newSize.coerceIn(PreferencesManager.MIN_FONT_SIZE, PreferencesManager.MAX_FONT_SIZE)
        if (size != currentFontSize) {
            currentFontSize = size
            outputPane.font = Font(Font.MONOSPACED, Font.PLAIN, size)

            // Update word wrap to handle new font size
            updateWordWrap(isWordWrapEnabled)

            // Update the status text in ControlPanel
            controlPanel.updateFontSizeStatus(size)

            // Cancel previous timer if exists
            fontSizeUpdateTimer?.stop()

            // Create a new timer
            fontSizeUpdateTimer = javax.swing.Timer(500) {
                PreferencesManager.fontSize = size
                fontSizeUpdateTimer?.stop()
            }
            fontSizeUpdateTimer?.isRepeats = false
            fontSizeUpdateTimer?.start()
        }
    }


    private fun appendLogMessage(level: LogLevel, message: String) {
        if (level !in activeLogLevels) return
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        val formattedMessage = "[$timestamp][${level.name}] $message\n"
        logQueue.put(Pair(formattedMessage, level))
    }

    override fun onLogMessage(level: LogLevel, message: String) {
        appendLogMessage(level, message)
    }

    private inner class LogWorker : SwingWorker<Void, Pair<String, LogLevel>>() {

        override fun doInBackground(): Void? {
            while (!isCancelled) {
                try {
                    val logEntry = logQueue.take()
                    publish(logEntry)
                } catch (e: InterruptedException) {
                    break
                }
            }
            return null
        }

        override fun process(chunks: List<Pair<String, LogLevel>>) {
            for (chunk in chunks) {
                logBuffer.addLast(chunk)
                if (logBuffer.size > maxLogBufferSize) {
                    logBuffer.removeFirst()
                }
            }
            updateDocument()
        }

        private fun updateDocument() {
            val doc = outputPane.styledDocument
            try {
                // Clear the document
                doc.remove(0, doc.length)

                // Append log messages from the buffer
                for ((message, level) in logBuffer) {
                    val style = outputPane.addStyle(level.name, null)
                    StyleConstants.setForeground(style, getColorForLogLevel(level))
                    doc.insertString(doc.length, message, style)
                }

                // Only auto-scroll if we were at the bottom
                if (shouldAutoScroll) {
                    outputPane.caretPosition = doc.length
                }

                if (!pauseSearchUpdates) {
                    searchPanel.documentUpdated()
                }
            } catch (e: BadLocationException) {
                e.printStackTrace()
            }
        }
    }

    internal fun getColorForLogLevel(level: LogLevel): Color = when (level) {
        LogLevel.INFO -> Color.BLACK                  // Default text color
        LogLevel.WARN -> Color(255, 165, 0)           // Orange (#FFA500)
        LogLevel.ERROR -> Color(220, 20, 60)          // Crimson (#DC143C)
        LogLevel.DEBUG -> Color(105, 105, 105)        // Dark Gray (#696969)
        LogLevel.TRACE -> Color(112, 128, 144)        // Slate Gray (#708090)
    }
}
