package com.dominions.modmerger.domain

open class LogDispatcher {
    private val listeners = mutableListOf<LogListener>()

    fun addListener(listener: LogListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: LogListener) {
        listeners.remove(listener)
    }

    open fun log(level: LogLevel, message: String) {
        listeners.forEach { it.onLogMessage(level, message) }
    }
}
