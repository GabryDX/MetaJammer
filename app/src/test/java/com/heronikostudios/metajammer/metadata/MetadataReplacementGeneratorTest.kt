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
}
