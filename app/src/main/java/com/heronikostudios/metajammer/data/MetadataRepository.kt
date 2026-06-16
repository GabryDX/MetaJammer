package com.heronikostudios.metajammer.data

import androidx.exifinterface.media.ExifInterface
import com.heronikostudios.metajammer.domain.model.MetadataEntry
import com.heronikostudios.metajammer.domain.model.MetadataReplacementPlan
import com.heronikostudios.metajammer.domain.model.ProcessingMode
import com.heronikostudios.metajammer.domain.model.SelectedFile
import com.heronikostudios.metajammer.domain.model.ThumbnailHandling
import com.heronikostudios.metajammer.metadata.ImageMetadataProcessor
import com.heronikostudios.metajammer.metadata.MediaMetadataProcessor
import com.heronikostudios.metajammer.metadata.PdfMetadataProcessor
import timber.log.Timber
import java.io.File

class MetadataRepository(
    private val fileRepository: FileRepository
) {
    private val context = fileRepository.getContext()
    private val imageProcessor = ImageMetadataProcessor(fileRepository)
    private val mediaProcessor = MediaMetadataProcessor(fileRepository)
    private val pdfProcessor = PdfMetadataProcessor(context, fileRepository)

    companion object {
        private val PREVIEW_TAGS = listOf(
            ExifInterface.TAG_ARTIST,
            ExifInterface.TAG_COPYRIGHT,
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_IMAGE_DESCRIPTION,
            ExifInterface.TAG_IMAGE_LENGTH,
            ExifInterface.TAG_IMAGE_WIDTH,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.TAG_SOFTWARE,
            ExifInterface.TAG_USER_COMMENT,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_FLASH,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_BODY_SERIAL_NUMBER,
            ExifInterface.TAG_LENS_MAKE,
            ExifInterface.TAG_LENS_MODEL,
            ExifInterface.TAG_LENS_SERIAL_NUMBER
        )
    }

    suspend fun processFile(
        selectedFile: SelectedFile,
        mode: ProcessingMode,
        keepOrientation: Boolean,
        thumbnailHandling: ThumbnailHandling = ThumbnailHandling.REMOVE,
        replacementPlan: MetadataReplacementPlan? = null
    ): File {
        val mime = selectedFile.mimeType ?: ""
        return when {
            mime.startsWith("image/") -> {
                when (mode) {
                    ProcessingMode.POISON_METADATA -> {
                        val plan = requireNotNull(replacementPlan) { "Plan required for poison mode" }
                        imageProcessor.poisonMetadata(selectedFile.uri, plan, keepOrientation, thumbnailHandling)
                    }
                    ProcessingMode.REMOVE_METADATA -> {
                        imageProcessor.removeMetadata(selectedFile.uri, keepOrientation, thumbnailHandling)
                    }
                }
            }

            mime.startsWith("video/") || mime.startsWith("audio/") -> {
                when (mode) {
                    ProcessingMode.POISON_METADATA -> {
                        val plan = requireNotNull(replacementPlan) { "Plan required for poison mode" }
                        mediaProcessor.poisonMetadata(selectedFile.uri, plan)
                    }
                    ProcessingMode.REMOVE_METADATA -> mediaProcessor.removeMetadata(selectedFile.uri)
                }
            }

            mime == "application/pdf" -> {
                val result = when (mode) {
                    ProcessingMode.POISON_METADATA -> {
                        val plan = requireNotNull(replacementPlan) { "Plan required for poison mode" }
                        pdfProcessor.poisonMetadata(selectedFile.uri, plan)
                    }
                    ProcessingMode.REMOVE_METADATA -> pdfProcessor.removeMetadata(selectedFile.uri)
                }
                result ?: fileRepository.copyUriToCache(selectedFile.uri, prefix = "pdf_failed_", suffix = ".pdf")
            }

            else -> fileRepository.copyUriToCache(selectedFile.uri, prefix = "generic_", suffix = null)
        }
    }

    suspend fun readMetadata(selectedFile: SelectedFile): List<MetadataEntry> {
        val mime = selectedFile.mimeType ?: ""
        return when {
            mime.startsWith("image/") -> readImageMetadata(selectedFile)
            mime.startsWith("video/") || mime.startsWith("audio/") -> readMediaMetadata(selectedFile)
            mime == "application/pdf" -> pdfProcessor.readMetadata(selectedFile.uri)
            else -> listOf(MetadataEntry("Info", "Metadata preview not yet supported for $mime"))
        }
    }

    /**
     * Reads metadata changes that would happen in poison mode.
     */
    fun getPoisonPreview(selectedFile: SelectedFile, plan: MetadataReplacementPlan): List<MetadataEntry> {
        val mime = selectedFile.mimeType ?: ""
        return when {
            mime.startsWith("image/") -> {
                listOf(
                    MetadataEntry(ExifInterface.TAG_DATETIME, plan.dateTime),
                    MetadataEntry(ExifInterface.TAG_MAKE, plan.make),
                    MetadataEntry(ExifInterface.TAG_MODEL, plan.model),
                    MetadataEntry(ExifInterface.TAG_SOFTWARE, plan.software),
                    MetadataEntry(ExifInterface.TAG_IMAGE_DESCRIPTION, plan.imageDescription),
                    MetadataEntry(ExifInterface.TAG_USER_COMMENT, plan.userComment),
                    MetadataEntry(ExifInterface.TAG_GPS_LATITUDE, plan.latitude.toString()),
                    MetadataEntry(ExifInterface.TAG_GPS_LONGITUDE, plan.longitude.toString())
                )
            }
            mime.startsWith("video/") -> {
                val entries = mutableListOf(
                    MetadataEntry("Location", "${plan.latitude}, ${plan.longitude}")
                )
                plan.title?.let { entries.add(MetadataEntry("Title", it)) }
                plan.artist?.let { entries.add(MetadataEntry("Director", it)) }
                plan.year?.let { entries.add(MetadataEntry("Year", it)) }
                plan.genre?.let { entries.add(MetadataEntry("Genre", it)) }
                plan.mediaDate?.let { entries.add(MetadataEntry("Date", it)) }
                entries
            }
            mime.startsWith("audio/") -> {
                val entries = mutableListOf(
                    MetadataEntry("Location", "${plan.latitude}, ${plan.longitude}")
                )
                plan.title?.let { entries.add(MetadataEntry("Title", it)) }
                plan.artist?.let { entries.add(MetadataEntry("Artist", it)) }
                plan.album?.let { entries.add(MetadataEntry("Album", it)) }
                plan.year?.let { entries.add(MetadataEntry("Year", it)) }
                plan.genre?.let { entries.add(MetadataEntry("Genre", it)) }
                plan.mediaDate?.let { entries.add(MetadataEntry("Date", it)) }
                entries
            }
            mime == "application/pdf" -> {
                val entries = mutableListOf<MetadataEntry>()
                plan.pdfTitle?.let { entries.add(MetadataEntry("Title", it)) }
                plan.author?.let { entries.add(MetadataEntry("Author", it)) }
                plan.creator?.let { entries.add(MetadataEntry("Creator", it)) }
                plan.producer?.let { entries.add(MetadataEntry("Producer", it)) }
                entries
            }
            else -> emptyList()
        }
    }

    private fun readMediaMetadata(selectedFile: SelectedFile): List<MetadataEntry> {
        val resolver = fileRepository.getContext().contentResolver
        val entries = mutableListOf<MetadataEntry>()
        
        runCatching {
            val retriever = android.media.MediaMetadataRetriever()
            resolver.openFileDescriptor(selectedFile.uri, "r")?.use { fd ->
                retriever.setDataSource(fd.fileDescriptor)
                
                // General metadata
                retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)?.let {
                    entries.add(MetadataEntry("Title", it))
                }
                retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let {
                    entries.add(MetadataEntry("Artist", it))
                }
                retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let {
                    entries.add(MetadataEntry("Album", it))
                }
                retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DATE)?.let {
                    entries.add(MetadataEntry("Date", it))
                }
                retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_LOCATION)?.let {
                    entries.add(MetadataEntry("Location (Raw)", it))
                }
                
                // Video specific
                retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)?.let {
                    if (it == "yes") {
                        retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.let { w ->
                            retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.let { h ->
                                entries.add(MetadataEntry("Resolution", "${w}x${h}"))
                            }
                        }
                    }
                }

                retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.let {
                    val durationMs = it.toLongOrNull() ?: 0L
                    entries.add(MetadataEntry("Duration", "${durationMs / 1000}s"))
                }
            }
            retriever.release()
        }.onFailure {
            Timber.e(it, "Failed to read media metadata for %s", selectedFile.uri)
        }

        return entries
    }

    private fun readImageMetadata(selectedFile: SelectedFile): List<MetadataEntry> {
        val resolver = fileRepository.getContext().contentResolver
        return try {
            resolver.openInputStream(selectedFile.uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                PREVIEW_TAGS.mapNotNull { tag ->
                    val value = exif.getAttribute(tag)
                    if (!value.isNullOrBlank()) MetadataEntry(tag, value) else null
                }
            } ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Could not read image metadata for %s", selectedFile.uri)
            listOf(MetadataEntry("Error", "Could not read metadata: ${e.message}"))
        }.ifEmpty {
            listOf(MetadataEntry("Info", "No readable EXIF metadata found"))
        }
    }
}
