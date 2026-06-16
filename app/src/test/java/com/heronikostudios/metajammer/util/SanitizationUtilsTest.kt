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
    fun `generateOutputName produces unique names in a tight loop with random names enabled`() {
        val originalName = "test.jpg"
        val names = mutableSetOf<String>()
        val count = 100
        repeat(count) {
            names.add(SanitizationUtils.generateOutputName(originalName, true, "", ""))
        }
        // Since it uses System.currentTimeMillis() AND random part, 
        // collisions are extremely unlikely even in a tight loop.
        assertEquals("Should have generated $count unique names", count, names.size)
    }

    @Test
    fun `generateOutputName respects prefix and suffix when random names disabled`() {
        val originalName = "photo.png"
        val result = SanitizationUtils.generateOutputName(originalName, false, "PRE_", "_POST")
        assertEquals("PRE_photo_POST.png", result)
    }
}
