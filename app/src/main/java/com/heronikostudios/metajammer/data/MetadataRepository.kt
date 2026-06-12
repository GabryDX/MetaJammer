package com.heronikostudios.metajammer.data

import androidx.exifinterface.media.ExifInterface
import com.heronikostudios.metajammer.domain.model.MetadataEntry
import com.heronikostudios.metajammer.domain.model.MetadataReplacementPlan
import com.heronikostudios.metajammer.domain.model.ProcessingMode
import com.heronikostudios.metajammer.domain.model.SelectedFile
import com.heronikostudios.metajammer.domain.model.ThumbnailHandling
import com.heronikostudios.metajammer.metadata.ImageMetadataProcessor
import com.heronikostudios.metajammer.metadata.VideoMetadataProcessor
import java.io.File

class MetadataRepository(
    private val fileRepository: FileRepository
) {
    private val imageProcessor = ImageMetadataProcessor(fileRepository)
    private val videoProcessor = VideoMetadataProcessor(fileRepository)

    fun processFile(
        selectedFile: SelectedFile,
        mode: ProcessingMode,
        keepOrientation: Boolean,
        thumbnailHandling: ThumbnailHandling = ThumbnailHandling.REMOVE,
        replacementPlan: MetadataReplacementPlan? = null
    ): File {
        return when {
            selectedFile.mimeType?.startsWith("image/") == true -> {
                when (mode) {
                    ProcessingMode.POISON_METADATA -> {
                        val plan = requireNotNull(replacementPlan) {
                            "Replacement plan is required for poison metadata mode"
                        }
                        imageProcessor.poisonMetadata(
                            inputUri = selectedFile.uri,
                            plan = plan,
                            keepOrientation = keepOrientation,
                            thumbnailHandling = thumbnailHandling
                        )
                    }

                    ProcessingMode.REMOVE_METADATA -> imageProcessor.removeMetadata(
                        selectedFile.uri,
                        keepOrientation = keepOrientation,
                        thumbnailHandling = thumbnailHandling
                    )
                }
            }

            selectedFile.mimeType?.startsWith("video/") == true -> {
                when (mode) {
                    ProcessingMode.POISON_METADATA -> videoProcessor.poisonMetadata(selectedFile.uri)
                    ProcessingMode.REMOVE_METADATA -> videoProcessor.removeMetadata(selectedFile.uri)
                }
            }

            else -> {
                fileRepository.copyUriToCache(selectedFile.uri, prefix = "generic_", suffix = null)
            }
        }
    }

    fun readMetadata(selectedFile: SelectedFile): List<MetadataEntry> {
        return when {
            selectedFile.mimeType?.startsWith("image/") == true -> {
                readImageMetadata(selectedFile)
            }

            selectedFile.mimeType?.startsWith("video/") == true -> {
                listOf(
                    MetadataEntry("Warning", "Video metadata stripping is currently limited. File is copied as-is."),
                    MetadataEntry("Status", "Support for MP4/MOV metadata stripping is planned.")
                )
            }

            else -> {
                listOf(
                    MetadataEntry("Info", "Metadata preview not yet supported for ${selectedFile.mimeType ?: "unknown"}")
                )
            }
        }
    }

    private fun readImageMetadata(selectedFile: SelectedFile): List<MetadataEntry> {
        val tempFile = fileRepository.copyUriToCache(selectedFile.uri, prefix = "preview_", suffix = null)
        val exif = ExifInterface(tempFile.absolutePath)

        val tags = listOf(
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

        return tags.mapNotNull { tag ->
            val value = exif.getAttribute(tag)
            if (!value.isNullOrBlank()) MetadataEntry(tag, value) else null
        }.ifEmpty {
            listOf(MetadataEntry("Info", "No readable EXIF metadata found"))
        }
    }
}
