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

class OggMetadataReaderTest {

    private val sampleOggFile = listOf(
        File("test_data/fake_metadata_sample.ogg"),
        File("../test_data/fake_metadata_sample.ogg")
    ).firstOrNull { it.exists() } ?: File("test_data/fake_metadata_sample.ogg")

    @Test
    fun `readMetadata parses all Vorbis comment fields from sample Ogg file`() {
        assertTrue("Sample OGG test file must exist", sampleOggFile.exists())
        val entries = sampleOggFile.inputStream().use { stream ->
            OggMetadataReader.readMetadata(stream)
        }
        val entriesMap = entries.associate { it.key to it.value }

        assertEquals("Classified Audio Intercept 2026", entriesMap["Title"])
        assertEquals("Dr. Jane Doe", entriesMap["Artist"])
        assertEquals("Alex Vance", entriesMap["Performer"])
        assertEquals("Project Cerberus Audio Vault", entriesMap["Album"])
        assertEquals("2026-10-02", entriesMap["Date"])
        assertEquals("Geneva, Switzerland", entriesMap["Location"])
        assertTrue(entriesMap["GPS Coordinates"]?.contains("46.204391 N") == true)
        assertTrue(entriesMap["Copyright"]?.contains("Acme Cybernetics") == true)
        assertEquals("Acme Cybernetics Security Ltd.", entriesMap["Organization"])
        assertEquals("research@cybernetics.corp", entriesMap["Contact"])
        assertEquals("Creative Commons Attribution-NonCommercial 4.0 International", entriesMap["License"])
        assertEquals("SN-AUDIO-REC-90812-X", entriesMap["Device ID"])
        assertEquals("TOP SECRET // LEVEL 4 // AIR-GAPPED", entriesMap["Classification"])
        assertEquals("Alex Vance", entriesMap["Auditor"])
        assertEquals("Dr. Jane Doe", entriesMap["Investigator"])
        assertEquals("7f3b891a-1d54-4a56-829b-00123456789a", entriesMap["UUID"])
        assertEquals("1", entriesMap["Track Number"])
        assertTrue(entriesMap["Encoder"]?.contains("Antigravity Audio Studio") == true)
        assertEquals("Present (Vorbis Picture Block)", entriesMap["Embedded Picture"])
        assertEquals("Present (XMP Metadata)", entriesMap["XMP Packet"])
    }

    @Test
    fun `stripMetadata removes all Vorbis comments and preserves valid Ogg pages and CRCs`() {
        assertTrue("Sample OGG test file must exist", sampleOggFile.exists())
        val outputStream = ByteArrayOutputStream()
        val success = sampleOggFile.inputStream().use { input ->
            OggMetadataReader.stripMetadata(input, outputStream)
        }
        assertTrue("stripMetadata must return true", success)

        val strippedBytes = outputStream.toByteArray()
        assertTrue("Stripped file must have non-zero content", strippedBytes.size > 100)
        assertTrue("Stripped file should be smaller than original", strippedBytes.size < sampleOggFile.length())

        // Verify with OggMetadataReader that no comments remain
        val remainingEntries = ByteArrayInputStream(strippedBytes).use { input ->
            OggMetadataReader.readMetadata(input)
        }
        val remainingMap = remainingEntries.associate { it.key to it.value }

        assertFalse("Stripped OGG should not have Title", remainingMap.containsKey("Title"))
        assertFalse("Stripped OGG should not have Artist", remainingMap.containsKey("Artist"))
        assertFalse("Stripped OGG should not have Location", remainingMap.containsKey("Location"))
        assertFalse("Stripped OGG should not have GPS Coordinates", remainingMap.containsKey("GPS Coordinates"))
        assertFalse("Stripped OGG should not have Embedded Picture", remainingMap.containsKey("Embedded Picture"))
        assertFalse("Stripped OGG should not have XMP Packet", remainingMap.containsKey("XMP Packet"))

        // Verify valid Ogg page headers and continuous sequence numbers
        val inputStream = ByteArrayInputStream(strippedBytes)
        var pageCount = 0
        var prevSeq = -1
        while (true) {
            val page = OggMetadataReader.readOggPage(inputStream) ?: break
            assertEquals("Page sequence numbers must be strictly consecutive", prevSeq + 1, page.sequence)
            prevSeq = page.sequence
            pageCount++
        }
        assertTrue("Must have read at least 3 pages (bos, clean comment, audio/setup)", pageCount >= 3)
    }

    @Test
    fun `poisonMetadata replaces Vorbis comments with spoofed plan values`() {
        assertTrue("Sample OGG test file must exist", sampleOggFile.exists())
        val plan = MetadataReplacementPlan(
            dateTime = "2025:12:31 23:59:59",
            make = "Olympus",
            model = "LS-P4",
            software = "MetaJammer Audio Scrubber v1.0",
            imageDescription = "Declassified Telemetry Stream",
            userComment = "Poisoned Vorbis Audio Test",
            photographicSensitivity = "0",
            exposureTime = "0",
            fNumber = "0",
            focalLength = "0",
            latitude = 37.7749,
            longitude = -122.4194,
            latitudeRef = "N",
            longitudeRef = "W",
            whiteBalance = "0",
            flash = "0"
        )

        val outputStream = ByteArrayOutputStream()
        val success = sampleOggFile.inputStream().use { input ->
            OggMetadataReader.poisonMetadata(input, outputStream, plan)
        }
        assertTrue("poisonMetadata must return true", success)

        val poisonedBytes = outputStream.toByteArray()
        val poisonedEntries = ByteArrayInputStream(poisonedBytes).use { input ->
            OggMetadataReader.readMetadata(input)
        }
        val poisonedMap = poisonedEntries.associate { it.key to it.value }

        assertEquals("Declassified Telemetry Stream", poisonedMap["Title"])
        assertEquals("Olympus LS-P4", poisonedMap["Artist"])
        assertEquals("2025:12:31 23:59:59", poisonedMap["Date"])
        assertTrue(poisonedMap["Location"]?.contains("37.7749") == true)
        assertTrue(poisonedMap["GPS Coordinates"]?.contains("37.7749 N") == true)
        assertEquals("MetaJammer Audio Scrubber v1.0", poisonedMap["Encoder"])
        assertEquals("Poisoned Vorbis Audio Test", poisonedMap["Comment"])

        // Original sensitive fields must not exist
        assertFalse("Poisoned file should not retain original author", poisonedMap.containsValue("Dr. Jane Doe"))
        assertFalse("Poisoned file should not retain original location", poisonedMap.containsValue("Geneva, Switzerland"))
        assertFalse("Poisoned file should not retain original classification", poisonedMap.containsValue("TOP SECRET // LEVEL 4 // AIR-GAPPED"))
    }
}
