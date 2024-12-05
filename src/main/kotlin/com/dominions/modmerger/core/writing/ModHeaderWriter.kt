package com.dominions.modmerger.core.writing

import com.dominions.modmerger.core.writing.config.ModOutputConfig
import com.dominions.modmerger.domain.MergeWarning
import com.dominions.modmerger.domain.ModFile
import com.dominions.modmerger.infrastructure.Logging
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ModHeaderWriter : Logging {
    private val warnings = mutableListOf<MergeWarning>()

    fun writeHeader(
        writer: java.io.Writer,
        config: ModOutputConfig,
        sourceModFiles: Collection<ModFile>
    ): List<MergeWarning> {
        warnings.clear()

        try {
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

            val headerContent = buildString {
                // Use display name for in-game modname
                appendLine("#modname \"${config.displayName}\"")
                appendLine("#description \"${config.description}")
                appendLine("\nMerged from:")
                sourceModFiles.forEach { appendLine("- ${it.name}") }
                appendLine("\nGenerated: $timestamp\"")

                config.icon?.let { icon ->
                    if (icon.exists()) {
                        appendLine("#icon \"${icon.name}\"")
                    } else {
                        sourceModFiles.firstOrNull()?.let { modFile ->
                            warnings.add(
                                MergeWarning.ResourceWarning(
                                    modFile = modFile,
                                    message = "Icon file not found",
                                    resourcePath = icon.absolutePath
                                )
                            )
                        }
                    }
                }

                appendLine("#version \"${config.version}\"")
                appendLine("\n-- Begin merged content\n")
            }

            writer.write(headerContent)
            debug("Successfully wrote mod header")

        } catch (e: Exception) {
            val error = "Failed to write mod header: ${e.message}"
            error(error)

            sourceModFiles.firstOrNull()?.let { modFile ->
                warnings.add(
                    MergeWarning.ContentWarning(
                        modFile = modFile,
                        message = error
                    )
                )
            }
        }

        return warnings
    }
}