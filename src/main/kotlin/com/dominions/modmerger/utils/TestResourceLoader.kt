package com.dominions.modmerger.utils

import java.io.InputStreamReader

/**
 * Utility class for loading test resources.
 */
object TestResourceLoader {
    /**
     * Loads a resource file from the test resources' directory.
     * @param path The path to the resource file, relative to the resources directory
     * @return The content of the resource file as a string
     * @throws IllegalArgumentException if the resource cannot be found
     */
    fun loadResource(path: String): String {
        return TestResourceLoader::class.java.getResourceAsStream(path)?.use { stream ->
            InputStreamReader(stream).use { reader ->
                reader.readText()
            }
        } ?: throw IllegalArgumentException("Resource not found: $path")
    }

    /**
     * Verifies that a resource exists.
     * @param path The path to check
     * @return true if the resource exists, false otherwise
     */
    fun resourceExists(path: String): Boolean {
        return TestResourceLoader::class.java.getResource(path) != null
    }
}