package com.heronikostudios.metajammer.metadata

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataReplacementGeneratorTest {

    @Test
    fun `generatePlan produces complete image plans by default`() {
        val plan = MetadataReplacementGenerator.generatePlan()
        
        assertNotNull(plan.dateTime)
        assertNotNull(plan.make)
        assertNotNull(plan.model)
        assertNotNull(plan.software)
        assertNotNull(plan.imageDescription)
        assertNotNull(plan.userComment)
        
        // Ensure values aren't empty
        assertTrue(plan.dateTime.isNotBlank())
        assertTrue(plan.make.isNotBlank())
        assertTrue(plan.model.isNotBlank())
    }

    @Test
    fun `generatePlan produces audio-specific metadata`() {
        val plan = MetadataReplacementGenerator.generatePlan(mimeType = "audio/mpeg")
        
        assertNotNull(plan.title)
        assertNotNull(plan.artist)
        assertNotNull(plan.album)
        assertNotNull(plan.genre)
        assertNotNull(plan.mediaDate)
        assertTrue(plan.title!!.isNotBlank())
        assertTrue(plan.artist!!.isNotBlank())
        assertTrue(plan.mediaDate!!.contains("T"))
    }

    @Test
    fun `generatePlan produces video-specific metadata`() {
        val plan = MetadataReplacementGenerator.generatePlan(mimeType = "video/mp4")
        
        assertNotNull(plan.title)
        assertNotNull(plan.artist) // Used as Director
        assertNotNull(plan.genre)
        assertNotNull(plan.mediaDate)
        assertNull(plan.album) // Videos shouldn't have albums in this context
        assertTrue(plan.title!!.isNotBlank())
        assertTrue(plan.mediaDate!!.contains("T"))
    }

    @Test
    fun `generatePlan produces pdf-specific metadata`() {
        val plan = MetadataReplacementGenerator.generatePlan(mimeType = "application/pdf")
        
        assertNotNull(plan.pdfTitle)
        assertNotNull(plan.author)
        assertNotNull(plan.creator)
        assertTrue(plan.pdfTitle!!.isNotBlank())
        assertTrue(plan.author!!.isNotBlank())
    }

    @Test
    fun `randomRecentDateTime returns correctly formatted EXIF date`() {
        val dateTime = MetadataReplacementGenerator.randomRecentDateTime()
        // Format: YYYY:MM:DD HH:MM:SS
        val regex = Regex("^\\d{4}:\\d{2}:\\d{2} \\d{2}:\\d{2}:\\d{2}$")
        assertTrue("DateTime '$dateTime' should match EXIF format", regex.matches(dateTime))
    }

    @Test
    fun `randomIsoDateTime returns correctly formatted ISO date`() {
        val dateTime = MetadataReplacementGenerator.randomIsoDateTime()
        // Format: YYYYMMDDTHHMMSS.SSSZ
        val regex = Regex("^\\d{8}T\\d{6}\\.000Z$")
        assertTrue("ISO DateTime '$dateTime' should match ISO format", regex.matches(dateTime))
    }

    @Test
    fun `randomLatLong returns valid coordinates`() {
        val (lat, lon) = MetadataReplacementGenerator.randomLatLong()
        assertTrue(lat in -90.0..90.0)
        assertTrue(lon in -180.0..180.0)
    }

    @Test
    fun `randomModel returns plausible strings`() {
        val model = MetadataReplacementGenerator.randomModel("Sony")
        assertFalse(model.contains("null"))
        assertTrue(model.length > 2)
    }

    @Test
    fun `generatePlan with custom coordinates preserves coordinates and computes refs`() {
        // Northern/Eastern hemisphere
        val planNE = MetadataReplacementGenerator.generatePlan(
            existingLat = 35.6762,
            existingLon = 139.6503
        )
        org.junit.Assert.assertEquals(35.6762, planNE.latitude, 0.02)
        org.junit.Assert.assertEquals(139.6503, planNE.longitude, 0.02)
        org.junit.Assert.assertEquals("N", planNE.latitudeRef)
        org.junit.Assert.assertEquals("E", planNE.longitudeRef)

        // Southern/Western hemisphere
        val planSW = MetadataReplacementGenerator.generatePlan(
            existingLat = -22.9068,
            existingLon = -43.1729
        )
        org.junit.Assert.assertEquals(-22.9068, planSW.latitude, 0.02)
        org.junit.Assert.assertEquals(-43.1729, planSW.longitude, 0.02)
        org.junit.Assert.assertEquals("S", planSW.latitudeRef)
        org.junit.Assert.assertEquals("W", planSW.longitudeRef)
    }

    @Test
    fun `generatePlan produces photographic parameters with valid formats`() {
        repeat(20) {
            val plan = MetadataReplacementGenerator.generatePlan()
            // ISO sensitivity must be a positive integer
            val iso = plan.photographicSensitivity.toIntOrNull()
            assertNotNull("ISO should be an integer", iso)
            assertTrue("ISO should be >= 50", iso!! >= 50)

            // F-number must be a positive decimal
            val fNum = plan.fNumber.toDoubleOrNull()
            assertNotNull("F-Number should be a double", fNum)
            assertTrue("F-Number should be > 0", fNum!! > 0)

            // Exposure time should match fraction "1/X" or decimal
            assertTrue("Exposure time should be valid format", plan.exposureTime.contains("/") || plan.exposureTime.toDoubleOrNull() != null)

            // Focal length should be rational "X/1" or number
            assertTrue("Focal length should be valid format", plan.focalLength.isNotBlank())
        }
    }

    @Test
    fun `all supported makes have corresponding models in generator`() {
        val supportedMakes = listOf(
            "Canon", "Nikon", "Sony", "Fujifilm", "Panasonic", "Olympus",
            "Apple", "Samsung", "Google", "Xiaomi", "OnePlus", "Huawei"
        )
        for (make in supportedMakes) {
            val model = MetadataReplacementGenerator.randomModel(make)
            assertTrue("Make '$make' must produce a valid model", model.isNotBlank())
            assertFalse("Model must not be generic fallback for supported make", model == "Generic Camera")
        }
    }
}
