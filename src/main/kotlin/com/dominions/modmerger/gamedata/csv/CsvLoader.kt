// src/main/kotlin/com/dominions/modmerger/gamedata/csv/CsvLoader.kt
package com.dominions.modmerger.gamedata.csv

import com.dominions.modmerger.infrastructure.Logging

class CsvLoader(
) : Logging {
    suspend fun loadCsvFromString(content: String): CsvData {
        debug("Processing CSV content")
        val lines = content.lines()
            .filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            throw CsvLoadException("CSV content is empty")
        }

        val header = parseHeader(lines.first())
        val rows = lines.drop(1).map { parseLine(it) }

        return CsvData(header, rows)
    }

    private fun parseHeader(headerLine: String): List<String> =
        headerLine.split("\t").map { it.trim() }

    private fun parseLine(line: String): List<String> =
        line.split("\t").map { it.trim() }
}

data class CsvData(
    val header: List<String>,
    val rows: List<List<String>>
) {
    fun getColumnIndex(name: String): Int =
        header.indexOf(name).also {
            require(it >= 0) { "Required column not found: $name" }
        }
}

class CsvLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)