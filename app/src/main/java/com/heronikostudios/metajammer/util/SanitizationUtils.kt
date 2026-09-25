package com.heronikostudios.metajammer.util

import java.io.File

object SanitizationUtils {

    // Filesystem-reserved characters across Linux, Android, and Windows: / \ : * ? " < > | and control characters
    private val ILLEGAL_CHAR_REGEX = Regex("""[/\\:*?"<>|\u0000-\u001F]""")
    private val collisionCounter = java.util.concurrent.atomic.AtomicLong(0)

    /**
     * Sanitizes a filename to prevent path traversal and remove illegal filesystem characters,
     * while preserving international Unicode alphabets, digits, and standard punctuation.
     */
    fun sanitizeFileName(fileName: String?): String {
        if (fileName.isNullOrBlank()) return "unknown_${System.currentTimeMillis()}"

        // 1. Strip path components by getting only the 'name' part
        val nameOnly = File(fileName).name
            .split("/")
            .last()
            .split("\\")
            .last()
            .trim()

        // 2. Replace illegal filesystem characters with underscores
        val sanitized = nameOnly.replace(ILLEGAL_CHAR_REGEX, "_").trim()

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

    /**
     * Generates an output filename based on settings.
     */
    fun generateOutputName(
        originalName: String,
        useRandomFileNames: Boolean,
        prefix: String,
        suffix: String
    ): String {
        val dotIndex = originalName.lastIndexOf('.')
        val base = if (dotIndex > 0) originalName.substring(0, dotIndex) else originalName
        val ext = if (dotIndex > 0) originalName.substring(dotIndex) else ""

        val finalBase = if (useRandomFileNames) {
            val randomPart = (100000..999999).random()
            val seq = collisionCounter.incrementAndGet()
            "mj_${System.currentTimeMillis()}_${seq}_$randomPart"
        } else {
            val sPrefix = sanitizeSimple(prefix)
            val sSuffix = sanitizeSimple(suffix)
            val sanitizedBase = sanitizeSimple(base)
            "$sPrefix$sanitizedBase$sSuffix"
        }

        val extension = sanitizeSimple(ext)
        return "$finalBase$extension"
    }
}
