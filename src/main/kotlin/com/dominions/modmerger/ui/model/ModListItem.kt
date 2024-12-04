package com.dominions.modmerger.ui.model

import com.dominions.modmerger.domain.ModFile
import mu.KotlinLogging
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class ModSourceType {
    STEAM,
    LOCAL
}

/**
 * UI model representing a mod file with optimized resource loading and caching.
 */
data class ModListItem(
    val modFile: ModFile,
    var isSelected: Boolean = false
) {
    private val logger = KotlinLogging.logger {}

    // Determine source type (Steam or Local)
    val sourceType: ModSourceType by lazy {
        val path = modFile.file?.absolutePath ?: return@lazy ModSourceType.LOCAL
        if (path.contains("\\workshop\\content\\2511500\\")) {
            ModSourceType.STEAM
        } else {
            ModSourceType.LOCAL
        }
    }

    // Eagerly loaded properties
    val fileName: String = modFile.file?.name ?: "Unknown"
    val absolutePath: String = modFile.file?.absolutePath ?: "Unknown"
    val size: Long = modFile.file?.length() ?: 0L
    val lastModified: LocalDateTime = modFile.file?.let {
        LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(it.lastModified()),
            ZoneId.systemDefault()
        )
    } ?: LocalDateTime.now()

    // Cached properties
    private var cachedModName: String? = null
    private var cachedFormattedDate: String? = null
    private var cachedFormattedSize: String? = null

    // Icon path property needed by the table model
    val iconPath: String?
        get() = modFile.iconPath?.let { path ->
            modFile.file?.parentFile?.let { dir ->
                val iconFile = File(dir, path)
                if (iconFile.exists()) iconFile.absolutePath else null
            }
        }

    // Lazy properties with caching
    val modName: String
        get() = cachedModName ?: modFile.modName.also { cachedModName = it }

    fun getFormattedDate(): String = cachedFormattedDate ?: lastModified
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        .also { cachedFormattedDate = it }

    fun getFormattedSize(): String = cachedFormattedSize ?: when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        else -> String.format("%.2f MB", size / (1024.0 * 1024.0))
    }.also { cachedFormattedSize = it }

    // This is needed for copying with new selection state
    fun copy(isSelected: Boolean = this.isSelected) = ModListItem(
        modFile = this.modFile,
        isSelected = isSelected
    )

    companion object {
        fun fromFile(file: File): ModListItem = ModListItem(ModFile.fromFile(file))
    }
}