// src/main/kotlin/com/dominions/modmerger/Main.kt
package com.dominions.modmerger

import com.dominions.modmerger.infrastructure.FontConfigurationManager
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val fontManager = FontConfigurationManager()
    val setupResult = fontManager.setupFontConfiguration()

    if (!setupResult.success) {
        exitProcess(1)
    }

    ModMergerApplication.main(args)
}