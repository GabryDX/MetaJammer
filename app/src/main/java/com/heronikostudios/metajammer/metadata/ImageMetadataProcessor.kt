package com.heronikostudios.metajammer.metadata

import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.heronikostudios.metajammer.data.FileRepository
import com.heronikostudios.metajammer.domain.model.MetadataReplacementPlan
import java.io.File

class ImageMetadataProcessor(
    private val fileRepository: FileRepository
) {

    fun removeMetadata(inputUri: Uri, keepOrientation: Boolean = true): File {
        val inputFile = fileRepository.copyUriToCache(inputUri, prefix = "img_in_", suffix = ".jpg")
        val outputFile = fileRepository.createSharedTempFile("img_clean_", ".jpg")
        inputFile.copyTo(outputFile, overwrite = true)

        val originalExif = ExifInterface(inputFile.absolutePath)
        val originalOrientation = originalExif.getAttribute(ExifInterface.TAG_ORIENTATION)

        val exif = ExifInterface(outputFile.absolutePath)

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
            ExifInterface.TAG_FLASH,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY
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

    fun poisonMetadata(
        inputUri: Uri,
        plan: MetadataReplacementPlan,
        keepOrientation: Boolean = true
    ): File {
        val inputFile = fileRepository.copyUriToCache(inputUri, prefix = "img_in_", suffix = ".jpg")
        val outputFile = fileRepository.createSharedTempFile("img_poisoned_", ".jpg")
        inputFile.copyTo(outputFile, overwrite = true)

        val originalExif = ExifInterface(inputFile.absolutePath)
        val originalOrientation = originalExif.getAttribute(ExifInterface.TAG_ORIENTATION)

        val exif = ExifInterface(outputFile.absolutePath)

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

        if (keepOrientation && !originalOrientation.isNullOrBlank()) {
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, originalOrientation)
        }

        exif.setLatLong(plan.latitude, plan.longitude)

        exif.saveAttributes()
        return outputFile
    }
}
