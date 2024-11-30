// src/main/kotlin/com/dominions/modmerger/ui/util/IconCache.kt
package com.dominions.modmerger.ui.util

import mu.KotlinLogging
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.File
import java.lang.ref.SoftReference
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import javax.swing.Icon
import javax.swing.ImageIcon

object IconCache {
    private val logger = KotlinLogging.logger {}
    private val cache = ConcurrentHashMap<String, SoftReference<Icon>>()
    private val defaultIcon = createDefaultIcon(256, 64)

    fun getIcon(path: String?, width: Int, height: Int): Icon {
        if (path == null) return defaultIcon

        return cache.compute(path) { _, ref ->
            val cachedIcon = ref?.get()
            if (cachedIcon != null) {
                logger.debug { "Cache hit for icon: $path" }
                return@compute ref
            }

            logger.debug { "Cache miss for icon: $path" }
            val newIcon = loadIcon(path, width, height)
            SoftReference(newIcon)
        }?.get() ?: defaultIcon
    }

    private fun loadIcon(path: String, width: Int, height: Int): Icon {
        return try {
            val file = File(path)
            if (!file.exists()) return defaultIcon

            val image = ImageIO.read(file)
            if (image == null) return defaultIcon

            val scaled = image.getScaledInstance(width, height, Image.SCALE_SMOOTH)
            ImageIcon(scaled)
        } catch (e: Exception) {
            logger.error(e) { "Failed to load icon: $path" }
            defaultIcon
        }
    }

    private fun createDefaultIcon(width: Int, height: Int): Icon {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g2d = image.createGraphics()
        try {
            g2d.color = java.awt.Color(240, 240, 240)
            g2d.fillRect(0, 0, width, height)
            g2d.color = java.awt.Color(220, 220, 220)
            g2d.drawRect(0, 0, width - 1, height - 1)
            g2d.color = java.awt.Color(150, 150, 150)
            g2d.font = java.awt.Font("SansSerif", java.awt.Font.BOLD, (height * 0.4).toInt())
            val text = "NO ICON"
            val metrics = g2d.fontMetrics
            g2d.drawString(
                text,
                (width - metrics.stringWidth(text)) / 2,
                (height - metrics.height) / 2 + metrics.ascent
            )
        } finally {
            g2d.dispose()
        }
        return ImageIcon(image)
    }

    fun clearCache() {
        cache.clear()
    }
}