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
        private val ALL_PRIVACY_TAGS = listOf(
            ExifInterface.TAG_ARTIST,
            ExifInterface.TAG_COPYRIGHT,
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DEVICE_SETTING_DESCRIPTION,
            ExifInterface.TAG_IMAGE_DESCRIPTION,
            ExifInterface.TAG_IMAGE_UNIQUE_ID,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_SOFTWARE,
            ExifInterface.TAG_SUBSEC_TIME,
            ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
            ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
            ExifInterface.TAG_USER_COMMENT,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_DEST_BEARING,
            ExifInterface.TAG_GPS_DEST_BEARING_REF,
            ExifInterface.TAG_GPS_IMG_DIRECTION,
            ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
            ExifInterface.TAG_GPS_SPEED,
            ExifInterface.TAG_GPS_SPEED_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_MAP_DATUM,
            ExifInterface.TAG_GPS_SATELLITES,
            ExifInterface.TAG_GPS_STATUS,
            ExifInterface.TAG_GPS_MEASURE_MODE,
            ExifInterface.TAG_GPS_DOP,
            ExifInterface.TAG_GPS_TRACK,
            ExifInterface.TAG_GPS_TRACK_REF,
            ExifInterface.TAG_GPS_VERSION_ID,
            ExifInterface.TAG_FLASH,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_BODY_SERIAL_NUMBER,
            ExifInterface.TAG_LENS_MAKE,
            ExifInterface.TAG_LENS_MODEL,
            ExifInterface.TAG_LENS_SERIAL_NUMBER,
            ExifInterface.TAG_XMP
        )

        private val POISON_LEAK_PROTECTION_TAGS = listOf(
            ExifInterface.TAG_ARTIST,
            ExifInterface.TAG_COPYRIGHT,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_BODY_SERIAL_NUMBER,
            ExifInterface.TAG_LENS_SERIAL_NUMBER,
            ExifInterface.TAG_XMP
        )
    }

    /**
     * Removes metadata from an image.
     */
    fun removeMetadata(
        inputUri: Uri,
        keepOrientation: Boolean = true,
        thumbnailHandling: ThumbnailHandling = ThumbnailHandling.REMOVE
    ): File {
        return processImage(inputUri, "img_clean_", keepOrientation, thumbnailHandling) { exif ->
            ALL_PRIVACY_TAGS.forEach { tag -> exif.setAttribute(tag, null) }
        }
    }

    /**
     * Replaces existing metadata with "poisoned" (fake) values from a plan.
     */
    fun poisonMetadata(
        inputUri: Uri,
        plan: MetadataReplacementPlan,
        keepOrientation: Boolean = true,
        thumbnailHandling: ThumbnailHandling = ThumbnailHandling.REMOVE
    ): File {
        return processImage(inputUri, "img_poisoned_", keepOrientation, thumbnailHandling) { exif ->
            // Set fake values
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

            // Clear other sensitive tags to ensure no leaks
            POISON_LEAK_PROTECTION_TAGS.forEach { exif.setAttribute(it, null) }

            exif.setLatLong(plan.latitude, plan.longitude)
        }
    }

    private inline fun processImage(
        inputUri: Uri,
        prefix: String,
        keepOrientation: Boolean,
        thumbnailHandling: ThumbnailHandling,
        action: (ExifInterface) -> Unit
    ): File {
        val extension = fileRepository.getExtension(inputUri)
        val outputFile = fileRepository.copyUriToCache(inputUri, prefix = prefix, suffix = extension)

        val exif = ExifInterface(outputFile.absolutePath)
        val originalOrientation = if (keepOrientation) exif.getAttribute(ExifInterface.TAG_ORIENTATION) else null

        action(exif)

        if (keepOrientation && !originalOrientation.isNullOrBlank()) {
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, originalOrientation)
        } else if (!keepOrientation) {
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, null)
        }

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
