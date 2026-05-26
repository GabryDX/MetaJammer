package com.heronikostudios.metajammer.metadata

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.heronikostudios.metajammer.data.FileRepository
import java.io.File

class ImageMetadataProcessor(
    private val context: Context,
    private val fileRepository: FileRepository
) {

    fun removeMetadata(inputUri: Uri): File {
        val inputFile = fileRepository.copyUriToCache(inputUri, prefix = "img_in_", suffix = ".jpg")
        val outputFile = File(context.cacheDir, "img_clean_${System.currentTimeMillis()}.jpg")
        inputFile.copyTo(outputFile, overwrite = true)

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
            ExifInterface.TAG_GPS_TIMESTAMP
        )

        tagsToClear.forEach { tag ->
            exif.setAttribute(tag, null)
        }

        exif.saveAttributes()
        return outputFile
    }

    fun poisonMetadata(inputUri: Uri): File {
        val inputFile = fileRepository.copyUriToCache(inputUri, prefix = "img_in_", suffix = ".jpg")
        val outputFile = File(context.cacheDir, "img_poisoned_${System.currentTimeMillis()}.jpg")
        inputFile.copyTo(outputFile, overwrite = true)

        val exif = ExifInterface(outputFile.absolutePath)
        val randomDateTime = MetadataReplacementGenerator.randomDateTime()
        val (lat, lon) = MetadataReplacementGenerator.randomLatLong()

        exif.setAttribute(ExifInterface.TAG_DATETIME, randomDateTime)
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, randomDateTime)
        exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, randomDateTime)
        exif.setAttribute(ExifInterface.TAG_MAKE, MetadataReplacementGenerator.randomMake())
        exif.setAttribute(ExifInterface.TAG_MODEL, MetadataReplacementGenerator.randomModel())
        exif.setAttribute(ExifInterface.TAG_SOFTWARE, MetadataReplacementGenerator.randomSoftware())
        exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, "Edited with MetaJammer")
        exif.setAttribute(ExifInterface.TAG_USER_COMMENT, "Metadata replaced for privacy testing")
        exif.setLatLong(lat, lon)

        exif.saveAttributes()
        return outputFile
    }
}
