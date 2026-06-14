package com.heronikostudios.metajammer.data

import androidx.exifinterface.media.ExifInterface
import com.heronikostudios.metajammer.domain.model.MetadataEntry
import com.heronikostudios.metajammer.domain.model.MetadataReplacementPlan
import com.heronikostudios.metajammer.domain.model.ProcessingMode
import com.heronikostudios.metajammer.domain.model.SelectedFile
import com.heronikostudios.metajammer.domain.model.ThumbnailHandling
import com.heronikostudios.metajammer.metadata.ImageMetadataProcessor
import com.heronikostudios.metajammer.metadata.VideoMetadataProcessor
import timber.log.Timber
import java.io.File

class MetadataRepository(
    private val fileRepository: FileRepository
) {
    private val imageProcessor = ImageMetadataProcessor(fileRepository)
    private val videoProcessor = VideoMetadataProcessor(fileRepository)

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

    fun processFile(
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

            mime.startsWith("video/") -> {
                when (mode) {
                    ProcessingMode.POISON_METADATA -> {
                        val plan = requireNotNull(replacementPlan) { "Plan required for poison mode" }
                        videoProcessor.poisonMetadata(selectedFile.uri, plan.latitude, plan.longitude)
                    }
                    ProcessingMode.REMOVE_METADATA -> videoProcessor.removeMetadata(selectedFile.uri)
                }
            }

            else -> fileRepository.copyUriToCache(selectedFile.uri, prefix = "generic_", suffix = null)
        }
    }

    fun readMetadata(selectedFile: SelectedFile): List<MetadataEntry> {
        val mime = selectedFile.mimeType ?: ""
        return when {
            mime.startsWith("image/") -> readImageMetadata(selectedFile)
            mime.startsWith("video/") -> readVideoMetadata(selectedFile)
            else -> listOf(MetadataEntry("Info", "Metadata preview not yet supported for $mime"))
        }
    }

    private fun readVideoMetadata(selectedFile: SelectedFile): List<MetadataEntry> {
        val resolver = fileRepository.getContext().contentResolver
        val entries = mutableListOf<MetadataEntry>()
        
        runCatching {
            val retriever = android.media.MediaMetadataRetriever()
            resolver.openFileDescriptor(selectedFile.uri, "r")?.use { fd ->
                retriever.setDataSource(fd.fileDescriptor)
                
                // Add some useful metadata fields
                retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DATE)?.let {
                    entries.add(MetadataEntry("Creation Date", it))
                }
                retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_LOCATION)?.let {
                    entries.add(MetadataEntry("Location (Raw)", it))
                }
                retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.let {
                    val durationMs = it.toLongOrNull() ?: 0L
                    entries.add(MetadataEntry("Duration", "${durationMs / 1000}s"))
                }
            }
            retriever.release()
        }.onFailure {
            Timber.e(it, "Failed to read video metadata for %s", selectedFile.uri)
        }

        entries.add(MetadataEntry("Status", "Full re-muxing supported for MP4/MOV containers."))
        entries.add(MetadataEntry("Privacy", "This process strips GPS, dates, and device atoms."))

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
