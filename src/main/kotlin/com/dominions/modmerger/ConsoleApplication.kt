package com.dominions.modmerger


import com.dominions.modmerger.core.ModMergerService
import com.dominions.modmerger.domain.ModFile
import com.dominions.modmerger.infrastructure.FileSystem
import kotlinx.coroutines.runBlocking

class ConsoleApplication(
    modMergerService: ModMergerService,
    fileSystem: FileSystem,
) : Application(modMergerService, fileSystem) {

    private object UserMessages {
        const val BANNER = """
            =====================
            = MOD MERGER v1.0.0 =
            =====================
            
            Mod Merger will scan the folder you put it in.
            This should be your Dominions mods folder which can be 
            opened from ingame via Tools & Manuals.
            
            You can also use a specific folder containing only
            the mods that you want to merge.
        """

        const val NO_MOD_FILES = "No .dm files found in current directory!"
        const val MERGE_SUCCESS = "\nSuccessfully created modmerger.dm!"
        const val EXIT_PROMPT = """
            
            What would you like to do?
            1. Try again
            2. Exit
            
            Enter your choice (1-2):"""

        const val INVALID_CHOICE = "Invalid choice. Please enter 1 or 2."

        fun foundModFiles(count: Int) = "Found $count mod files:"
        fun modFile(name: String) = " - $name"
        fun mergeFailed(error: String) = "\nFailed to create modmerger.dm: $error"
    }

    override fun run() {
        var shouldContinue = true

        while (shouldContinue) {
            logger.debug("Starting new application run")
            runBlocking {
                try {
                    displayBanner()
                    processModFiles()
                } catch (e: Exception) {
                    handleError(e)
                }
            }

            shouldContinue = promptForRerun()
        }
    }

    private fun promptForRerun(): Boolean {
        println(UserMessages.EXIT_PROMPT)

        while (true) {
            when (readlnOrNull()?.trim()) {
                "1" -> {
                    println("\nRestarting...\n")
                    return true
                }

                "2" -> {
                    println("\nExiting...\n")
                    return false
                }

                else -> println(UserMessages.INVALID_CHOICE)
            }
        }
    }

    private suspend fun processModFiles() {
        try {
            val modFiles = fileSystem.findModFiles()

            when {
                modFiles.isEmpty() -> {
                    logger.error("No mod files found")
                    println(UserMessages.NO_MOD_FILES)
                    return
                }

                else -> {
                    logger.debug("Found ${modFiles.size} mod files")
                    displayModFiles(modFiles)

                    if (!promptToContinue("Press [Enter] to parse files... (or type 'exit' to quit)")) {
                        return
                    }

                    processMergeResult(
                        modMergerService.mergeMods(modFiles)
                    )
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error during mod file processing" }
            println("\nAn error occurred while processing mod files: ${e.message}")
        }
    }

    private fun displayModFiles(modFiles: List<ModFile>) {
        println(UserMessages.foundModFiles(modFiles.size))
        modFiles.forEach { modFile ->
            println(UserMessages.modFile(modFile.name))
        }
    }

    private fun processMergeResult(result: MergeResult) {
        when (result) {
            is MergeResult.Success -> handleMergeSuccess(result)
            is MergeResult.Failure -> handleMergeFailure(result)
        }
    }

    private fun handleMergeSuccess(result: MergeResult.Success) {
        logger.info("Merge completed successfully")
        println(UserMessages.MERGE_SUCCESS)
        displayWarnings(result.warnings)
    }

    private fun handleMergeFailure(result: MergeResult.Failure) {
        logger.error("Merge failed: ${result.error}")
        println(UserMessages.mergeFailed(result.error))
    }

    private fun handleError(e: Exception) {
        logger.error(e) { "An unexpected error occurred" }
        if (logger.isDebugEnabled) {
            e.printStackTrace()
        }
        println("\nAn unexpected error occurred: ${e.message}")
    }

    private fun displayBanner() {
        println(UserMessages.BANNER.trimIndent())
        promptToContinue()
    }

    private fun promptToContinue(message: String = "Press [Enter] to continue... (or type 'exit' to quit)"): Boolean {
        println("\n$message")
        val input = readlnOrNull()?.lowercase()
        return input != "exit"
    }

    private fun displayWarnings(warnings: List<MergeWarning>) {
        if (warnings.isNotEmpty()) {
            println("\nWarnings:")
            warnings.forEach { warning ->
                val warningMessage = when (warning) {
                    is MergeWarning.AmbiguousSpell ->
                        "Ambiguous spell mapping in ${warning.modFile.name}: ${warning.spellName}"

                    is MergeWarning.InvalidUtf8 ->
                        "Invalid UTF-8 in ${warning.modFile.name}"

                    is MergeWarning.ImplicitId ->
                        "Implicit ID used in ${warning.modFile.name} for ${warning.entityType}"

                    is MergeWarning.ConflictWarning -> TODO()
                    is MergeWarning.ContentWarning -> TODO()
                    is MergeWarning.GeneralWarning -> TODO()
                    is MergeWarning.ResourceWarning -> TODO()
                    is MergeWarning.ValidationWarning -> TODO()
                }
                logger.warn("Warning during merge: $warningMessage")
                println(warningMessage)
            }
        }
    }
}