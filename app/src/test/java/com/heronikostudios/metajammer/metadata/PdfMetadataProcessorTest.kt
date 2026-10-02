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
        assertTrue(entries.any { it.key == "Creator" && it.value == "Adobe InDesign 2023" })
        assertFalse(entries.any { it.key == "Page Count" })
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
            assertNull("PageLabels should be null", doc.documentCatalog.pageLabels)
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

    @Test
    fun testRemoveMetadataStripsPageLabelsAndPieceInfo() = runTest {
        val file = tempFolder.newFile("test_pagelabels.pdf")
        PDDocument().use { doc ->
            doc.addPage(PDPage())
            doc.addPage(PDPage())
            val catalog = doc.documentCatalog
            // Add PageLabels dictionary
            val pageLabelsDict = com.tom_roush.pdfbox.cos.COSDictionary()
            catalog.cosObject.setItem(com.tom_roush.pdfbox.cos.COSName.getPDFName("PageLabels"), pageLabelsDict)
            // Add PieceInfo dictionary
            val pieceInfoDict = com.tom_roush.pdfbox.cos.COSDictionary()
            catalog.cosObject.setItem(com.tom_roush.pdfbox.cos.COSName.getPDFName("PieceInfo"), pieceInfoDict)
            doc.save(file)
        }

        // Verify readMetadata detects Page Labels
        val entriesBefore = processor.readMetadata(file.toUri())
        assertTrue("Page Labels should be detected", entriesBefore.any { it.key == "Page Labels" })

        // Clean
        val cleanFile = processor.removeMetadata(file.toUri())
        PDDocument.load(cleanFile).use { doc ->
            val catalog = doc.documentCatalog
            assertNull("pageLabels property should be null", catalog.pageLabels)
            assertFalse("PageLabels COS entry should be removed", catalog.cosObject.containsKey(com.tom_roush.pdfbox.cos.COSName.getPDFName("PageLabels")))
            assertFalse("PieceInfo COS entry should be removed", catalog.cosObject.containsKey(com.tom_roush.pdfbox.cos.COSName.getPDFName("PieceInfo")))
        }

        // Verify readMetadata on clean file has zero entries
        val entriesAfter = processor.readMetadata(cleanFile.toUri())
        assertTrue("Cleaned PDF must have zero metadata entries", entriesAfter.isEmpty())
    }

    @Test
    fun testReadAndRemoveTimestamps() = runTest {
        val file = tempFolder.newFile("test_dates.pdf")
        val now = java.util.Calendar.getInstance()
        PDDocument().use { doc ->
            doc.addPage(PDPage())
            val info = PDDocumentInformation().apply {
                creationDate = now
                modificationDate = now
                title = "Dated Document"
            }
            doc.documentInformation = info
            doc.save(file)
        }

        val entries = processor.readMetadata(file.toUri())
        assertTrue("Creation Date should be detected", entries.any { it.key == "Creation Date" })
        assertTrue("Modification Date should be detected", entries.any { it.key == "Modification Date" })

        val cleanFile = processor.removeMetadata(file.toUri())
        PDDocument.load(cleanFile).use { doc ->
            assertNull("Creation date must be wiped", doc.documentInformation.creationDate)
            assertNull("Modification date must be wiped", doc.documentInformation.modificationDate)
        }
    }

    @Test
    fun testPoisonMetadataStripsPageLabelsAndPieceInfo() = runTest {
        val file = tempFolder.newFile("test_poison_pagelabels.pdf")
        PDDocument().use { doc ->
            doc.addPage(PDPage())
            val catalog = doc.documentCatalog
            catalog.cosObject.setItem(com.tom_roush.pdfbox.cos.COSName.getPDFName("PageLabels"), com.tom_roush.pdfbox.cos.COSDictionary())
            catalog.cosObject.setItem(com.tom_roush.pdfbox.cos.COSName.getPDFName("PieceInfo"), com.tom_roush.pdfbox.cos.COSDictionary())
            doc.save(file)
        }

        val plan = MetadataReplacementPlan(
            pdfTitle = "Poison Title",
            author = "Poison Author"
        )
        val poisonedFile = processor.poisonMetadata(file.toUri(), plan)

        PDDocument.load(poisonedFile).use { doc ->
            val catalog = doc.documentCatalog
            assertNull(catalog.pageLabels)
            assertFalse(catalog.cosObject.containsKey(com.tom_roush.pdfbox.cos.COSName.getPDFName("PageLabels")))
            assertFalse(catalog.cosObject.containsKey(com.tom_roush.pdfbox.cos.COSName.getPDFName("PieceInfo")))
            assertEquals("Poison Title", doc.documentInformation.title)
            assertEquals("Poison Author", doc.documentInformation.author)
        }
    }

    @Test
    fun testReadMetadataOnCorruptPdfReturnsEmptyListSafely() = runTest {
        val corruptFile = tempFolder.newFile("corrupt.pdf")
        corruptFile.writeBytes(byteArrayOf(0, 1, 2, 3, 4, 5))

        val entries = processor.readMetadata(corruptFile.toUri())
        assertTrue("Corrupt PDF should result in empty metadata list without throwing", entries.isEmpty())
    }

    @Test
    fun testRemoveMetadataStripsWatermarkAnnotations() = runTest {
        val file = tempFolder.newFile("test_watermark.pdf")
        PDDocument().use { doc ->
            val page = PDPage()
            doc.addPage(page)

            val stampAnnotation = com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationRubberStamp()
            stampAnnotation.cosObject.setName(com.tom_roush.pdfbox.cos.COSName.SUBTYPE, "Watermark")
            page.annotations = listOf(stampAnnotation)
            doc.save(file)
        }

        val cleanFile = processor.removeMetadata(file.toUri())
        PDDocument.load(cleanFile).use { doc ->
            val page = doc.getPage(0)
            assertTrue("Watermark/Stamp annotations must be stripped", page.annotations.isEmpty())
        }
    }
}

