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

    @Test
    fun testPagedOut008HiddenAnnotationDetectionAndStripping() = runTest {
        val file = listOf(File("test_data/PagedOut_008.pdf"), File("../test_data/PagedOut_008.pdf")).firstOrNull { it.exists() }
        assertNotNull("test_data/PagedOut_008.pdf should exist", file)
        val targetFile = file!!

        // 1. readMetadata should detect annotations, watermarks, and authors
        val originalLinks = PDDocument.load(targetFile).use { doc ->
            doc.pages.sumOf { it.annotations?.count { a -> a.subtype == "Link" } ?: 0 }
        }
        val originalNonLinks = PDDocument.load(targetFile).use { doc ->
            doc.pages.sumOf { it.annotations?.count { a -> a.subtype != "Link" } ?: 0 }
        }
        println("Original links: $originalLinks, original non-links: $originalNonLinks")

        val entries = processor.readMetadata(targetFile.toUri())
        val annotationsEntry = entries.find { it.key == "Annotations" }
        assertNotNull("Annotations entry should be present", annotationsEntry)
        assertTrue("Annotations should report hidden annotation: ${annotationsEntry!!.value}", annotationsEntry.value.contains("hidden"))
        assertTrue("Annotations should report watermarks: ${annotationsEntry.value}", annotationsEntry.value.contains("watermark/stamp"))
        val authorEntry = entries.find { it.key == "Annotation Author" }
        assertNotNull("Annotation Author entry should be present", authorEntry)
        assertTrue("Annotation Author should contain PDF-XChange Editor: ${authorEntry!!.value}", authorEntry.value.contains("PDF-XChange Editor"))
        assertTrue("Annotation Author should contain Lenovo: ${authorEntry.value}", authorEntry.value.contains("Lenovo"))

        // 2. removeMetadata with stripAnnotations = true should strip user annotations & watermarks while preserving 471 links and trial stamps
        val cleanFile = processor.removeMetadata(targetFile.toUri(), stripAnnotations = true, stripComments = true)
        assertTrue(cleanFile.exists())
        PDDocument.load(cleanFile).use { doc ->
            var totalAnnotations = 0
            var hiddenCount = 0
            var textSubtypeCount = 0
            for (page in doc.pages) {
                for (annot in page.annotations) {
                    totalAnnotations++
                    val flags = annot.annotationFlags
                    if ((flags and 2) != 0 || (flags and 1) != 0 || (flags and 32) != 0) {
                        hiddenCount++
                    }
                    if (annot.subtype == "Text") {
                        textSubtypeCount++
                    }
                }
            }
            assertEquals("Navigation links should be preserved", originalLinks, totalAnnotations)
            assertEquals("Zero hidden annotations should remain", 0, hiddenCount)
            assertEquals("Zero text subtype annotations should remain", 0, textSubtypeCount)

            // Verify user watermarks (e.g. "Watermark Sample") were stripped while trial stamps remain
            var userWatermarkCount = 0
            var trialStampCount = 0
            for (page in doc.pages) {
                val res = page.resources ?: continue
                for (cosName in res.xObjectNames) {
                    val xobj = runCatching { res.getXObject(cosName) }.getOrNull()
                    if (xobj is com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject) {
                        val text = runCatching {
                            String(xobj.contentStream.createInputStream().readBytes(), Charsets.ISO_8859_1)
                        }.getOrDefault("")
                        if (text.contains("Watermark Sample", ignoreCase = true)) {
                            userWatermarkCount++
                        }
                        if (text.contains("BUY NOW", ignoreCase = true)) {
                            trialStampCount++
                        }
                    }
                }
            }
            assertEquals("Zero user watermarks should remain", 0, userWatermarkCount)
            assertTrue("Commercial trial stamps should be preserved: $trialStampCount", trialStampCount > 0)
        }

        // Cleaned PDF should no longer report Annotation Author
        val cleanEntries = processor.readMetadata(cleanFile.toUri())
        assertFalse("Annotation Author entry should not be present on clean file", cleanEntries.any { it.key == "Annotation Author" })

        // 3. removeMetadata with stripAnnotations = false and stripComments = false should preserve the hidden annotation
        val preservedFile = processor.removeMetadata(targetFile.toUri(), stripAnnotations = false, stripComments = false)
        PDDocument.load(preservedFile).use { doc ->
            var totalAnnotations = 0
            var hiddenCount = 0
            for (page in doc.pages) {
                for (annot in page.annotations) {
                    totalAnnotations++
                    val flags = annot.annotationFlags
                    if ((flags and 2) != 0) {
                        hiddenCount++
                    }
                }
            }
            assertEquals("All annotations should be preserved", originalLinks + originalNonLinks, totalAnnotations)
            assertEquals("1 hidden annotation should be preserved", 1, hiddenCount)
        }
    }

    @Test
    fun testSyntheticHiddenAnnotationAndMarkupStripping() = runTest {
        val file = tempFolder.newFile("test_synthetic_annot.pdf")
        PDDocument().use { doc ->
            val page = PDPage()
            doc.addPage(page)

            // 1. Hidden annotation
            val textAnnot = com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText()
            textAnnot.annotationFlags = 2 // Hidden flag
            textAnnot.contents = "Secret hidden text"

            // 2. Visible user comment
            val commentAnnot = com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText()
            commentAnnot.annotationFlags = 4 // Print
            commentAnnot.contents = "Visible comment"
            commentAnnot.cosObject.setString(com.tom_roush.pdfbox.cos.COSName.T, "Reviewer1")

            // 3. Link annotation (navigation)
            val linkAnnot = com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink()
            linkAnnot.annotationFlags = 4

            page.annotations = listOf(textAnnot, commentAnnot, linkAnnot)
            doc.save(file)
        }

        val entries = processor.readMetadata(file.toUri())
        assertTrue("Annotations entry should be present", entries.any { it.key == "Annotations" })
        assertTrue("Annotation Author should be present", entries.any { it.key == "Annotation Author" && it.value == "Reviewer1" })

        // Strip both
        val cleanedBoth = processor.removeMetadata(file.toUri(), stripAnnotations = true, stripComments = true)
        PDDocument.load(cleanedBoth).use { doc ->
            val page = doc.getPage(0)
            assertEquals("Only link annotation should remain", 1, page.annotations.size)
            assertEquals("Link", page.annotations[0].subtype)
        }

        // Keep comments, strip hidden annotations
        val cleanedHiddenOnly = processor.removeMetadata(file.toUri(), stripAnnotations = true, stripComments = false)
        PDDocument.load(cleanedHiddenOnly).use { doc ->
            val page = doc.getPage(0)
            assertEquals("Visible comment and link annotation should remain", 2, page.annotations.size)
            assertTrue(page.annotations.any { it.subtype == "Link" })
            assertTrue(page.annotations.any { it.subtype == "Text" })
        }
    }
}

