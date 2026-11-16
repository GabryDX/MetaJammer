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

    @Test
    fun `readMetadata parses ID3v1 and ID3v1_1 tags when ID3v2 is absent`() {
        val dummyAudio = ByteArray(256) { 0xFF.toByte() }
        val id3v1 = ByteArray(128)
        id3v1[0] = 'T'.code.toByte()
        id3v1[1] = 'A'.code.toByte()
        id3v1[2] = 'G'.code.toByte()
        "Song Title".toByteArray().copyInto(id3v1, 3)
        "Great Artist".toByteArray().copyInto(id3v1, 33)
        "Awesome Album".toByteArray().copyInto(id3v1, 63)
        "2026".toByteArray().copyInto(id3v1, 93)
        "Clean Comment".toByteArray().copyInto(id3v1, 97)
        id3v1[125] = 0.toByte()
        id3v1[126] = 7.toByte() // Track 7 (ID3v1.1)

        val fullFile = dummyAudio + id3v1
        val entries = ByteArrayInputStream(fullFile).use {
            Mp3MetadataReader.readMetadata(it)
        }

        val entriesMap = entries.associate { it.key to it.value }
        assertEquals("ID3v1.1", entriesMap["ID3 Version"])
        assertEquals("Song Title", entriesMap["Title"])
        assertEquals("Great Artist", entriesMap["Artist"])
        assertEquals("Awesome Album", entriesMap["Album"])
        assertEquals("2026", entriesMap["Date"])
        assertEquals("Clean Comment", entriesMap["Comment"])
        assertEquals("7", entriesMap["Track Number"])
    }

    @Test
    fun `stripMetadata handles files smaller than 10 bytes gracefully without data loss`() {
        val tinyAudio = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        val out = ByteArrayOutputStream()
        val success = ByteArrayInputStream(tinyAudio).use {
            Mp3MetadataReader.stripMetadata(it, out)
        }
        assertTrue(success)
        assertEquals(tinyAudio.toList(), out.toByteArray().toList())
    }

    @Test
    fun `stripMetadata throughput performance test on 4MB sample`() {
        if (sampleMp3File == null || !sampleMp3File.exists()) return

        val inputBytes = sampleMp3File.readBytes()
        val iterations = 10
        val startTime = System.nanoTime()

        repeat(iterations) {
            val out = object : java.io.OutputStream() {
                var bytesWritten = 0L
                override fun write(b: Int) { bytesWritten++ }
                override fun write(b: ByteArray, off: Int, len: Int) { bytesWritten += len }
            }
            val ok = ByteArrayInputStream(inputBytes).use { input ->
                Mp3MetadataReader.stripMetadata(input, out)
            }
            assertTrue(ok)
            assertTrue(out.bytesWritten > 4_000_000L)
        }

        val totalDurationMs = (System.nanoTime() - startTime) / 1_000_000.0
        val avgDurationMs = totalDurationMs / iterations
        val throughputMBps = (inputBytes.size / (1024.0 * 1024.0)) / (avgDurationMs / 1000.0)

        println("MP3 Strip Performance: avg ${avgDurationMs}ms per 4.2MB file (${throughputMBps} MB/s)")
        assertTrue("Throughput must be at least 50 MB/s", throughputMBps > 50.0)
    }
}
