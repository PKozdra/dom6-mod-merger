// src/main/kotlin/com/dominions/modmerger/gamedata/Dom5CsvGameDataProvider.kt
package com.dominions.modmerger.gamedata

class Dom5CsvGameDataProvider(
) : AbstractCsvGameDataProvider() {
    override fun getResourcePath(): String = "gamedata/dom5"
}