package com.heronikostudios.metajammer.metadata

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataReplacementGeneratorTest {

    @Test
    fun `generatePlan produces complete plans`() {
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
    fun `randomRecentDateTime returns correctly formatted EXIF date`() {
        val dateTime = MetadataReplacementGenerator.randomRecentDateTime()
        // Format: YYYY:MM:DD HH:MM:SS
        val regex = Regex("^\\d{4}:\\d{2}:\\d{2} \\d{2}:\\d{2}:\\d{2}$")
        assertTrue("DateTime '$dateTime' should match EXIF format", regex.matches(dateTime))
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
