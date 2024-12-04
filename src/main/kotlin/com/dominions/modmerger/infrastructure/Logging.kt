package com.dominions.modmerger.infrastructure

import org.slf4j.LoggerFactory

// Interface for log dispatching functionality
interface LogDispatcher {
    fun log(level: LogLevel, message: String)
    fun addListener(listener: LogListener)
    fun removeListener(listener: LogListener)
}

// Global singleton implementation
object GlobalLogDispatcher : LogDispatcher {
    private val listeners = mutableListOf<LogListener>()

    override fun log(level: LogLevel, message: String) {
        listeners.forEach { it.onLogMessage(level, message) }
    }

    override fun addListener(listener: LogListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: LogListener) {
        listeners.remove(listener)
    }

    fun reset() {
        listeners.clear()
    }
}

interface LogListener {
    fun onLogMessage(level: LogLevel, message: String)
}

enum class LogLevel {
    INFO, WARN, ERROR, DEBUG, TRACE
}

// Enhanced Logging interface
interface Logging {
    val logger: org.slf4j.Logger
        get() = LoggerFactory.getLogger(this::class.java)

    private val loggerFunctions: Map<LogLevel, (String) -> Unit>
        get() = mapOf(
            LogLevel.TRACE to logger::trace,
            LogLevel.DEBUG to logger::debug,
            LogLevel.INFO to logger::info,
            LogLevel.WARN to logger::warn,
            LogLevel.ERROR to logger::error
        )

    fun log(level: LogLevel, message: String, error: Throwable? = null, useDispatcher: Boolean = false) {
        if (useDispatcher) {
            GlobalLogDispatcher.log(level, message)
        }

        when {
            error != null && level == LogLevel.ERROR -> logger.error(message, error)
            else -> loggerFunctions[level]?.invoke(message)
        }
    }

    // Convenience methods with dispatcher flag
    fun debug(message: String, useDispatcher: Boolean = true) =
        log(LogLevel.DEBUG, message, useDispatcher = useDispatcher)

    fun info(message: String, useDispatcher: Boolean = true) =
        log(LogLevel.INFO, message, useDispatcher = useDispatcher)

    fun warn(message: String, useDispatcher: Boolean = true) =
        log(LogLevel.WARN, message, useDispatcher = useDispatcher)

    fun error(message: String, error: Throwable? = null, useDispatcher: Boolean = true) =
        log(LogLevel.ERROR, message, error, useDispatcher)

    fun trace(message: String, useDispatcher: Boolean = true) =
        log(LogLevel.TRACE, message, useDispatcher = useDispatcher)
}