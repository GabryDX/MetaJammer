package com.heronikostudios.metajammer.metadata

import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.heronikostudios.metajammer.data.FileRepository
import com.heronikostudios.metajammer.domain.model.MetadataReplacementPlan
import com.heronikostudios.metajammer.domain.model.ThumbnailHandling
import timber.log.Timber
import java.io.File

class ImageMetadataProcessor(
    private val fileRepository: FileRepository
) {

    companion object {
        /**
         * Dynamically find all TAG_ constants in ExifInterface using reflection.
         * This ensures that when we clear metadata, we target every tag the library knows about,
         * including obscure ones.
         */
        private val ALL_SUPPORTED_TAGS by lazy {
            val reflectionTags = ExifInterface::class.java.fields
                .filter { it.name.startsWith("TAG_") && it.type == String::class.java }
                .mapNotNull {
                    try {
                        it.get(null) as? String
                    } catch (_: Exception) {
                        null
                    }
                }

            val customTags = listOf(
                "ImageResources",
                "OwnerName",
                "PrintIM",
                "SensitivityType",
                "StandardOutputSensitivity",
                "RecommendedExposureIndex"
            )

            (reflectionTags + customTags).distinct()
        }

        private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        private val PNG_METADATA_CHUNKS = setOf("tEXt", "zTXt", "iTXt", "eXIf")

        /**
         * Strips ancillary metadata chunks (tEXt, zTXt, iTXt, eXIf) from a PNG byte stream
         * without recompressing or altering image raster pixels.
         */
        fun stripPngChunks(inputFile: File, outputFile: File): Boolean {
            val bytes = inputFile.readBytes()
            if (bytes.size < 8) return false
            for (i in 0 until 8) {
                if (bytes[i] != PNG_SIGNATURE[i]) return false
            }

            val output = java.io.ByteArrayOutputStream(bytes.size)
            output.write(PNG_SIGNATURE)

            var offset = 8
            val buffer = java.nio.ByteBuffer.wrap(bytes)

            while (offset + 8 <= bytes.size) {
                buffer.position(offset)
                val length = buffer.int
                if (length < 0 || offset + 12L + length > bytes.size) {
                    return false
                }
                val typeBytes = ByteArray(4)
                buffer.get(typeBytes)
                val chunkType = String(typeBytes, Charsets.US_ASCII)

                val totalChunkSize = 12 + length
                if (chunkType in PNG_METADATA_CHUNKS) {
                    Timber.d("Stripped PNG metadata chunk: %s (%d bytes)", chunkType, totalChunkSize)
                } else {
                    output.write(bytes, offset, totalChunkSize)
                }

                offset += totalChunkSize
                if (chunkType == "IEND") break
            }

            outputFile.writeBytes(output.toByteArray())
            return true
        }
    }

    /**
     * Removes metadata from an image.
     */
    fun removeMetadata(
        inputUri: Uri,
        keepOrientation: Boolean = true,
        thumbnailHandling: ThumbnailHandling = ThumbnailHandling.REMOVE,
        mimeType: String? = null
    ): File {
        return processImage(inputUri, "img_clean_", keepOrientation, thumbnailHandling, mimeType) { exif ->
            ALL_SUPPORTED_TAGS.forEach { tag -> exif.setAttribute(tag, null) }
        }
    }

    /**
     * Replaces existing metadata with "poisoned" (fake) values from a plan.
     */
    fun poisonMetadata(
        inputUri: Uri,
        plan: MetadataReplacementPlan,
        keepOrientation: Boolean = true,
        thumbnailHandling: ThumbnailHandling = ThumbnailHandling.REMOVE,
        mimeType: String? = null
    ): File {
        return processImage(inputUri, "img_poisoned_", keepOrientation, thumbnailHandling, mimeType) { exif ->
            // First, clear ALL supported tags to ensure no non-standard or obscure metadata remains.
            // This satisfies the requirement to delete all "extra" metadata instead of leaving it.
            ALL_SUPPORTED_TAGS.forEach { tag -> exif.setAttribute(tag, null) }

            // Set fake values from the plan
            exif.setAttribute(ExifInterface.TAG_DATETIME, plan.dateTime)
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, plan.dateTime)
            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, plan.dateTime)
            exif.setAttribute(ExifInterface.TAG_MAKE, plan.make)
            exif.setAttribute(ExifInterface.TAG_MODEL, plan.model)
            exif.setAttribute(ExifInterface.TAG_SOFTWARE, plan.software)
            exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, plan.imageDescription)
            exif.setAttribute(ExifInterface.TAG_USER_COMMENT, plan.userComment)
            exif.setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, plan.photographicSensitivity)
            exif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, plan.exposureTime)
            exif.setAttribute(ExifInterface.TAG_F_NUMBER, plan.fNumber)
            exif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, plan.focalLength)
            exif.setAttribute(ExifInterface.TAG_WHITE_BALANCE, plan.whiteBalance)
            exif.setAttribute(ExifInterface.TAG_FLASH, plan.flash)

            plan.lensMake?.let { exif.setAttribute(ExifInterface.TAG_LENS_MAKE, it) }
            plan.lensModel?.let { exif.setAttribute(ExifInterface.TAG_LENS_MODEL, it) }

            exif.setLatLong(plan.latitude, plan.longitude)
        }
    }

    private inline fun processImage(
        inputUri: Uri,
        prefix: String,
        keepOrientation: Boolean,
        thumbnailHandling: ThumbnailHandling,
        mimeType: String?,
        action: (ExifInterface) -> Unit
    ): File {
        val extension = if (mimeType != null) {
            fileRepository.getExtensionFromMime(mimeType)
        } else {
            fileRepository.getExtension(inputUri)
        }
        val outputFile = fileRepository.copyUriToCache(inputUri, prefix = prefix, suffix = extension)

        // For PNG images: strip ancillary metadata chunks (tEXt, zTXt, iTXt, eXIf)
        if (mimeType == "image/png" || extension.equals(".png", ignoreCase = true)) {
            runCatching {
                val tempCleanPng = fileRepository.createCacheFile(prefix = "png_chunk_clean_", suffix = ".png")
                if (stripPngChunks(outputFile, tempCleanPng)) {
                    tempCleanPng.copyTo(outputFile, overwrite = true)
                }
                tempCleanPng.delete()
            }.onFailure {
                Timber.w(it, "Failed to strip PNG chunks, proceeding with ExifInterface")
            }
        }

        val exif = ExifInterface(outputFile.absolutePath)
        val originalOrientation = if (keepOrientation) exif.getAttribute(ExifInterface.TAG_ORIENTATION) else null
        val originalWidth = exif.getAttribute(ExifInterface.TAG_IMAGE_WIDTH)
        val originalHeight = exif.getAttribute(ExifInterface.TAG_IMAGE_LENGTH)

        action(exif)

        if (keepOrientation && !originalOrientation.isNullOrBlank()) {
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, originalOrientation)
        } else if (!keepOrientation) {
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, null)
        }

        // Always restore dimensions as they are intrinsic to the file structure
        if (!originalWidth.isNullOrBlank()) exif.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, originalWidth)
        if (!originalHeight.isNullOrBlank()) exif.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, originalHeight)

        if (thumbnailHandling == ThumbnailHandling.REMOVE) {
            exif.setAttribute(ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT, null)
            exif.setAttribute(ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH, null)
        }

        try {
            exif.saveAttributes()
        } catch (e: Exception) {
            Timber.e(e, "Failed to save EXIF attributes for %s", outputFile.absolutePath)
            outputFile.delete()
            throw e
        }
        return outputFile
    }
}
