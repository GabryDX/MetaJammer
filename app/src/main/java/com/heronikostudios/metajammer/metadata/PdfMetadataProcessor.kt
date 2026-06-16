package com.heronikostudios.metajammer.metadata

import android.content.Context
import android.net.Uri
import com.heronikostudios.metajammer.data.FileRepository
import com.heronikostudios.metajammer.domain.model.MetadataEntry
import com.heronikostudios.metajammer.domain.model.MetadataReplacementPlan
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

    suspend fun readMetadata(uri: Uri): List<MetadataEntry> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<MetadataEntry>()

        runCatching {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                PDDocument.load(inputStream).use { document ->
                    entries.add(MetadataEntry("Page Count", document.numberOfPages.toString()))
                    
                    val info = document.documentInformation
                    info.title?.let { entries.add(MetadataEntry("Title", it)) }
                    info.author?.let { entries.add(MetadataEntry("Author", it)) }
                    info.subject?.let { entries.add(MetadataEntry("Subject", it)) }
                    info.keywords?.let { entries.add(MetadataEntry("Keywords", it)) }
                    info.creator?.let { entries.add(MetadataEntry("Creator", it)) }
                    info.producer?.let { entries.add(MetadataEntry("Producer", it)) }
                }
            }
        }.onFailure {
            Timber.e(it, "Failed to read PDF metadata for %s", uri)
        }

        return@withContext entries
    }

    suspend fun poisonMetadata(inputUri: Uri, plan: MetadataReplacementPlan): File? = withContext(Dispatchers.IO) {
        runCatching {
            val outputFile = fileRepository.createCacheFile(prefix = "pdf_poisoned_", suffix = ".pdf")
            
            context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
                PDDocument.load(inputStream).use { document ->
                    
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
                    document.save(FileOutputStream(outputFile))
                }
            }
            outputFile
        }.onFailure {
            Timber.e(it, "Failed to poison PDF metadata for %s", inputUri)
        }.getOrNull()
    }

    suspend fun removeMetadata(inputUri: Uri): File? = withContext(Dispatchers.IO) {
        runCatching {
            val outputFile = fileRepository.createCacheFile(prefix = "pdf_clean_", suffix = ".pdf")
            
            context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
                PDDocument.load(inputStream).use { document ->
                    
                    // Overwrite metadata with a blank information dictionary
                    document.documentInformation = PDDocumentInformation()
                    document.save(FileOutputStream(outputFile))
                }
            }
            outputFile
        }.onFailure {
            Timber.e(it, "Failed to strip PDF metadata for %s", inputUri)
        }.getOrNull()
    }
}
