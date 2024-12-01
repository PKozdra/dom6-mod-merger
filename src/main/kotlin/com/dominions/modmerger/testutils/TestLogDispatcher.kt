// src/test/kotlin/com/dominions/modmerger/testutils/TestLogDispatcher.kt
package com.dominions.modmerger.testutils

import com.dominions.modmerger.domain.LogDispatcher
import com.dominions.modmerger.domain.LogLevel

class TestLogDispatcher : LogDispatcher() {
    private val logs = mutableListOf<LogMessage>()

    override fun log(level: LogLevel, message: String) {
        logs.add(LogMessage(level, message))
        super.log(level, message)
    }

    fun getLogs(): List<LogMessage> = logs.toList()

    fun clear() {
        logs.clear()
    }

    data class LogMessage(
        val level: LogLevel,
        val message: String
    )
}