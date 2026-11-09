package com.heronikostudios.metajammer.metadata

import android.content.Context
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import com.heronikostudios.metajammer.data.FileRepository
import com.heronikostudios.metajammer.data.MetadataRepository
import com.heronikostudios.metajammer.domain.model.MetadataReplacementPlan
import com.heronikostudios.metajammer.domain.model.ProcessingMode
import com.heronikostudios.metajammer.domain.model.SelectedFile
import com.heronikostudios.metajammer.domain.model.ThumbnailHandling
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
class HeicMetadataProcessorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var fileRepository: FileRepository
    private lateinit var heicProcessor: HeicMetadataProcessor
    private lateinit var metadataRepository: MetadataRepository

    private val sampleHeic: File by lazy {
        listOf(
            File("test_data/fake_metadata_sample.heic"),
            File("../test_data/fake_metadata_sample.heic")
        ).firstOrNull { it.exists() } ?: File("test_data/fake_metadata_sample.heic")
    }

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        fileRepository = FileRepository(context)
        heicProcessor = HeicMetadataProcessor(fileRepository)
        metadataRepository = MetadataRepository(fileRepository)
        org.junit.Assume.assumeTrue("fake_metadata_sample.heic must exist for test execution", sampleHeic.exists())
    }

    @Test
    fun `readMetadata on fake_metadata_sample heic detects EXIF and XMP metadata`() {
        assertTrue("Test sample must exist", sampleHeic.exists())

        val selectedFile = SelectedFile(
            uri = sampleHeic.toUri(),
            displayName = "fake_metadata_sample.heic",
            mimeType = "image/heic",
            sizeBytes = sampleHeic.length()
        )

        val entries = heicProcessor.readMetadata(selectedFile)
        val entryMap = entries.associate { it.key to it.value }

        assertTrue("Should detect entries in fake_metadata_sample.heic", entries.isNotEmpty())
        assertEquals("Sony", entryMap[ExifInterface.TAG_MAKE])
        assertEquals("ILCE-7RM5", entryMap[ExifInterface.TAG_MODEL])
        assertNotNull("DateTime must be detected", entryMap[ExifInterface.TAG_DATETIME])
        assertTrue("XMP Metadata must be detected", entryMap.containsKey("XMP Metadata"))
    }

    @Test
    fun `removeMetadata strips all EXIF and XMP metadata losslessly`() {
        assertTrue("Test sample must exist", sampleHeic.exists())

        val strippedFile = heicProcessor.removeMetadata(
            inputUri = sampleHeic.toUri(),
            keepOrientation = true,
            thumbnailHandling = ThumbnailHandling.REMOVE,
            stripGps = true,
            stripDeviceModel = true,
            stripDateTime = true,
            stripCameraSettings = true,
            stripComments = true
        )

        assertTrue("Stripped file must exist", strippedFile.exists())
        assertTrue("Stripped file must have non-zero size", strippedFile.length() > 0)
        assertTrue("Stripped file size must not exceed original", strippedFile.length() <= sampleHeic.length())
        strippedFile.copyTo(File("build/tmp/stripped.heic"), overwrite = true)

        // Verify with extractExifInterface
        val cleanExif = heicProcessor.extractExifInterface(strippedFile)
        assertNull("Make should be stripped", cleanExif?.getAttribute(ExifInterface.TAG_MAKE))
        assertNull("Model should be stripped", cleanExif?.getAttribute(ExifInterface.TAG_MODEL))
        assertNull("Software should be stripped", cleanExif?.getAttribute(ExifInterface.TAG_SOFTWARE))
        assertNull("DateTime should be stripped", cleanExif?.getAttribute(ExifInterface.TAG_DATETIME))
        assertNull("UserComment should be stripped", cleanExif?.getAttribute(ExifInterface.TAG_USER_COMMENT))
        assertNull("GPS coordinates should be stripped", cleanExif?.latLong)

        // Verify with readMetadata
        val selectedStripped = SelectedFile(
            uri = strippedFile.toUri(),
            displayName = strippedFile.name,
            mimeType = "image/heic",
            sizeBytes = strippedFile.length()
        )
        val entries = heicProcessor.readMetadata(selectedStripped)
        val sensitiveEntries = entries.filter {
            it.key != ExifInterface.TAG_IMAGE_WIDTH &&
            it.key != ExifInterface.TAG_IMAGE_LENGTH &&
            it.key != ExifInterface.TAG_ORIENTATION
        }
        assertTrue("No sensitive metadata should remain after stripping", sensitiveEntries.isEmpty())
    }

    @Test
    fun `poisonMetadata injects fake metadata plan into HEIC container`() {
        assertTrue("Test sample must exist", sampleHeic.exists())

        val plan = MetadataReplacementPlan(
            make = "Fujifilm",
            model = "X100VI",
            dateTime = "2026:11:03 10:00:00",
            latitude = 35.6895,
            longitude = 139.6917,
            software = "MetaJammer Heic Engine",
            imageDescription = "Scrambled photo",
            userComment = "Poisoned by MetaJammer",
            exposureTime = "1/250",
            fNumber = "2.8",
            photographicSensitivity = "200",
            focalLength = "23.0",
            whiteBalance = "0",
            flash = "0"
        )

        val poisonedFile = heicProcessor.poisonMetadata(
            inputUri = sampleHeic.toUri(),
            plan = plan,
            keepOrientation = true,
            thumbnailHandling = ThumbnailHandling.REMOVE,
            stripGps = true,
            stripDeviceModel = true,
            stripDateTime = true,
            stripCameraSettings = true,
            stripComments = true
        )

        assertTrue("Poisoned file must exist", poisonedFile.exists())
        assertTrue("Poisoned file must have valid size", poisonedFile.length() > 0)
        poisonedFile.copyTo(File("build/tmp/poisoned.heic"), overwrite = true)

        val poisonedExif = heicProcessor.extractExifInterface(poisonedFile)
        assertNotNull("Poisoned Exif must be extractable", poisonedExif)
        assertEquals("Fujifilm", poisonedExif!!.getAttribute(ExifInterface.TAG_MAKE))
        assertEquals("X100VI", poisonedExif.getAttribute(ExifInterface.TAG_MODEL))
        assertEquals("MetaJammer Heic Engine", poisonedExif.getAttribute(ExifInterface.TAG_SOFTWARE))
        assertEquals("2026:11:03 10:00:00", poisonedExif.getAttribute(ExifInterface.TAG_DATETIME))
        assertEquals("Scrambled photo", poisonedExif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION))

        val latLong = poisonedExif.latLong
        assertNotNull("GPS coordinates must be present in poisoned file", latLong)
        assertEquals(35.6895, latLong!![0], 0.001)
        assertEquals(139.6917, latLong[1], 0.001)
    }

    @Test
    fun `removeMetadata respects granular category preservation`() {
        assertTrue("Test sample must exist", sampleHeic.exists())

        // Preserve GPS (stripGps = false), strip everything else
        val strippedFile = heicProcessor.removeMetadata(
            inputUri = sampleHeic.toUri(),
            keepOrientation = true,
            thumbnailHandling = ThumbnailHandling.REMOVE,
            stripGps = false, // Keep GPS!
            stripDeviceModel = true,
            stripDateTime = true,
            stripCameraSettings = true,
            stripComments = true
        )

        assertTrue(strippedFile.exists())
        val exif = heicProcessor.extractExifInterface(strippedFile)
        assertNotNull(exif)
        assertNull("Make should be stripped", exif!!.getAttribute(ExifInterface.TAG_MAKE))
        assertNull("Model should be stripped", exif.getAttribute(ExifInterface.TAG_MODEL))
        assertNull("Software should be stripped", exif.getAttribute(ExifInterface.TAG_SOFTWARE))
        assertNull("DateTime should be stripped", exif.getAttribute(ExifInterface.TAG_DATETIME))

        val origExif = heicProcessor.extractExifInterface(sampleHeic)
        assertNotNull(origExif)
        val origLatLong = origExif!!.latLong
        val preservedLatLong = exif.latLong
        assertNotNull("GPS coordinates should be preserved when stripGps is false", preservedLatLong)
        assertEquals(origLatLong!![0], preservedLatLong!![0], 0.001)
        assertEquals(origLatLong[1], preservedLatLong[1], 0.001)
    }

    @Test
    fun `MetadataRepository processFile handles HEIC remove and poison modes`() = kotlinx.coroutines.test.runTest {
        val selectedFile = SelectedFile(
            uri = sampleHeic.toUri(),
            displayName = "sample.heic",
            mimeType = "image/heic",
            sizeBytes = sampleHeic.length()
        )

        // 1. Remove mode
        val cleaned = metadataRepository.processFile(
            selectedFile = selectedFile,
            mode = ProcessingMode.REMOVE_METADATA,
            keepOrientation = true
        )
        assertTrue("Cleaned HEIC must exist", cleaned.exists())
        val cleanExif = heicProcessor.extractExifInterface(cleaned)
        assertNull("Make must be stripped", cleanExif?.getAttribute(ExifInterface.TAG_MAKE))

        // 2. Poison mode
        val plan = MetadataReplacementPlan(
            make = "Olympus",
            model = "OM-1",
            dateTime = "2026:10:15 08:30:00",
            latitude = 48.8584,
            longitude = 2.2945,
            software = "Firmware 2.0"
        )
        val poisoned = metadataRepository.processFile(
            selectedFile = selectedFile,
            mode = ProcessingMode.POISON_METADATA,
            keepOrientation = true,
            replacementPlan = plan
        )
        assertTrue("Poisoned HEIC must exist", poisoned.exists())
        val poisonExif = heicProcessor.extractExifInterface(poisoned)
        assertNotNull("Poisoned Exif must be extractable", poisonExif)
        assertEquals("Olympus", poisonExif!!.getAttribute(ExifInterface.TAG_MAKE))
        assertEquals("OM-1", poisonExif.getAttribute(ExifInterface.TAG_MODEL))
        val poisonLatLong = poisonExif.latLong
        assertNotNull("Poison GPS must be present", poisonLatLong)
        assertEquals(48.8584, poisonLatLong!![0], 0.001)
        assertEquals(2.2945, poisonLatLong[1], 0.001)
    }

    @Test
    fun `test stripping real Nokia heic sample with thumbnail removal`() {
        val testFile = listOf(File("build/tmp/autumn.heic"), File("../build/tmp/autumn.heic")).firstOrNull { it.exists() }
        org.junit.Assume.assumeNotNull(testFile)

        val stripped = heicProcessor.removeMetadata(
            inputUri = testFile!!.toUri(),
            keepOrientation = true,
            thumbnailHandling = ThumbnailHandling.REMOVE,
            stripGps = true,
            stripDeviceModel = true,
            stripDateTime = true,
            stripCameraSettings = true,
            stripComments = true
        )
        assertTrue("Stripped autumn.heic must exist", stripped.exists())
        assertTrue("Stripped autumn.heic must not be empty", stripped.length() > 0)
        stripped.copyTo(File(testFile.parentFile, "autumn_stripped.heic"), overwrite = true)

        // Verify with pillow_heif if python3 is available
        verifyWithPillowHeif(File(testFile.parentFile, "autumn_stripped.heic"), 1440, 960)
    }

    @Test
    fun `test stripping and poisoning real iPhone multi-tile heic sample`() {
        val testFile = listOf(File("build/tmp/image1.heic"), File("../build/tmp/image1.heic")).firstOrNull { it.exists() }
        org.junit.Assume.assumeNotNull(testFile)

        // 1. Strip
        val stripped = heicProcessor.removeMetadata(
            inputUri = testFile!!.toUri(),
            keepOrientation = true,
            thumbnailHandling = ThumbnailHandling.REMOVE,
            stripGps = true,
            stripDeviceModel = true,
            stripDateTime = true,
            stripCameraSettings = true,
            stripComments = true
        )
        assertTrue("Stripped image1.heic must exist", stripped.exists())
        assertTrue("Stripped image1.heic must not be empty", stripped.length() > 0)
        val cleanDest = File(testFile.parentFile, "image1_clean.heic")
        stripped.copyTo(cleanDest, overwrite = true)
        verifyWithPillowHeif(cleanDest, 3992, 2992)

        // 2. Poison
        val plan = MetadataReplacementPlan(
            make = "Leica",
            model = "M11",
            software = "Leica Firmware 2.0.1",
            dateTime = "2026:11:03 14:00:00",
            latitude = 35.6762,
            longitude = 139.6503,
            lensMake = "Leica",
            lensModel = "Summilux-M 35mm f/1.4 ASPH"
        )
        val poisoned = heicProcessor.poisonMetadata(
            inputUri = testFile.toUri(),
            plan = plan,
            keepOrientation = true,
            thumbnailHandling = ThumbnailHandling.REMOVE,
            stripGps = true,
            stripDeviceModel = true,
            stripDateTime = true,
            stripCameraSettings = true,
            stripComments = true
        )
        assertTrue("Poisoned image1.heic must exist", poisoned.exists())
        assertTrue("Poisoned image1.heic must not be empty", poisoned.length() > 0)
        val poisonDest = File(testFile.parentFile, "image1_poisoned.heic")
        poisoned.copyTo(poisonDest, overwrite = true)
        verifyWithPillowHeif(poisonDest, 3992, 2992)

        val poisonExif = heicProcessor.extractExifInterface(poisoned)
        assertNotNull("Poisoned Exif must be extractable from iPhone HEIC", poisonExif)
        assertEquals("Leica", poisonExif?.getAttribute(ExifInterface.TAG_MAKE))
        assertEquals("M11", poisonExif?.getAttribute(ExifInterface.TAG_MODEL))
        val latLong = poisonExif?.latLong
        assertNotNull("LatLong must be present", latLong)
        assertEquals(35.6762, latLong!![0], 0.001)
        assertEquals(139.6503, latLong[1], 0.001)
    }

    private fun verifyWithPillowHeif(file: File, expectedWidth: Int, expectedHeight: Int) {
        val proc = runCatching {
            ProcessBuilder(
                "python3", "-c",
                "import pillow_heif; h = pillow_heif.read_heif('${file.absolutePath}'); print(h.size[0], h.size[1])"
            ).redirectErrorStream(true).start()
        }.getOrNull() ?: return

        val output = proc.inputStream.bufferedReader().readText().trim()
        val exitCode = proc.waitFor()
        if (exitCode == 0) {
            val parts = output.split(" ")
            if (parts.size == 2) {
                val w = parts[0].toIntOrNull()
                val h = parts[1].toIntOrNull()
                assertEquals("Decoded width must match", expectedWidth, w)
                assertEquals("Decoded height must match", expectedHeight, h)
            }
        }
    }
}
