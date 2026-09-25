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
         * Strips ancillary metadata chunks (tEXt, zTXt, iTXt, eXIf) from a PNG byte array
         * without recompressing or altering image raster pixels.
         */
        fun stripPngChunks(bytes: ByteArray): ByteArray? {
            if (bytes.size < 8) return null
            for (i in 0 until 8) {
                if (bytes[i] != PNG_SIGNATURE[i]) return null
            }

            val output = java.io.ByteArrayOutputStream(bytes.size)
            output.write(PNG_SIGNATURE)

            var offset = 8
            val buffer = java.nio.ByteBuffer.wrap(bytes)

            while (offset + 8 <= bytes.size) {
                buffer.position(offset)
                val length = buffer.int
                if (length < 0 || offset + 12L + length > bytes.size) {
                    return null
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

            return output.toByteArray()
        }

        /**
         * Strips ancillary metadata chunks (tEXt, zTXt, iTXt, eXIf) from a PNG file
         * without recompressing or altering image raster pixels.
         */
        fun stripPngChunks(inputFile: File, outputFile: File): Boolean {
            val bytes = inputFile.readBytes()
            val stripped = stripPngChunks(bytes) ?: return false
            outputFile.writeBytes(stripped)
            return true
        }

        /**
         * Strips metadata APPn markers (APP1 Exif/XMP, APP2 ICC, APP13 IPTC, COM) from a JPEG byte array
         * without recompressing raster image scan data.
         */
        fun stripJpegMarkers(bytes: ByteArray): ByteArray? {
            if (bytes.size < 4 || (bytes[0].toInt() and 0xFF) != 0xFF || (bytes[1].toInt() and 0xFF) != 0xD8) {
                return null
            }
            val output = java.io.ByteArrayOutputStream(bytes.size)
            output.write(0xFF)
            output.write(0xD8)
            var offset = 2
            while (offset + 1 < bytes.size) {
                if ((bytes[offset].toInt() and 0xFF) != 0xFF) {
                    output.write(bytes, offset, bytes.size - offset)
                    break
                }
                val marker = bytes[offset + 1].toInt() and 0xFF
                if (marker == 0xD9) { // EOI
                    output.write(0xFF)
                    output.write(0xD9)
                    break
                }
                if (marker == 0xDA) { // SOS (Start of Scan)
                    output.write(bytes, offset, bytes.size - offset)
                    break
                }
                if (marker == 0x00 || (marker in 0xD0..0xD7)) {
                    output.write(bytes, offset, 2)
                    offset += 2
                    continue
                }
                if (offset + 4 > bytes.size) {
                    output.write(bytes, offset, bytes.size - offset)
                    break
                }
                val length = ((bytes[offset + 2].toInt() and 0xFF) shl 8) or (bytes[offset + 3].toInt() and 0xFF)
                val totalLength = 2 + length
                if (offset + totalLength > bytes.size) {
                    output.write(bytes, offset, bytes.size - offset)
                    break
                }

                val isMetadata = (marker in 0xE1..0xEF) || marker == 0xFE
                if (!isMetadata) {
                    output.write(bytes, offset, totalLength)
                } else {
                    Timber.d("Stripped JPEG metadata marker: 0xFF%02X (%d bytes)", marker, totalLength)
                }
                offset += totalLength
            }
            return output.toByteArray()
        }

        /**
         * Strips metadata APPn markers from a JPEG file.
         */
        fun stripJpegMarkers(inputFile: File, outputFile: File): Boolean {
            val bytes = inputFile.readBytes()
            val stripped = stripJpegMarkers(bytes) ?: return false
            outputFile.writeBytes(stripped)
            return true
        }
        val GPS_TAGS = setOf(
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
            ExifInterface.TAG_GPS_AREA_INFORMATION,
            ExifInterface.TAG_GPS_DOP,
            ExifInterface.TAG_GPS_SPEED,
            ExifInterface.TAG_GPS_SPEED_REF,
            ExifInterface.TAG_GPS_TRACK,
            ExifInterface.TAG_GPS_TRACK_REF,
            ExifInterface.TAG_GPS_IMG_DIRECTION,
            ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
            ExifInterface.TAG_GPS_MAP_DATUM,
            ExifInterface.TAG_GPS_DEST_LATITUDE,
            ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
            ExifInterface.TAG_GPS_DEST_LONGITUDE,
            ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
            ExifInterface.TAG_GPS_DEST_BEARING,
            ExifInterface.TAG_GPS_DEST_BEARING_REF,
            ExifInterface.TAG_GPS_DEST_DISTANCE,
            ExifInterface.TAG_GPS_DEST_DISTANCE_REF,
            ExifInterface.TAG_GPS_DIFFERENTIAL
        )

        val DEVICE_TAGS = setOf(
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_SOFTWARE,
            ExifInterface.TAG_BODY_SERIAL_NUMBER,
            ExifInterface.TAG_CAMERA_OWNER_NAME,
            ExifInterface.TAG_LENS_MAKE,
            ExifInterface.TAG_LENS_MODEL,
            ExifInterface.TAG_LENS_SERIAL_NUMBER,
            ExifInterface.TAG_LENS_SPECIFICATION,
            ExifInterface.TAG_DEVICE_SETTING_DESCRIPTION,
            "OwnerName"
        )

        val DATE_TIME_TAGS = setOf(
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_SUBSEC_TIME,
            ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
            ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
            ExifInterface.TAG_OFFSET_TIME,
            ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
            ExifInterface.TAG_OFFSET_TIME_DIGITIZED
        )

        val CAMERA_SETTINGS_TAGS = setOf(
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_EXPOSURE_PROGRAM,
            ExifInterface.TAG_SPECTRAL_SENSITIVITY,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_OECF,
            ExifInterface.TAG_SHUTTER_SPEED_VALUE,
            ExifInterface.TAG_APERTURE_VALUE,
            ExifInterface.TAG_BRIGHTNESS_VALUE,
            ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
            ExifInterface.TAG_MAX_APERTURE_VALUE,
            ExifInterface.TAG_SUBJECT_DISTANCE,
            ExifInterface.TAG_METERING_MODE,
            ExifInterface.TAG_LIGHT_SOURCE,
            ExifInterface.TAG_FLASH,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_SUBJECT_AREA,
            ExifInterface.TAG_FLASH_ENERGY,
            ExifInterface.TAG_SPATIAL_FREQUENCY_RESPONSE,
            ExifInterface.TAG_FOCAL_PLANE_X_RESOLUTION,
            ExifInterface.TAG_FOCAL_PLANE_Y_RESOLUTION,
            ExifInterface.TAG_FOCAL_PLANE_RESOLUTION_UNIT,
            ExifInterface.TAG_SUBJECT_LOCATION,
            ExifInterface.TAG_EXPOSURE_INDEX,
            ExifInterface.TAG_SENSING_METHOD,
            ExifInterface.TAG_FILE_SOURCE,
            ExifInterface.TAG_SCENE_TYPE,
            ExifInterface.TAG_CFA_PATTERN,
            ExifInterface.TAG_CUSTOM_RENDERED,
            ExifInterface.TAG_EXPOSURE_MODE,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_DIGITAL_ZOOM_RATIO,
            ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
            ExifInterface.TAG_SCENE_CAPTURE_TYPE,
            ExifInterface.TAG_GAIN_CONTROL,
            ExifInterface.TAG_CONTRAST,
            ExifInterface.TAG_SATURATION,
            ExifInterface.TAG_SHARPNESS,
            ExifInterface.TAG_SUBJECT_DISTANCE_RANGE,
            "SensitivityType",
            "StandardOutputSensitivity",
            "RecommendedExposureIndex"
        )

        val COMMENTS_TAGS = setOf(
            ExifInterface.TAG_IMAGE_DESCRIPTION,
            ExifInterface.TAG_ARTIST,
            ExifInterface.TAG_COPYRIGHT,
            ExifInterface.TAG_USER_COMMENT,
            "XPAuthor",
            "XPComment",
            "XPKeywords",
            "XPSubject",
            "XPTitle"
        )

        fun shouldStripTag(
            tag: String,
            stripGps: Boolean,
            stripDeviceModel: Boolean,
            stripDateTime: Boolean,
            stripCameraSettings: Boolean,
            stripComments: Boolean
        ): Boolean {
            if (tag in GPS_TAGS || tag.startsWith("GPS")) return stripGps
            if (tag in DEVICE_TAGS) return stripDeviceModel
            if (tag in DATE_TIME_TAGS) return stripDateTime
            if (tag in CAMERA_SETTINGS_TAGS) return stripCameraSettings
            if (tag in COMMENTS_TAGS) return stripComments
            return true
        }
    }

    /**
     * Removes metadata from an image with granular category control.
     */
    fun removeMetadata(
        inputUri: Uri,
        keepOrientation: Boolean = true,
        thumbnailHandling: ThumbnailHandling = ThumbnailHandling.REMOVE,
        mimeType: String? = null,
        stripGps: Boolean = true,
        stripDeviceModel: Boolean = true,
        stripDateTime: Boolean = true,
        stripCameraSettings: Boolean = true,
        stripComments: Boolean = true
    ): File {
        return processImage(inputUri, "img_clean_", keepOrientation, thumbnailHandling, mimeType) { exif ->
            ALL_SUPPORTED_TAGS.forEach { tag ->
                if (shouldStripTag(tag, stripGps, stripDeviceModel, stripDateTime, stripCameraSettings, stripComments)) {
                    exif.setAttribute(tag, null)
                }
            }
        }
    }

    /**
     * Replaces existing metadata with "poisoned" (fake) values from a plan with granular category control.
     */
    fun poisonMetadata(
        inputUri: Uri,
        plan: MetadataReplacementPlan,
        keepOrientation: Boolean = true,
        thumbnailHandling: ThumbnailHandling = ThumbnailHandling.REMOVE,
        mimeType: String? = null,
        stripGps: Boolean = true,
        stripDeviceModel: Boolean = true,
        stripDateTime: Boolean = true,
        stripCameraSettings: Boolean = true,
        stripComments: Boolean = true
    ): File {
        return processImage(inputUri, "img_poisoned_", keepOrientation, thumbnailHandling, mimeType) { exif ->
            // Clear only categories marked for stripping or poisoning
            ALL_SUPPORTED_TAGS.forEach { tag ->
                if (shouldStripTag(tag, stripGps, stripDeviceModel, stripDateTime, stripCameraSettings, stripComments)) {
                    exif.setAttribute(tag, null)
                }
            }

            // Set fake values only for categories marked for poisoning
            if (stripDateTime) {
                exif.setAttribute(ExifInterface.TAG_DATETIME, plan.dateTime)
                exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, plan.dateTime)
                exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, plan.dateTime)
            }

            if (stripDeviceModel) {
                exif.setAttribute(ExifInterface.TAG_MAKE, plan.make)
                exif.setAttribute(ExifInterface.TAG_MODEL, plan.model)
                exif.setAttribute(ExifInterface.TAG_SOFTWARE, plan.software)
                plan.lensMake?.let { exif.setAttribute(ExifInterface.TAG_LENS_MAKE, it) }
                plan.lensModel?.let { exif.setAttribute(ExifInterface.TAG_LENS_MODEL, it) }
            }

            if (stripComments) {
                exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, plan.imageDescription)
                exif.setAttribute(ExifInterface.TAG_USER_COMMENT, plan.userComment)
            }

            if (stripCameraSettings) {
                exif.setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, plan.photographicSensitivity)
                exif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, plan.exposureTime)
                exif.setAttribute(ExifInterface.TAG_F_NUMBER, plan.fNumber)
                exif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, plan.focalLength)
                exif.setAttribute(ExifInterface.TAG_WHITE_BALANCE, plan.whiteBalance)
                exif.setAttribute(ExifInterface.TAG_FLASH, plan.flash)
            }

            if (stripGps) {
                exif.setLatLong(plan.latitude, plan.longitude)
            }
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
