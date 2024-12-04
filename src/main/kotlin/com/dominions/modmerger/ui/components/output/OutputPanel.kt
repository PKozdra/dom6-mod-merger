package com.dominions.modmerger.ui.components.output


import com.dominions.modmerger.infrastructure.*
import com.dominions.modmerger.ui.util.NoWrapEditorKit
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.LinkedBlockingQueue
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.text.BadLocationException
import javax.swing.text.StyleConstants
import javax.swing.text.StyledEditorKit

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
    internal var maxLogBufferSize = 1000  // Maximum number of log messages to keep

    internal val activeLogLevels = mutableSetOf<LogLevel>()

    internal val outputPane = JTextPane()
    private val scrollPane = JScrollPane(outputPane)
    private val controlPanel = ControlPanel(this)
    private val logLevelPanel = LogLevelPanel(this) // Depends on activeLogLevels
    internal val searchPanel = SearchPanel(this)

    private var isWordWrapEnabled = true
    private val logWorker = LogWorker()
    internal var pauseSearchUpdates = false  // Added to control search updates

    init {
        setupPanel()
        setupOutputPane()
        setupScrollPane()
        setupLogLevels()
        setupControlPanel()
        setupKeyBindings()
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
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            editorKit = if (isWordWrapEnabled) StyledEditorKit() else NoWrapEditorKit()
        }
    }

    private fun setupScrollPane() {
        scrollPane.apply {
            border = BorderFactory.createTitledBorder("Output")
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
        }
        val layeredPane = JPanel(BorderLayout())
        layeredPane.add(scrollPane, BorderLayout.CENTER)
        layeredPane.add(searchPanel, BorderLayout.NORTH)
        add(layeredPane, BorderLayout.CENTER)
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

                outputPane.caretPosition = doc.length

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
