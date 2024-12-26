package com.dominions.modmerger.ui.components

import com.dominions.modmerger.infrastructure.Logging
import java.awt.Color
import java.awt.image.BufferedImage
import javax.swing.Icon
import javax.swing.ImageIcon

class IconLoader {
    companion object : Logging {
        private const val DEFAULT_ICON_SIZE = 32

        fun loadIcon(
            resourcePath: String,
            iconSize: Int = DEFAULT_ICON_SIZE,
            fallbackColor: Color? = null,
            componentClass: Class<*> = ModTablePanel::class.java
        ): Icon? = loadIcon(resourcePath, iconSize, iconSize, fallbackColor, componentClass)

        fun loadIcon(
            resourcePath: String,
            width: Int,
            height: Int,
            fallbackColor: Color? = null,
            componentClass: Class<*> = ModTablePanel::class.java
        ): Icon? {
            return try {
                val resource = componentClass.getResource(resourcePath)
                if (resource != null && resourcePath.endsWith(".png", ignoreCase = true)) {
                    val image = ImageIcon(resource).image
                    val scaledImage = image.getScaledInstance(width, height, java.awt.Image.SCALE_SMOOTH)
                    ImageIcon(scaledImage)
                } else {
                    warn("Resource not found or unsupported format: $resourcePath", useDispatcher = false)
                    fallbackColor?.let { createFallbackIcon(width, height, it) }
                }
            } catch (e: Exception) {
                error("Error loading icon from resource: $resourcePath", e, useDispatcher = false)
                fallbackColor?.let { createFallbackIcon(width, height, it) }
            }
        }

        private fun createFallbackIcon(width: Int, height: Int, color: Color): ImageIcon {
            require(width > 0 && height > 0) {
                "Icon dimensions must be greater than 0. Current size: ${width}x$height"
            }
            return ImageIcon(BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).apply {
                createGraphics().apply {
                    this.color = color
                    fillRect(0, 0, width, height)
                    dispose()
                }
            })
        }
    }
}