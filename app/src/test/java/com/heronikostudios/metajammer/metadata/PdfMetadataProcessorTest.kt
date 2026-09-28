package com.heronikostudios.metajammer.metadata

import android.content.Context
import androidx.core.net.toUri
import com.heronikostudios.metajammer.data.FileRepository
import com.heronikostudios.metajammer.domain.model.MetadataReplacementPlan
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.PDPage
import kotlinx.coroutines.test.runTest
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
class PdfMetadataProcessorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var fileRepository: FileRepository
    private lateinit var processor: PdfMetadataProcessor

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        fileRepository = FileRepository(context)
        processor = PdfMetadataProcessor(context, fileRepository)
    }

    private fun createTestPdfWithMetadata(): File {
        val file = tempFolder.newFile("test_source.pdf")
        PDDocument().use { doc ->
            doc.addPage(PDPage())
            val info = PDDocumentInformation().apply {
                title = "Original Confidential Title"
                author = "Real Author Name"
                subject = "Internal Company Memo"
                creator = "Adobe InDesign 2023"
                producer = "Internal PDF Engine"
                keywords = "Confidential, Internal, ProjectX"
            }
            doc.documentInformation = info
            doc.save(file)
        }
        return file
    }

    @Test
    fun testReadMetadataReturnsEntries() = runTest {
        val file = createTestPdfWithMetadata()
        val entries = processor.readMetadata(file.toUri())

        assertTrue(entries.any { it.key == "Title" && it.value == "Original Confidential Title" })
        assertTrue(entries.any { it.key == "Author" && it.value == "Real Author Name" })
        assertTrue(entries.any { it.key == "Subject" && it.value == "Internal Company Memo" })
        assertTrue(entries.any { it.key == "Page Count" && it.value == "1" })
    }

    @Test
    fun testRemoveMetadataWipesDocumentInformation() = runTest {
        val file = createTestPdfWithMetadata()
        val cleanFile = processor.removeMetadata(file.toUri())

        assertTrue(cleanFile.exists())
        assertTrue(cleanFile.length() > 0)

        PDDocument.load(cleanFile).use { doc ->
            val info = doc.documentInformation
            assertNull("Title should be wiped", info.title)
            assertNull("Author should be wiped", info.author)
            assertNull("Subject should be wiped", info.subject)
            assertNull("Creator should be wiped", info.creator)
            assertNull("Keywords should be wiped", info.keywords)
            assertNull("Producer should be wiped or default", info.producer)
            assertNull("Catalog XMP metadata should be null", doc.documentCatalog.metadata)
        }
    }

    @Test
    fun testPoisonMetadataReplacesWithFakeInformation() = runTest {
        val file = createTestPdfWithMetadata()
        val fakePlan = MetadataReplacementPlan(
            pdfTitle = "Public Presentation",
            author = "Anonymous Contributor",
            subject = "General Topic",
            creator = "MetaJammer PDF Sanitizer",
            producer = "Open Source Producer",
            keywords = "Public, Document"
        )

        val poisonedFile = processor.poisonMetadata(file.toUri(), fakePlan)
        assertTrue(poisonedFile.exists())

        PDDocument.load(poisonedFile).use { doc ->
            val info = doc.documentInformation
            assertEquals("Public Presentation", info.title)
            assertEquals("Anonymous Contributor", info.author)
            assertEquals("General Topic", info.subject)
            assertEquals("MetaJammer PDF Sanitizer", info.creator)
            assertEquals("Open Source Producer", info.producer)
            assertEquals("Public, Document", info.keywords)
            assertNull("Catalog XMP metadata should be null", doc.documentCatalog.metadata)
        }
    }
}
