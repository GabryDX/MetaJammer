package com.heronikostudios.metajammer.metadata

import androidx.exifinterface.media.ExifInterface
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageMetadataTagReflectionTest {

    @Test
    fun `verify reflection finds standard tags`() {
        val fields = ExifInterface::class.java.fields
            .filter { it.name.startsWith("TAG_") && it.type == String::class.java }
            .mapNotNull {
                try {
                    it.get(null) as? String
                } catch (_: Exception) {
                    null
                }
            }
            .plus(listOf("ImageResources", "PrintIM", "OwnerName"))
            .distinct()

        // Check if common tags are present
        assertTrue("Should contain TAG_ARTIST", fields.contains(ExifInterface.TAG_ARTIST))
        assertTrue("Should contain TAG_GPS_LATITUDE", fields.contains(ExifInterface.TAG_GPS_LATITUDE))
        assertTrue("Should contain TAG_MAKE", fields.contains(ExifInterface.TAG_MAKE))
        assertTrue("Should contain TAG_DATETIME", fields.contains(ExifInterface.TAG_DATETIME))
        assertTrue("Should contain TAG_XMP", fields.contains(ExifInterface.TAG_XMP))
        
        // Check if custom tags are present
        assertTrue("Should contain ImageResources", fields.contains("ImageResources"))
        assertTrue("Should contain OwnerName", fields.contains("OwnerName"))

        // Check if it found a substantial amount of tags
        assertTrue("Should find a lot of tags, found ${fields.size}", fields.size > 100)
    }
}
