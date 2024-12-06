package com.dominions.modmerger.infrastructure

import org.slf4j.LoggerFactory
import java.awt.*
import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory

class FontConfigurationManager : Logging {
    private val resourcesPath = "/lib"
    private val requiredFiles = listOf(
        "fontconfig.properties.src",
        "fontconfig.bfc"
    )

    data class SetupResult(
        val success: Boolean,
        val error: String? = null,
        val usedExistingConfig: Boolean = false
    )

    fun setupFontConfiguration(): SetupResult {
        try {
            debug("Starting font configuration setup")

            // Check if we have valid existing configuration
            System.getProperty("java.home")?.let { javaHome ->
                debug("Found java.home: $javaHome")
                val libDir = File(javaHome, "lib")

                if (hasValidFontConfiguration(libDir)) {
                    debug("Using existing font configuration from: $libDir")
                    return SetupResult(success = true, usedExistingConfig = true)
                }
            }

            debug("No valid existing font configuration found, setting up temporary configuration")
            return setupTemporaryFontConfiguration()
        } catch (e: Exception) {
            val errorMessage = buildErrorMessage(e)
            error("Failed to setup font configuration", e)
            showErrorDialog("Font Configuration Error", errorMessage)
            return SetupResult(success = false, error = errorMessage)
        }
    }

    private fun hasValidFontConfiguration(libDir: File): Boolean {
        if (!libDir.exists() || !libDir.isDirectory) {
            debug("Lib directory not found or not a directory: ${libDir.absolutePath}")
            return false
        }

        // Check for either .properties or .properties.src
        val hasProperties = File(libDir, "fontconfig.properties").exists() ||
                File(libDir, "fontconfig.properties.src").exists()

        val hasBfc = File(libDir, "fontconfig.bfc").exists()

        debug("Font configuration check in ${libDir.absolutePath}:")
        debug("- Has properties file: $hasProperties")
        debug("- Has BFC file: $hasBfc")

        return hasProperties && hasBfc
    }

    private fun setupTemporaryFontConfiguration(): SetupResult {
        val tempDir = createTempDirectory()
        debug("Created temp directory: ${tempDir.toFile().absolutePath}")

        try {
            extractFontsToDirectory(tempDir.toFile())

            // Point font configuration to temporary directory
            System.setProperty("java.home", tempDir.toFile().absolutePath)
            System.setProperty("sun.awt.fontconfig", "${tempDir.toFile().absolutePath}/lib/fontconfig.properties.src")

            debug("Font configuration properties set:")
            debug("java.home: ${System.getProperty("java.home")}")
            debug("sun.awt.fontconfig: ${System.getProperty("sun.awt.fontconfig")}")

            // Register shutdown hook to clean up
            Runtime.getRuntime().addShutdownHook(Thread {
                try {
                    debug("Cleaning up temporary font directory")
                    tempDir.toFile().deleteRecursively()
                } catch (e: Exception) {
                    error("Failed to clean up temporary directory", e)
                }
            })

            return SetupResult(success = true)
        } catch (e: Exception) {
            try {
                tempDir.toFile().deleteRecursively()
            } catch (cleanupError: Exception) {
                error("Failed to clean up after error", cleanupError)
            }
            throw e
        }
    }

    private fun extractFontsToDirectory(dir: File) {
        val libDir = File(dir, "lib").apply {
            mkdirs()
            debug("Created lib directory: ${absolutePath}")
        }

        requiredFiles.forEach { fileName ->
            try {
                val resource = javaClass.getResourceAsStream("$resourcesPath/$fileName")
                    ?: throw IOException("Font resource not found: $fileName")

                resource.use { input ->
                    File(libDir, fileName).outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                debug("Successfully extracted: $fileName")
            } catch (e: Exception) {
                error("Failed to extract font file: $fileName", e)
                throw IOException("Failed to extract required font file: $fileName", e)
            }
        }

        // Verify all files were extracted correctly
        val missingFiles = requiredFiles.filter { !File(libDir, it).exists() }
        if (missingFiles.isNotEmpty()) {
            throw IOException("Failed to verify extracted files: $missingFiles")
        }
    }

    private fun buildErrorMessage(e: Exception): String {
        return """
            Failed to initialize font configuration.
            
            Error: ${e.message}
            
            This could be due to:
            - Insufficient permissions to create temporary files
            - Missing or corrupted application resources
            - System temporary directory is not accessible
            - Invalid existing font configuration
            
            Technical Details:
            ${e.stackTraceToString().lines().take(3).joinToString("\n")}
            
            Please ensure the application has write permissions to the temporary directory
            and that all required resources are available.
            If the problem persists, please report this issue.
        """.trimIndent()
    }

    private fun showErrorDialog(title: String, message: String) {
        try {
            // Use basic AWT dialog since Swing isn't initialized yet
            val errorDialog = Dialog(null as Frame?, title, true)
            errorDialog.apply {
                layout = BorderLayout(10, 10)

                // Add padding around components
                add(Panel().apply {
                    layout = BorderLayout(10, 10)
                    val textArea = TextArea(message, 10, 50).apply {
                        isEditable = false
                        background = SystemColor.control
                        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
                    }
                    add(textArea, BorderLayout.CENTER)
                }, BorderLayout.CENTER)

                // Button panel
                add(Panel().apply {
                    layout = FlowLayout(FlowLayout.CENTER, 10, 10)
                    add(Button("OK").apply {
                        preferredSize = Dimension(100, 30)
                        addActionListener {
                            errorDialog.dispose()
                        }
                    })
                }, BorderLayout.SOUTH)

                // Set minimum size and center on screen
                minimumSize = Dimension(600, 400)
                pack()
                setLocationRelativeTo(null)
            }

            // Show dialog on AWT thread
            EventQueue.invokeAndWait {
                errorDialog.isVisible = true
            }
        } catch (e: Exception) {
            // If even the error dialog fails, log it and print to stderr as last resort
            error("Failed to show error dialog", e)
            System.err.println("Critical Error: Failed to initialize font configuration")
            System.err.println(message)
        }
    }
}