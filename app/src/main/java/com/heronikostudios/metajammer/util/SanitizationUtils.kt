package com.heronikostudios.metajammer.util

import java.io.File

object SanitizationUtils {

    /**
     * Sanitizes a filename to prevent path traversal and remove illegal characters.
     */
    fun sanitizeFileName(fileName: String?): String {
        if (fileName.isNullOrBlank()) return "unknown_${System.currentTimeMillis()}"

        // 1. Strip path components by getting only the 'name' part
        // We use File().name but also manually check for path separators just in case
        val nameOnly = File(fileName).name
            .split("/")
            .last()
            .split("\\")
            .last()

        // 2. Replace common illegal characters with underscores
        val sanitized = nameOnly.replace(Regex("[^a-zA-Z0-9._-]"), "_")

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
        return text.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }
}
