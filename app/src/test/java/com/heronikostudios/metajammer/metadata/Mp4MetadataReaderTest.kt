package com.heronikostudios.metajammer.metadata

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

class Mp4MetadataReaderTest {

    @Test
    fun testReadMetadataOnFakeMetadataSampleMp4() {
        val testMp4 = listOf(File("test_data/fake_metadata_sample.mp4"), File("../test_data/fake_metadata_sample.mp4"))
            .firstOrNull { it.exists() }
        assertNotNull("fake_metadata_sample.mp4 should exist", testMp4)

        val entries = testMp4!!.inputStream().use { stream ->
            Mp4MetadataReader.readMetadata(stream)
        }

        assertTrue("Should read multiple metadata entries, found ${entries.size}", entries.size >= 10)
        assertTrue(entries.any { it.key == "Title" && it.value.contains("Classified Drone Surveillance") })
        assertTrue(entries.any { it.key == "Artist" && it.value.contains("Dr. Jane Doe") })
        assertTrue(entries.any { it.key == "Album" && it.value.contains("Project Cerberus") })
        assertTrue(entries.any { it.key == "Date" && it.value == "2026-10-02" })
        assertTrue(entries.any { it.key == "Location" && it.value.contains("+46.2043+006.1431") })
        assertTrue(entries.any { it.key == "Copyright" && it.value.contains("Acme Cybernetics") })
        assertTrue(entries.any { it.key == "Comment" && it.value.contains("Synthetic telemetry video") })
        assertTrue(entries.any { it.key == "Software" && it.value.contains("Antigravity") })
        assertTrue(entries.any { it.key == "CameraModel" && it.value.contains("Sony ILCE-7RM5") })
        assertTrue(entries.any { it.key == "GPS" && it.value.contains("46.204391 N, 6.143158 E") })
    }

    @Test
    fun testStripContainerMetadataOnFakeMetadataSampleMp4() {
        val testMp4 = listOf(File("test_data/fake_metadata_sample.mp4"), File("../test_data/fake_metadata_sample.mp4"))
            .firstOrNull { it.exists() }
        assertNotNull("fake_metadata_sample.mp4 should exist", testMp4)

        val strippedOutput = ByteArrayOutputStream()
        val stripped = testMp4!!.inputStream().use { input ->
            Mp4MetadataReader.stripContainerMetadata(input, strippedOutput)
        }
        assertTrue("Should return true indicating metadata was stripped", stripped)

        val strippedBytes = strippedOutput.toByteArray()
        assertEquals("Output file size should be identical because udta/uuid are replaced with free", testMp4.length(), strippedBytes.size.toLong())

        val postEntries = ByteArrayInputStream(strippedBytes).use { input ->
            Mp4MetadataReader.readMetadata(input)
        }
        assertTrue("All metadata should be wiped after container scrubbing, but found: $postEntries", postEntries.isEmpty())
    }
}
