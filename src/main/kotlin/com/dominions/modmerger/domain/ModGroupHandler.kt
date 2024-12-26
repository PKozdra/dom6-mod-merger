package com.dominions.modmerger.domain

class ModGroupHandler(private val registry: ModGroupRegistry) {
    // Keep track of original files for resource copying
    private val groupSourceFiles = mutableMapOf<String, List<ModFile>>()

    fun getSourceFiles(combinedName: String): List<ModFile> =
        groupSourceFiles[combinedName] ?: emptyList()

    fun processFiles(files: List<ModFile>): List<ModFile> {
        groupSourceFiles.clear()

        // Group files
        val groupedFiles = files.groupBy { file ->
            registry.findGroupForMod(file)
        }

        val result = mutableListOf<ModFile>()

        // Add ungrouped files
        result.addAll(groupedFiles[null] ?: emptyList())

        // Process each group
        groupedFiles.filterKeys { it != null }.forEach { (group, groupFiles) ->
            val combinedName = "_Combined_${group!!.id}"

            // Store original files for resource handling
            groupSourceFiles[combinedName] = groupFiles

            // Create combined file
            result.add(createCombinedFile(groupFiles, combinedName))
        }

        return result
    }

    private fun createCombinedFile(files: List<ModFile>, name: String): ModFile {
        val combinedContent = buildString {
            files.forEachIndexed { index, file ->
                if (index > 0) appendLine()
                appendLine("-- Start of original file: ${file.name}")
                appendLine(file.content)
                appendLine("-- End of original file: ${file.name}")
            }
        }

        return ModFile.fromContent(name, combinedContent)
    }
}
