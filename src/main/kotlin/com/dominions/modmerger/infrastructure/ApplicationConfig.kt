package com.dominions.modmerger.infrastructure

import java.awt.Image
import javax.swing.ImageIcon

object ApplicationConfig : Logging {

    const val APP_NAME = "Dominions 6 Mod Merger"
    const val APP_VERSION = "0.0.6"
    private const val APP_ICON_PATH = "icon.ico"

    /**
     * Gets the application icon, scaling it if dimensions are provided
     */
    fun getApplicationIcon(width: Int? = null, height: Int? = null): ImageIcon? {
        return try {
            val resource = ApplicationConfig::class.java.classLoader.getResource(APP_ICON_PATH)
                ?: throw IllegalStateException("Application icon not found")

            val icon = ImageIcon(resource)

            if (width != null && height != null) {
                val scaled = icon.image.getScaledInstance(width, height, Image.SCALE_SMOOTH)
                ImageIcon(scaled)
            } else {
                icon
            }
        } catch (e: Exception) {
            error("Failed to load application icon: ${e.message}", useDispatcher = false)
            null
        }
    }
}