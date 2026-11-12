package com.heronikostudios.metajammer.metadata

import com.heronikostudios.metajammer.domain.model.MetadataReplacementPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

class Mp3MetadataReaderTest {

    private val sampleMp3File = listOf(
        File("test_data/real/geminipro_1794438035738.mp3"),
        File("../test_data/real/geminipro_1794438035738.mp3")
    ).firstOrNull { it.exists() }

    @Test
    fun `readMetadata detects ID3v2 and C2PA GEOB manifest from geminipro sample`() {
        if (sampleMp3File == null || !sampleMp3File.exists()) return

        val entries = sampleMp3File.inputStream().use { stream ->
            Mp3MetadataReader.readMetadata(stream)
        }

        assertTrue("Should detect at least 1 metadata entry", entries.isNotEmpty())

        val entriesMap = entries.associate { it.key to it.value }
        assertEquals("ID3v2.3.0", entriesMap["ID3 Version"])

        val c2paEntry = entries.firstOrNull { it.key.contains("C2PA", ignoreCase = true) }
        assertNotNull("Should detect C2PA / GEOB frame in Gemini Pro MP3", c2paEntry)
        assertTrue(c2paEntry?.value?.contains("c2pa", ignoreCase = true) == true)
        assertTrue(c2paEntry?.value?.contains("application/c2pa", ignoreCase = true) == true)
    }

    @Test
    fun `stripMetadata completely removes ID3v2 and C2PA manifest while preserving MPEG audio frames`() {
        if (sampleMp3File == null || !sampleMp3File.exists()) return

        val out = ByteArrayOutputStream()
        val success = sampleMp3File.inputStream().use { input ->
            Mp3MetadataReader.stripMetadata(input, out)
        }

        assertTrue("stripMetadata must succeed", success)
        val strippedBytes = out.toByteArray()
        assertTrue("Stripped output must not be empty", strippedBytes.size > 1000)

        // Verify ID3 header is gone
        assertFalse(
            "Stripped file must not start with ID3 magic",
            strippedBytes[0] == 'I'.code.toByte() &&
                strippedBytes[1] == 'D'.code.toByte() &&
                strippedBytes[2] == '3'.code.toByte()
        )

        // Verify it starts directly with MPEG frame sync word (0xFF, 0xFB)
        assertEquals(0xFF.toByte(), strippedBytes[0])
        assertEquals((0xE0).toByte(), (strippedBytes[1].toInt() and 0xE0).toByte())

        // Re-reading stripped audio must find zero metadata
        val remainingEntries = ByteArrayInputStream(strippedBytes).use {
            Mp3MetadataReader.readMetadata(it)
        }
        assertTrue("No metadata should remain after stripping", remainingEntries.isEmpty())
    }

    @Test
    fun `stripMetadata strips trailing ID3v1 tag`() {
        val dummyAudio = ByteArray(500) { (it % 256).toByte() }
        dummyAudio[0] = 0xFF.toByte()
        dummyAudio[1] = 0xFB.toByte()

        val id3v1 = ByteArray(128)
        id3v1[0] = 'T'.code.toByte()
        id3v1[1] = 'A'.code.toByte()
        id3v1[2] = 'G'.code.toByte()
        "Test Song".toByteArray().copyInto(id3v1, 3)

        val fullFile = dummyAudio + id3v1
        val out = ByteArrayOutputStream()

        val success = ByteArrayInputStream(fullFile).use { input ->
            Mp3MetadataReader.stripMetadata(input, out)
        }

        assertTrue(success)
        val result = out.toByteArray()
        assertEquals(dummyAudio.size, result.size)
        assertEquals(dummyAudio.toList(), result.toList())
    }

    @Test
    fun `poisonMetadata injects synthetic ID3v2 tag with fake profile and comment`() {
        if (sampleMp3File == null || !sampleMp3File.exists()) return

        val plan = MetadataReplacementPlan(
            make = "Sony",
            model = "Alpha A7 IV",
            software = "LAME 3.100",
            title = "Voice Memo",
            userComment = "Sanitized and poisoned with MetaJammer",
            dateTime = "2026-11-12 15:00:00",
            latitude = 37.7749,
            longitude = -122.4194
        )

        val out = ByteArrayOutputStream()
        val success = sampleMp3File.inputStream().use { input ->
            Mp3MetadataReader.poisonMetadata(input, out, plan)
        }

        assertTrue("poisonMetadata must succeed", success)
        val poisonedBytes = out.toByteArray()

        // Verify ID3 magic is present
        assertEquals('I'.code.toByte(), poisonedBytes[0])
        assertEquals('D'.code.toByte(), poisonedBytes[1])
        assertEquals('3'.code.toByte(), poisonedBytes[2])

        // Parse poisoned tag
        val entries = ByteArrayInputStream(poisonedBytes).use {
            Mp3MetadataReader.readMetadata(it)
        }
        val entriesMap = entries.associate { it.key to it.value }

        assertEquals("ID3v2.3.0", entriesMap["ID3 Version"])
        assertEquals("Sony Alpha A7 IV", entriesMap["Artist"])
        assertEquals("Voice Memo", entriesMap["Title"])
        assertEquals("2026", entriesMap["Date"])
        assertTrue(entriesMap["Comment"]?.contains("Sanitized and poisoned") == true)

        // Verify C2PA is completely absent
        assertFalse(entries.any { it.key.contains("C2PA", ignoreCase = true) })
    }
}
