package com.heronikostudios.metajammer.data

import android.content.Context
import androidx.core.net.toUri
import com.heronikostudios.metajammer.domain.model.ProcessingMode
import com.heronikostudios.metajammer.domain.model.SelectedFile
import com.heronikostudios.metajammer.metadata.OggMetadataReader
import com.heronikostudios.metajammer.metadata.SvgMetadataProcessor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
class MetadataRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var fileRepository: FileRepository
    private lateinit var metadataRepository: MetadataRepository

    private val sampleSvgFile = listOf(
        File("test_data/fake_metadata_sample.svg"),
        File("../test_data/fake_metadata_sample.svg")
    ).firstOrNull { it.exists() } ?: File("test_data/fake_metadata_sample.svg")

    private val sampleOggFile = listOf(
        File("test_data/fake_metadata_sample.ogg"),
        File("../test_data/fake_metadata_sample.ogg")
    ).firstOrNull { it.exists() } ?: File("test_data/fake_metadata_sample.ogg")

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        fileRepository = FileRepository(context)
        metadataRepository = MetadataRepository(fileRepository)
    }

    @Test
    fun `readMetadata correctly routes SVG and extracts Dublin Core and editor tags`() = runTest {
        assertTrue("Sample SVG must exist", sampleSvgFile.exists())
        val selectedFile = SelectedFile(
            uri = sampleSvgFile.toUri(),
            displayName = "fake_metadata_sample.svg",
            mimeType = "image/svg+xml",
            sizeBytes = sampleSvgFile.length()
        )

        val entries = metadataRepository.readMetadata(selectedFile)
        assertFalse("Metadata entries must not be empty for SVG sample", entries.isEmpty())

        val entriesMap = entries.associate { it.key to it.value }
        assertEquals("Classified Operations & Metadata Analysis 2026", entriesMap["Title"])
        assertEquals("Dr. Jane Doe & Director Alex Vance", entriesMap["Creator"])
        assertEquals("2026-10-02T12:52:03+02:00", entriesMap["Date"])
        assertEquals("Geneva, Switzerland (46.204391 N, 6.143158 E)", entriesMap["Location"])
        assertTrue(entriesMap["Software"]?.contains("Inkscape") == true)
        assertEquals("classified_operations_2026.svg", entriesMap["Document Name"])
        assertEquals("11 comment(s)", entriesMap["Comments"])
    }

    @Test
    fun `readMetadata correctly routes OGG and extracts Vorbis comment tags`() = runTest {
        assertTrue("Sample OGG must exist", sampleOggFile.exists())
        val selectedFile = SelectedFile(
            uri = sampleOggFile.toUri(),
            displayName = "fake_metadata_sample.ogg",
            mimeType = "audio/ogg",
            sizeBytes = sampleOggFile.length()
        )

        val entries = metadataRepository.readMetadata(selectedFile)
        assertFalse("Metadata entries must not be empty for OGG sample", entries.isEmpty())

        val entriesMap = entries.associate { it.key to it.value }
        assertEquals("Classified Audio Intercept 2026", entriesMap["Title"])
        assertEquals("Dr. Jane Doe", entriesMap["Artist"])
        assertEquals("Project Cerberus Audio Vault", entriesMap["Album"])
        assertEquals("2026-10-02", entriesMap["Date"])
        assertEquals("Geneva, Switzerland", entriesMap["Location"])
        assertTrue(entriesMap["GPS Coordinates"]?.contains("46.204391 N") == true)
        assertEquals("Acme Cybernetics Security Ltd.", entriesMap["Organization"])
        assertEquals("Creative Commons Attribution-NonCommercial 4.0 International", entriesMap["License"])
        assertEquals("Present (Vorbis Picture Block)", entriesMap["Embedded Picture"])
        assertEquals("Present (XMP Metadata)", entriesMap["XMP Packet"])
    }

    @Test
    fun `processFile in remove mode strips all metadata from SVG`() = runTest {
        assertTrue("Sample SVG must exist", sampleSvgFile.exists())
        val selectedFile = SelectedFile(
            uri = sampleSvgFile.toUri(),
            displayName = "fake_metadata_sample.svg",
            mimeType = "image/svg+xml",
            sizeBytes = sampleSvgFile.length()
        )

        val resultFile = metadataRepository.processFile(
            selectedFile = selectedFile,
            mode = ProcessingMode.REMOVE_METADATA,
            keepOrientation = false
        )

        assertNotNull("Result file must be returned", resultFile)
        assertTrue("Result file must exist", resultFile.exists())

        val cleanedSvg = resultFile.readText()
        assertFalse("Cleaned SVG must not contain title tag", cleanedSvg.contains("<title>"))
        assertFalse("Cleaned SVG must not contain metadata tag", cleanedSvg.contains("<metadata"))
        assertFalse("Cleaned SVG must not contain comments", cleanedSvg.contains("<!--"))
        assertFalse("Cleaned SVG must not contain dc:creator", cleanedSvg.contains("dc:creator"))
        assertFalse("Cleaned SVG must not contain inkscape:version attribute", cleanedSvg.contains("inkscape:version=\""))
        assertTrue("Cleaned SVG must preserve visual rect elements", cleanedSvg.contains("<rect"))
    }

    @Test
    fun `processFile in remove mode strips all metadata from OGG`() = runTest {
        assertTrue("Sample OGG must exist", sampleOggFile.exists())
        val selectedFile = SelectedFile(
            uri = sampleOggFile.toUri(),
            displayName = "fake_metadata_sample.ogg",
            mimeType = "audio/ogg",
            sizeBytes = sampleOggFile.length()
        )

        val resultFile = metadataRepository.processFile(
            selectedFile = selectedFile,
            mode = ProcessingMode.REMOVE_METADATA,
            keepOrientation = false
        )

        assertNotNull("Result file must be returned", resultFile)
        assertTrue("Result file must exist", resultFile.exists())
        assertTrue("Result file must be smaller than original", resultFile.length() < sampleOggFile.length())

        val remainingEntries = resultFile.inputStream().use { OggMetadataReader.readMetadata(it) }
        val remainingMap = remainingEntries.associate { it.key to it.value }

        assertFalse("Cleaned OGG must not have Title", remainingMap.containsKey("Title"))
        assertFalse("Cleaned OGG must not have Artist", remainingMap.containsKey("Artist"))
        assertFalse("Cleaned OGG must not have Location", remainingMap.containsKey("Location"))
        assertFalse("Cleaned OGG must not have GPS Coordinates", remainingMap.containsKey("GPS Coordinates"))
        assertFalse("Cleaned OGG must not have Embedded Picture", remainingMap.containsKey("Embedded Picture"))
    }
}
