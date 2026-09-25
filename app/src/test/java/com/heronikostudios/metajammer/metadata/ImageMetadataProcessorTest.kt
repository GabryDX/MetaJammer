package com.heronikostudios.metajammer.metadata

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import com.heronikostudios.metajammer.data.FileRepository
import com.heronikostudios.metajammer.domain.model.MetadataReplacementPlan
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ImageMetadataProcessorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var fileRepository: FileRepository
    private lateinit var processor: ImageMetadataProcessor

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        fileRepository = FileRepository(context)
        processor = ImageMetadataProcessor(fileRepository)
    }

    private fun createTestJpegWithExif(): File {
        val file = tempFolder.newFile("test_source.jpg")
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }

        val exif = ExifInterface(file.absolutePath)
        exif.setAttribute(ExifInterface.TAG_MAKE, "RealCameraMake")
        exif.setAttribute(ExifInterface.TAG_MODEL, "RealCameraModel")
        exif.setAttribute(ExifInterface.TAG_DATETIME, "2022:05:10 14:30:00")
        exif.setAttribute(ExifInterface.TAG_SOFTWARE, "SecretFirmware v1.0")
        exif.setAttribute(ExifInterface.TAG_USER_COMMENT, "Secret notes about this photo")
        exif.setLatLong(48.8584, 2.2945) // Eiffel Tower coordinates
        exif.saveAttributes()

        return file
    }

    @Test
    fun testRemoveMetadataStripsExifTagsAndGps() {
        val originalFile = createTestJpegWithExif()
        val originalUri = originalFile.toUri()

        // Verify original file contains EXIF
        val originalExif = ExifInterface(originalFile.absolutePath)
        assertEquals("RealCameraMake", originalExif.getAttribute(ExifInterface.TAG_MAKE))
        assertNotNull(originalExif.latLong)

        // Strip metadata
        val cleanedFile = processor.removeMetadata(
            inputUri = originalUri,
            keepOrientation = true,
            mimeType = "image/jpeg"
        )

        assertTrue("Cleaned file should exist", cleanedFile.exists())
        assertTrue("Cleaned file should have content", cleanedFile.length() > 0)

        // Verify EXIF metadata was stripped
        val cleanedExif = ExifInterface(cleanedFile.absolutePath)
        assertNull("Camera Make should be stripped", cleanedExif.getAttribute(ExifInterface.TAG_MAKE))
        assertNull("Camera Model should be stripped", cleanedExif.getAttribute(ExifInterface.TAG_MODEL))
        assertNull("Software should be stripped", cleanedExif.getAttribute(ExifInterface.TAG_SOFTWARE))
        assertNull("UserComment should be stripped", cleanedExif.getAttribute(ExifInterface.TAG_USER_COMMENT))
        assertNull("GPS Coordinates should be stripped", cleanedExif.latLong)

        // Intrinsic dimensions should be preserved
        assertNotNull("Image width should be preserved", cleanedExif.getAttribute(ExifInterface.TAG_IMAGE_WIDTH))
        assertNotNull("Image length should be preserved", cleanedExif.getAttribute(ExifInterface.TAG_IMAGE_LENGTH))
    }

    @Test
    fun testPoisonMetadataReplacesOriginalMetadataWithPlan() {
        val originalFile = createTestJpegWithExif()
        val originalUri = originalFile.toUri()

        val fakePlan = MetadataReplacementPlan(
            make = "SpoofedBrand",
            model = "SpoofedModelX",
            dateTime = "2025:12:31 23:59:59",
            latitude = -33.8568, // Sydney Opera House
            longitude = 151.2153,
            software = "MetaJammer 0.4.0",
            imageDescription = "A completely fake description",
            userComment = "Poisoned Comment"
        )

        val poisonedFile = processor.poisonMetadata(
            inputUri = originalUri,
            plan = fakePlan,
            mimeType = "image/jpeg"
        )

        assertTrue(poisonedFile.exists())

        val poisonedExif = ExifInterface(poisonedFile.absolutePath)
        assertEquals("SpoofedBrand", poisonedExif.getAttribute(ExifInterface.TAG_MAKE))
        assertEquals("SpoofedModelX", poisonedExif.getAttribute(ExifInterface.TAG_MODEL))
        assertEquals("MetaJammer 0.4.0", poisonedExif.getAttribute(ExifInterface.TAG_SOFTWARE))
        assertEquals("A completely fake description", poisonedExif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION))

        val coords = poisonedExif.latLong
        assertNotNull("GPS coordinates should be present", coords)
        assertEquals(fakePlan.latitude, coords!![0], 0.001)
        assertEquals(fakePlan.longitude, coords[1], 0.001)
    }

    @Test
    fun testStripJpegMarkersRemovesAppMarkers() {
        // Construct a minimal valid JPEG with SOI, APP1 marker, and EOI
        val app1Payload = "Exif\u0000\u0000TestPayload".toByteArray(Charsets.ISO_8859_1)
        val app1Length = app1Payload.size + 2
        val jpegBytes = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), // SOI
            0xFF.toByte(), 0xE1.toByte(), // APP1 marker
            ((app1Length shr 8) and 0xFF).toByte(),
            (app1Length and 0xFF).toByte()
        ) + app1Payload + byteArrayOf(
            0xFF.toByte(), 0xD9.toByte()  // EOI
        )

        val stripped = ImageMetadataProcessor.stripJpegMarkers(jpegBytes)
        assertNotNull("Stripped output should not be null", stripped)

        // Verify APP1 marker (0xFF, 0xE1) was removed
        var hasApp1 = false
        for (i in 0 until stripped!!.size - 1) {
            if (stripped[i] == 0xFF.toByte() && stripped[i + 1] == 0xE1.toByte()) {
                hasApp1 = true
            }
        }
        assertFalse("APP1 marker must be removed from JPEG", hasApp1)
        // SOI and EOI must remain
        assertEquals(0xFF.toByte(), stripped[0])
        assertEquals(0xD8.toByte(), stripped[1])
        assertEquals(0xFF.toByte(), stripped[stripped.size - 2])
        assertEquals(0xD9.toByte(), stripped[stripped.size - 1])
    }
}
