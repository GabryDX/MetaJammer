package com.heronikostudios.metajammer.metadata

import android.content.Context
import android.net.Uri
import com.heronikostudios.metajammer.data.FileRepository
import com.heronikostudios.metajammer.domain.model.MetadataEntry
import com.heronikostudios.metajammer.domain.model.MetadataReplacementPlan
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

/**
 * Handles metadata reading and modification for PDF files using PDFBox-Android.
 */
class PdfMetadataProcessor(
    private val context: Context,
    private val fileRepository: FileRepository
) {

    companion object {
        private val COMMENT_MARKUP_SUBTYPES = setOf(
            "Text",
            "FreeText",
            "Highlight",
            "Underline",
            "Squiggly",
            "StrikeOut",
            "Caret",
            "Ink",
            "Line",
            "Square",
            "Circle",
            "Polygon",
            "PolyLine",
            "Popup",
            "FileAttachment",
            "Sound",
            "Movie",
            "Screen",
            "RichMedia",
            "3D",
            "Redact"
        )

        private val WATERMARK_STAMP_SUBTYPES = setOf(
            "Watermark",
            "Stamp"
        )
    }

    private fun ensureInitialized() {
        if (!PDFBoxResourceLoader.isReady()) {
            PDFBoxResourceLoader.init(context)
        }
    }

    suspend fun readMetadata(uri: Uri): List<MetadataEntry> = withContext(Dispatchers.IO) {
        ensureInitialized()
        val entries = mutableListOf<MetadataEntry>()

        runCatching {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                PDDocument.load(inputStream, MemoryUsageSetting.setupMixed(10L * 1024 * 1024)).use { document ->
                    val info = document.documentInformation
                    info.title?.let { entries.add(MetadataEntry("Title", it)) }
                    info.author?.let { entries.add(MetadataEntry("Author", it)) }
                    info.subject?.let { entries.add(MetadataEntry("Subject", it)) }
                    info.keywords?.let { entries.add(MetadataEntry("Keywords", it)) }
                    info.creator?.let { entries.add(MetadataEntry("Creator", it)) }
                    info.producer?.let { entries.add(MetadataEntry("Producer", it)) }
                    info.creationDate?.time?.let { entries.add(MetadataEntry("Creation Date", it.toString())) }
                    info.modificationDate?.time?.let { entries.add(MetadataEntry("Modification Date", it.toString())) }

                    // Check for PageLabels metadata
                    val catalog = document.documentCatalog
                    if (catalog.pageLabels != null || catalog.cosObject.containsKey(com.tom_roush.pdfbox.cos.COSName.getPDFName("PageLabels"))) {
                        entries.add(MetadataEntry("Page Labels", "Custom numbering"))
                    }

                    // Check for strippable annotations, watermarks, and comments
                    var totalAnnotations = 0
                    var hiddenCount = 0
                    var watermarkCount = 0
                    var commentCount = 0
                    val annotationAuthors = mutableSetOf<String>()

                    for (page in document.pages) {
                        val annots = page.annotations ?: continue
                        for (annot in annots) {
                            val subtype = annot.subtype ?: ""
                            val flags = annot.annotationFlags
                            val isHidden = (flags and 2) != 0 || (flags and 1) != 0 || (flags and 32) != 0
                            val isWatermark = subtype in WATERMARK_STAMP_SUBTYPES || annot.cosObject.getNameAsString("IT") == "Watermark"
                            val isCommentOrMarkup = subtype in COMMENT_MARKUP_SUBTYPES

                            if (isHidden || isWatermark || isCommentOrMarkup) {
                                totalAnnotations++
                                if (isHidden) hiddenCount++
                                if (isWatermark) watermarkCount++
                                if (isCommentOrMarkup) commentCount++

                                val author = annot.cosObject.getString(com.tom_roush.pdfbox.cos.COSName.T)
                                if (!author.isNullOrBlank()) {
                                    annotationAuthors.add(author)
                                }
                            }
                        }
                    }

                    if (totalAnnotations > 0) {
                        val summary = buildString {
                            append("$totalAnnotations found")
                            val details = mutableListOf<String>()
                            if (hiddenCount > 0) details.add("$hiddenCount hidden")
                            if (watermarkCount > 0) details.add("$watermarkCount watermark/stamp")
                            if (commentCount > 0 && details.isEmpty()) details.add("$commentCount comment/markup")
                            if (details.isNotEmpty()) {
                                append(" (${details.joinToString(", ")})")
                            }
                        }
                        entries.add(MetadataEntry("Annotations", summary))
                    }

                    if (annotationAuthors.isNotEmpty()) {
                        entries.add(MetadataEntry("Annotation Author", annotationAuthors.joinToString(", ")))
                    }
                }
            }
        }.onFailure {
            Timber.e(it, "Failed to read PDF metadata for %s", uri)
        }

        return@withContext entries
    }

    suspend fun poisonMetadata(
        inputUri: Uri,
        plan: MetadataReplacementPlan,
        stripAnnotations: Boolean = true,
        stripComments: Boolean = true
    ): File = withContext(Dispatchers.IO) {
        ensureInitialized()
        val outputFile = fileRepository.createCacheFile(prefix = "pdf_poisoned_", suffix = ".pdf")
        try {
            context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
                PDDocument.load(inputStream, MemoryUsageSetting.setupMixed(10L * 1024 * 1024)).use { document ->
                    // Replace with fake data
                    val info = PDDocumentInformation().apply {
                        title = plan.pdfTitle
                        author = plan.author
                        creator = plan.creator
                        producer = plan.producer
                        subject = plan.subject
                        keywords = plan.keywords
                    }
                    
                    document.documentInformation = info
                    val catalog = document.documentCatalog
                    catalog.metadata = null
                    catalog.pageLabels = null
                    catalog.cosObject.removeItem(com.tom_roush.pdfbox.cos.COSName.getPDFName("PageLabels"))
                    catalog.cosObject.removeItem(com.tom_roush.pdfbox.cos.COSName.getPDFName("PieceInfo"))
                    catalog.cosObject.removeItem(com.tom_roush.pdfbox.cos.COSName.getPDFName("StructTreeRoot"))
                    catalog.cosObject.removeItem(com.tom_roush.pdfbox.cos.COSName.getPDFName("Metadata"))

                    stripAnnotationsAndWatermarks(
                        document = document,
                        stripAnnotations = stripAnnotations,
                        stripComments = stripComments
                    )
                    document.save(FileOutputStream(outputFile))
                }
            } ?: throw IllegalStateException("Could not open input stream for $inputUri")
            outputFile
        } catch (e: Exception) {
            outputFile.delete()
            Timber.e(e, "Failed to poison PDF metadata for %s", inputUri)
            throw e
        }
    }

    suspend fun removeMetadata(
        inputUri: Uri,
        stripAnnotations: Boolean = true,
        stripComments: Boolean = true
    ): File = withContext(Dispatchers.IO) {
        ensureInitialized()
        val outputFile = fileRepository.createCacheFile(prefix = "pdf_clean_", suffix = ".pdf")
        try {
            context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
                PDDocument.load(inputStream, MemoryUsageSetting.setupMixed(10L * 1024 * 1024)).use { document ->
                    // Overwrite metadata with a blank information dictionary
                    document.documentInformation = PDDocumentInformation()
                    val catalog = document.documentCatalog
                    catalog.metadata = null
                    catalog.pageLabels = null
                    catalog.cosObject.removeItem(com.tom_roush.pdfbox.cos.COSName.getPDFName("PageLabels"))
                    catalog.cosObject.removeItem(com.tom_roush.pdfbox.cos.COSName.getPDFName("PieceInfo"))
                    catalog.cosObject.removeItem(com.tom_roush.pdfbox.cos.COSName.getPDFName("StructTreeRoot"))
                    catalog.cosObject.removeItem(com.tom_roush.pdfbox.cos.COSName.getPDFName("Metadata"))

                    stripAnnotationsAndWatermarks(
                        document = document,
                        stripAnnotations = stripAnnotations,
                        stripComments = stripComments
                    )
                    document.save(FileOutputStream(outputFile))
                }
            } ?: throw IllegalStateException("Could not open input stream for $inputUri")
            outputFile
        } catch (e: Exception) {
            outputFile.delete()
            Timber.e(e, "Failed to strip PDF metadata for %s", inputUri)
            throw e
        }
    }

    private fun stripAnnotationsAndWatermarks(
        document: PDDocument,
        stripAnnotations: Boolean = true,
        stripComments: Boolean = true
    ) {
        runCatching {
            for (page in document.pages) {
                val annotations = page.annotations ?: continue
                val remaining = annotations.filter { annotation ->
                    val subtype = annotation.subtype ?: ""
                    val intent = annotation.getCOSObject().getNameAsString("IT")
                    val flags = annotation.annotationFlags
                    val isHidden = (flags and 2) != 0 || (flags and 1) != 0 || (flags and 32) != 0
                    val isWatermark = subtype in WATERMARK_STAMP_SUBTYPES || intent == "Watermark"
                    val isCommentOrMarkup = subtype in COMMENT_MARKUP_SUBTYPES

                    val shouldStrip = when {
                        stripAnnotations && (isHidden || isWatermark) -> true
                        stripComments && isCommentOrMarkup -> true
                        stripAnnotations && stripComments && subtype != "Link" && subtype != "Widget" -> true
                        else -> false
                    }
                    !shouldStrip
                }
                if (remaining.size != annotations.size) {
                    if (remaining.isEmpty()) {
                        page.cosObject.removeItem(com.tom_roush.pdfbox.cos.COSName.ANNOTS)
                    } else {
                        page.annotations = remaining
                    }
                }
            }

            // 2. Clear Optional Content Groups (OCGs) / Layers
            // OCGs are often used for watermarks that can be toggled on/off
            if (stripAnnotations && document.documentCatalog.ocProperties != null) {
                document.documentCatalog.ocProperties = null
                Timber.d("Cleared PDF OCG properties (layers)")
            }
        }.onFailure {
            Timber.e(it, "Failed to strip annotations and watermarks from PDF")
        }
    }
}
