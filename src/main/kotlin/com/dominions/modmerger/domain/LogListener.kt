package com.dominions.modmerger.domain

interface LogListener {
    fun onLogMessage(level: LogLevel, message: String)
}

enum class LogLevel {
    INFO, WARN, ERROR, DEBUG
}