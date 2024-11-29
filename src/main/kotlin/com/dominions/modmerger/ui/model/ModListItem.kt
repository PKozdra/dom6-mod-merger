package com.dominions.modmerger.ui.model

import com.dominions.modmerger.domain.ModFile
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.ImageIcon

/**
 * UI model representing a mod file with its metadata and display properties.
 * Leverages lazy loading from ModFile for efficient resource usage.
 */
data class ModListItem(
    val modFile: ModFile,
    var isSelected: Boolean = false
) {
    // File system properties - eagerly loaded as they're lightweight
    val fileName: String = modFile.file?.name ?: "Unknown"
    val absolutePath: String = modFile.file?.absolutePath ?: "Unknown"
    val size: Long = modFile.file?.length() ?: 0L
    val lastModified: LocalDateTime = modFile.file?.let {
        LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(it.lastModified()),
            ZoneId.systemDefault()
        )
    } ?: LocalDateTime.now()

    // Metadata properties - delegated to ModFile's lazy loading implementation
    val modName: String get() = modFile.modName
    val description: String get() = modFile.description
    val version: String get() = modFile.version

    // Icon is lazy loaded only when needed
    val icon: ImageIcon? by lazy {
        modFile.iconPath?.let { path ->
            modFile.file?.parentFile?.let { dir ->
                val iconFile = File(dir, path)
                if (iconFile.exists()) ImageIcon(iconFile.absolutePath) else null
            }
        }
    }

    /**
     * Returns a human-readable formatted file size.
     */
    fun getFormattedSize(): String = when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        else -> String.format("%.2f MB", size / (1024.0 * 1024.0))
    }

    /**
     * Returns a formatted date string for the last modified timestamp.
     */
    fun getFormattedDate(): String = lastModified.format(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    )

    companion object {
        /**
         * Creates a ModListItem from a File, handling the ModFile creation internally.
         */
        fun fromFile(file: File): ModListItem = ModListItem(
            modFile = ModFile.fromFile(file)
        )
    }
}