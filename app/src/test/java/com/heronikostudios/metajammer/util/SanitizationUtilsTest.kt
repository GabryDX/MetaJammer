package com.heronikostudios.metajammer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SanitizationUtilsTest {

    @Test
    fun `sanitizeFileName removes path traversal components`() {
        val input = "../../../etc/passwd"
        val result = SanitizationUtils.sanitizeFileName(input)
        assertEquals("passwd", result)
    }

    @Test
    fun `sanitizeFileName handles backslash path separators`() {
        val input = "C:\\Windows\\System32\\cmd.exe"
        val result = SanitizationUtils.sanitizeFileName(input)
        assertEquals("cmd.exe", result)
    }

    @Test
    fun `sanitizeFileName replaces illegal characters with underscores`() {
        val input = "my file!@#$%^&*().jpg"
        val result = SanitizationUtils.sanitizeFileName(input)
        assertEquals("my_file__________.jpg", result)
    }

    @Test
    fun `sanitizeFileName handles null or blank input`() {
        val resultNull = SanitizationUtils.sanitizeFileName(null)
        assertTrue(resultNull.startsWith("unknown_"))

        val resultBlank = SanitizationUtils.sanitizeFileName("   ")
        assertTrue(resultBlank.startsWith("unknown_"))
    }

    @Test
    fun `sanitizeFileName handles hidden files by prefixing them`() {
        val input = ".bashrc"
        val result = SanitizationUtils.sanitizeFileName(input)
        assertTrue(result.startsWith("file_"))
        assertTrue(result.endsWith(".bashrc"))
    }

    @Test
    fun `sanitizeFileName prevents returning just dots`() {
        val input = "..."
        val result = SanitizationUtils.sanitizeFileName(input)
        assertTrue(result.startsWith("file_"))
        assertTrue(!result.contains(".."))
    }

    @Test
    fun `sanitizeSimple cleans prefixes and suffixes`() {
        val input = "my-prefix!"
        val result = SanitizationUtils.sanitizeSimple(input)
        assertEquals("my-prefix_", result)
    }

    @Test
    fun `sanitizeFileName handles complex path traversal`() {
        val input = "folder/../folder2/filename.txt"
        val result = SanitizationUtils.sanitizeFileName(input)
        assertEquals("filename.txt", result)
    }

    @Test
    fun `sanitizeFileName handles mixed separators`() {
        val input = "some/path\\other\\file.png"
        val result = SanitizationUtils.sanitizeFileName(input)
        assertEquals("file.png", result)
    }
}
