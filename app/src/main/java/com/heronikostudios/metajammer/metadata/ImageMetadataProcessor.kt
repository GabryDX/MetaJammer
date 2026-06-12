package com.heronikostudios.metajammer.metadata

import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.heronikostudios.metajammer.data.FileRepository
import com.heronikostudios.metajammer.domain.model.MetadataReplacementPlan
import java.io.File

class ImageMetadataProcessor(
    private val fileRepository: FileRepository
) {

    /**
     * Removes metadata from an image.
     * Uses an extensive list of tags to clear, targeting privacy-sensitive information.
     */
    fun removeMetadata(inputUri: Uri, keepOrientation: Boolean = true): File {
        val extension = fileRepository.getExtension(inputUri)
        val inputFile = fileRepository.copyUriToCache(inputUri, prefix = "img_in_", suffix = extension)
        val outputFile = fileRepository.createSharedTempFile("img_clean_", extension)
        inputFile.copyTo(outputFile, overwrite = true)

        val originalExif = ExifInterface(inputFile.absolutePath)
        val originalOrientation = originalExif.getAttribute(ExifInterface.TAG_ORIENTATION)

        val exif = ExifInterface(outputFile.absolutePath)

        // Extensive list of tags to clear for maximum privacy.
        // This covers GPS, device info, owner info, and workflow history.
        val tagsToClear = listOf(
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
            ExifInterface.TAG_XMP // Clear embedded XMP data which often contains deep history
        )

        tagsToClear.forEach { tag ->
            exif.setAttribute(tag, null)
        }

        if (keepOrientation && !originalOrientation.isNullOrBlank()) {
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, originalOrientation)
        } else {
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, null)
        }

        exif.saveAttributes()
        return outputFile
    }

    /**
     * Replaces existing metadata with "poisoned" (fake) values from a plan.
     */
    fun poisonMetadata(
        inputUri: Uri,
        plan: MetadataReplacementPlan,
        keepOrientation: Boolean = true
    ): File {
        val extension = fileRepository.getExtension(inputUri)
        val inputFile = fileRepository.copyUriToCache(inputUri, prefix = "img_in_", suffix = extension)
        val outputFile = fileRepository.createSharedTempFile("img_poisoned_", extension)
        inputFile.copyTo(outputFile, overwrite = true)

        val originalExif = ExifInterface(inputFile.absolutePath)
        val originalOrientation = originalExif.getAttribute(ExifInterface.TAG_ORIENTATION)

        val exif = ExifInterface(outputFile.absolutePath)

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

        // Clear other sensitive tags that aren't part of the plan to ensure no leaks
        val tagsToClear = listOf(
            ExifInterface.TAG_ARTIST,
            ExifInterface.TAG_COPYRIGHT,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_BODY_SERIAL_NUMBER,
            ExifInterface.TAG_LENS_SERIAL_NUMBER,
            ExifInterface.TAG_XMP
        )
        tagsToClear.forEach { exif.setAttribute(it, null) }

        if (keepOrientation && !originalOrientation.isNullOrBlank()) {
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, originalOrientation)
        }

        exif.setLatLong(plan.latitude, plan.longitude)

        exif.saveAttributes()
        return outputFile
    }
}
