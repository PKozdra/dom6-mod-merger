package com.dominions.modmerger.ui.components.output

import java.awt.Color
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.util.*
import java.util.Timer
import java.util.regex.Pattern
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.BadLocationException
import javax.swing.text.DefaultHighlighter

class SearchPanel(private val outputPanel: OutputPanel) : JPanel() {

    private val searchField = JTextField(20)
    private val caseSensitiveCheckbox = JCheckBox("Case Sensitive")
    private val wholeWordCheckbox = JCheckBox("Whole Word")
    private val resultLabel = JLabel("0 / 0")
    private val prevButton = JButton("↑")
    private val nextButton = JButton("↓")
    private val closeButton = JButton("✕")
    private var searchPositions = mutableListOf<Int>()
    private var currentIndex = -1
    private var searchTimer: Timer? = null
    private var documentChangeListener: DocumentListener? = null

    init {
        layout = FlowLayout(FlowLayout.RIGHT, 5, 5)
        isVisible = false
        background = UIManager.getColor("Panel.background")
        addComponents()
        setupListeners()
    }

    private fun addComponents() {
        add(searchField)
        add(caseSensitiveCheckbox)
        add(wholeWordCheckbox)
        add(prevButton)
        add(nextButton)
        add(resultLabel)
        add(closeButton)
    }

    private fun setupListeners() {
        val updateSearch = { performSearchWithDebounce() }
        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = updateSearch()
            override fun removeUpdate(e: DocumentEvent?) = updateSearch()
            override fun changedUpdate(e: DocumentEvent?) = updateSearch()
        })

        caseSensitiveCheckbox.addActionListener { performSearch() }
        wholeWordCheckbox.addActionListener { performSearch() }

        prevButton.addActionListener { navigateToPrevious() }
        nextButton.addActionListener { navigateToNext() }
        closeButton.addActionListener { closeSearchBar() }

        val enterKey = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)
        searchField.inputMap.put(enterKey, "enterAction")
        searchField.actionMap.put("enterAction", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                navigateToNext()
            }
        })
    }

    fun showSearchBar() {
        if (!isVisible) {
            isVisible = true
            searchField.requestFocusInWindow()
            addDocumentListener()
            performSearch()
        } else {
            searchField.requestFocusInWindow()
        }
    }

    private fun closeSearchBar() {
        isVisible = false
        clearHighlights()
        removeDocumentListener()
    }

    fun documentUpdated() {
        if (isVisible) {
            performSearchWithDebounce()
        }
    }

    private fun performSearchWithDebounce() {
        searchTimer?.cancel()
        searchTimer = Timer()
        searchTimer?.schedule(object : TimerTask() {
            override fun run() {
                SwingUtilities.invokeLater { performSearch() }
            }
        }, 300)
    }

    private fun performSearch() {
        clearHighlights()
        val query = searchField.text
        if (query.isEmpty()) {
            updateResultLabel(0, 0)
            return
        }

        val text = outputPanel.outputPane.document.getText(0, outputPanel.outputPane.document.length)
        val flags = if (caseSensitiveCheckbox.isSelected) 0 else Pattern.CASE_INSENSITIVE
        val patternString = if (wholeWordCheckbox.isSelected) {
            "\\b${Pattern.quote(query)}\\b"
        } else {
            Pattern.quote(query)
        }
        val pattern = Pattern.compile(patternString, flags)
        val matcher = pattern.matcher(text)
        searchPositions.clear()
        while (matcher.find()) {
            searchPositions.add(matcher.start())
            highlightText(matcher.start(), matcher.end())
        }
        updateResultLabel(0, searchPositions.size)
        if (searchPositions.isNotEmpty()) {
            currentIndex = 0
            scrollToPosition(searchPositions[currentIndex])
            updateResultLabel(currentIndex + 1, searchPositions.size)
        } else {
            currentIndex = -1
        }
    }

    private fun highlightText(start: Int, end: Int) {
        val painter = DefaultHighlighter.DefaultHighlightPainter(Color.YELLOW)
        outputPanel.outputPane.highlighter.addHighlight(start, end, painter)
    }

    private fun clearHighlights() {
        outputPanel.outputPane.highlighter.removeAllHighlights()
        searchPositions.clear()
        currentIndex = -1
        updateResultLabel(0, 0)
    }

    private fun scrollToPosition(position: Int) {
        outputPanel.outputPane.caretPosition = position
        try {
            val rect = outputPanel.outputPane.modelToView2D(position)
            if (rect != null) {
                outputPanel.outputPane.scrollRectToVisible(rect.bounds)
            }
        } catch (e: BadLocationException) {
            e.printStackTrace()
        }
    }

    private fun navigateToNext() {
        if (searchPositions.isEmpty()) return
        currentIndex = (currentIndex + 1) % searchPositions.size
        scrollToPosition(searchPositions[currentIndex])
        updateResultLabel(currentIndex + 1, searchPositions.size)
    }

    private fun navigateToPrevious() {
        if (searchPositions.isEmpty()) return
        currentIndex = if (currentIndex - 1 < 0) searchPositions.size - 1 else currentIndex - 1
        scrollToPosition(searchPositions[currentIndex])
        updateResultLabel(currentIndex + 1, searchPositions.size)
    }

    private fun updateResultLabel(current: Int, total: Int) {
        resultLabel.text = "$current / $total"
    }

    private fun addDocumentListener() {
        if (documentChangeListener == null) {
            documentChangeListener = object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = documentUpdated()
                override fun removeUpdate(e: DocumentEvent?) = documentUpdated()
                override fun changedUpdate(e: DocumentEvent?) = documentUpdated()
            }
            outputPanel.outputPane.document.addDocumentListener(documentChangeListener)
        }
    }

    private fun removeDocumentListener() {
        if (documentChangeListener != null) {
            outputPanel.outputPane.document.removeDocumentListener(documentChangeListener)
            documentChangeListener = null
        }
    }
}
