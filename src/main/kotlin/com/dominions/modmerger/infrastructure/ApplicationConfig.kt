package com.dominions.modmerger.infrastructure

import com.dominions.modmerger.generated.BuildInfo
import java.awt.Image
import javax.swing.ImageIcon

object ApplicationConfig {

    const val APP_NAME = "Dominions 6 Mod Merger"
    val APP_VERSION = BuildInfo.VERSION

    // Windows takes 16 and 32 pixels for a window, and the larger ones on a scaled display
    fun windowIcons(): List<Image> = listOf(16, 24, 32, 48, 64)
        .mapNotNull { size -> javaClass.classLoader.getResource("icons/app_$size.png") }
        .map { ImageIcon(it).image }
}
