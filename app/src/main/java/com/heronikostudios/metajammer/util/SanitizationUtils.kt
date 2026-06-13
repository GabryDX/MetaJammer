package com.heronikostudios.metajammer.util

import java.io.File

object SanitizationUtils {

    // Pre-compile regex for better performance in batch operations
    private val ILLEGAL_CHAR_REGEX = Regex("[^a-zA-Z0-9._-]")

    /**
     * Sanitizes a filename to prevent path traversal and remove illegal characters.
     */
    fun sanitizeFileName(fileName: String?): String {
        if (fileName.isNullOrBlank()) return "unknown_${System.currentTimeMillis()}"

        // 1. Strip path components by getting only the 'name' part
        val nameOnly = File(fileName).name
            .split("/")
            .last()
            .split("\\")
            .last()

        // 2. Replace common illegal characters with underscores
        val sanitized = nameOnly.replace(ILLEGAL_CHAR_REGEX, "_")

        // 3. Ensure it's not empty and doesn't start with a dot (hidden file)
        return if (sanitized.isBlank() || sanitized.all { it == '.' }) {
            "file_${System.currentTimeMillis()}"
        } else if (sanitized.startsWith(".")) {
            "file_${System.currentTimeMillis()}_$sanitized"
        } else {
            sanitized
        }
    }

    /**
     * Simple sanitization for string components like prefixes/suffixes.
     */
    fun sanitizeSimple(text: String): String {
        return text.replace(ILLEGAL_CHAR_REGEX, "_")
    }
}
