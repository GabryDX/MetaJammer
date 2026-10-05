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
import com.heronikostudios.metajammer.metadata.SvgMetadataProcessor
import com.heronikostudios.metajammer.util.useCompat
import timber.log.Timber
import java.io.File

class MetadataRepository(
    private val fileRepository: FileRepository
) {
    private val context = fileRepository.getContext()
    private val imageProcessor = ImageMetadataProcessor(fileRepository)
    private val mediaProcessor = MediaMetadataProcessor(fileRepository)
    private val pdfProcessor = PdfMetadataProcessor(context, fileRepository)
    private val svgProcessor = SvgMetadataProcessor(fileRepository)

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
        replacementPlan: MetadataReplacementPlan? = null,
        stripGps: Boolean = true,
        stripDeviceModel: Boolean = true,
        stripDateTime: Boolean = true,
        stripCameraSettings: Boolean = true,
        stripComments: Boolean = true,
        stripPdfAnnotations: Boolean = true,
        stripPdfComments: Boolean = true,
        preserveJpegJfif: Boolean = true
    ): File {
        val mime = resolveEffectiveMime(selectedFile)
        return when {
            mime == "image/svg+xml" -> {
                when (mode) {
                    ProcessingMode.POISON_METADATA -> {
                        val plan = requireNotNull(replacementPlan) { "Plan required for poison mode" }
                        svgProcessor.poisonMetadata(selectedFile.uri, plan)
                    }
                    ProcessingMode.REMOVE_METADATA -> svgProcessor.removeMetadata(selectedFile.uri)
                }
            }

            mime == "application/pdf" -> {
                when (mode) {
                    ProcessingMode.POISON_METADATA -> {
                        val plan = requireNotNull(replacementPlan) { "Plan required for poison mode" }
                        pdfProcessor.poisonMetadata(
                            inputUri = selectedFile.uri,
                            plan = plan,
                            stripAnnotations = stripPdfAnnotations,
                            stripComments = stripPdfComments
                        )
                    }
                    ProcessingMode.REMOVE_METADATA -> pdfProcessor.removeMetadata(
                        inputUri = selectedFile.uri,
                        stripAnnotations = stripPdfAnnotations,
                        stripComments = stripPdfComments
                    )
                }
            }

            mime.startsWith("image/") -> {
                when (mode) {
                    ProcessingMode.POISON_METADATA -> {
                        val plan = requireNotNull(replacementPlan) { "Plan required for poison mode" }
                        imageProcessor.poisonMetadata(
                            inputUri = selectedFile.uri,
                            plan = plan,
                            keepOrientation = keepOrientation,
                            thumbnailHandling = thumbnailHandling,
                            mimeType = mime,
                            stripGps = stripGps,
                            stripDeviceModel = stripDeviceModel,
                            stripDateTime = stripDateTime,
                            stripCameraSettings = stripCameraSettings,
                            stripComments = stripComments,
                            preserveJfif = preserveJpegJfif
                        )
                    }
                    ProcessingMode.REMOVE_METADATA -> {
                        imageProcessor.removeMetadata(
                            inputUri = selectedFile.uri,
                            keepOrientation = keepOrientation,
                            thumbnailHandling = thumbnailHandling,
                            mimeType = mime,
                            stripGps = stripGps,
                            stripDeviceModel = stripDeviceModel,
                            stripDateTime = stripDateTime,
                            stripCameraSettings = stripCameraSettings,
                            stripComments = stripComments,
                            preserveJfif = preserveJpegJfif
                        )
                    }
                }
            }

            mime.startsWith("video/") || mime.startsWith("audio/") -> {
                when (mode) {
                    ProcessingMode.POISON_METADATA -> {
                        val plan = requireNotNull(replacementPlan) { "Plan required for poison mode" }
                        mediaProcessor.poisonMetadata(selectedFile.uri, plan, mime)
                    }
                    ProcessingMode.REMOVE_METADATA -> mediaProcessor.removeMetadata(selectedFile.uri, mime)
                }
            }

            else -> throw IllegalArgumentException("Unsupported file format for metadata processing: ${mime.ifBlank { "unknown" }} (${selectedFile.displayName})")
        }
    }

    private fun resolveEffectiveMime(selectedFile: SelectedFile): String {
        val mime = selectedFile.mimeType
        if (!mime.isNullOrBlank() && mime != "application/octet-stream" && mime != "binary/octet-stream") {
            return mime
        }
        val ext = selectedFile.displayName.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "heic", "heif" -> "image/heic"
            "svg" -> "image/svg+xml"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "m4a" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "ogg" -> "audio/ogg"
            "pdf" -> "application/pdf"
            else -> mime ?: ""
        }
    }

    suspend fun readMetadata(selectedFile: SelectedFile): List<MetadataEntry> {
        val mime = resolveEffectiveMime(selectedFile)
        return when {
            mime == "image/svg+xml" -> svgProcessor.readMetadata(selectedFile.uri)
            mime == "application/pdf" -> pdfProcessor.readMetadata(selectedFile.uri)
            mime.startsWith("image/") -> readImageMetadata(selectedFile)
            mime.startsWith("video/") || mime.startsWith("audio/") -> readMediaMetadata(selectedFile)
            else -> listOf(MetadataEntry("Info", "Metadata preview not yet supported for $mime"))
        }
    }

    private fun readMediaMetadata(selectedFile: SelectedFile): List<MetadataEntry> {
        val resolver = fileRepository.getContext().contentResolver
        val entries = mutableListOf<MetadataEntry>()
        val mime = resolveEffectiveMime(selectedFile)
        val isOgg = mime == "audio/ogg" || mime == "application/ogg" ||
            selectedFile.displayName.endsWith(".ogg", ignoreCase = true)

        if (isOgg) {
            runCatching {
                resolver.openInputStream(selectedFile.uri)?.use { stream ->
                    val oggEntries = com.heronikostudios.metajammer.metadata.OggMetadataReader.readMetadata(stream)
                    for (oe in oggEntries) {
                        if (entries.none { it.key.equals(oe.key, ignoreCase = true) }) {
                            entries.add(oe)
                        }
                    }
                }
            }.onFailure {
                Timber.e(it, "Failed to read OGG metadata for %s", selectedFile.uri)
            }
        }

        runCatching {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.useCompat { r ->
                resolver.openFileDescriptor(selectedFile.uri, "r")?.use { fd ->
                    r.setDataSource(fd.fileDescriptor)
                    
                    // General metadata
                    r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)?.let {
                        if (entries.none { e -> e.key.equals("Title", ignoreCase = true) }) {
                            entries.add(MetadataEntry("Title", it))
                        }
                    }
                    r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let {
                        if (entries.none { e -> e.key.equals("Artist", ignoreCase = true) }) {
                            entries.add(MetadataEntry("Artist", it))
                        }
                    }
                    r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let {
                        if (entries.none { e -> e.key.equals("Album", ignoreCase = true) }) {
                            entries.add(MetadataEntry("Album", it))
                        }
                    }
                    r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DATE)?.let {
                        if (entries.none { e -> e.key.equals("Date", ignoreCase = true) }) {
                            entries.add(MetadataEntry("Date", it))
                        }
                    }
                    r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_LOCATION)?.let {
                        if (entries.none { e -> e.key.equals("Location", ignoreCase = true) }) {
                            entries.add(MetadataEntry("Location", it))
                        }
                    }
                    
                    // Video specific
                    r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)?.let {
                        if (it == "yes") {
                            r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.let { w ->
                                r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.let { h ->
                                    entries.add(MetadataEntry("Resolution", "${w}x${h}"))
                                }
                            }
                        }
                    }

                    r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.let {
                        val durationMs = it.toLongOrNull() ?: 0L
                        if (durationMs > 0 && entries.none { e -> e.key.equals("Duration", ignoreCase = true) }) {
                            entries.add(MetadataEntry("Duration", "${durationMs / 1000}s"))
                        }
                    }
                }
            }
        }.onFailure {
            Timber.e(it, "Failed to read media metadata for %s", selectedFile.uri)
        }

        // Also query MP4 container box metadata for MP4, MOV, M4A, 3GP containers
        if (!isOgg) {
            runCatching {
                resolver.openInputStream(selectedFile.uri)?.use { stream ->
                    val boxEntries = com.heronikostudios.metajammer.metadata.Mp4MetadataReader.readMetadata(stream)
                    for (boxEntry in boxEntries) {
                        if (entries.none { it.key.equals(boxEntry.key, ignoreCase = true) }) {
                            entries.add(boxEntry)
                        }
                    }
                }
            }.onFailure {
                Timber.e(it, "Failed to read MP4 box metadata for %s", selectedFile.uri)
            }
        }

        return entries
    }

    private fun readImageMetadata(selectedFile: SelectedFile): List<MetadataEntry> {
        val resolver = fileRepository.getContext().contentResolver
        val mime = selectedFile.mimeType ?: ""
        val isPng = mime == "image/png" || selectedFile.displayName.endsWith(".png", ignoreCase = true)

        return try {
            val pngBytes = if (isPng) {
                resolver.openInputStream(selectedFile.uri)?.use { it.readBytes() }
            } else null
            val pngInfo = pngBytes?.let { com.heronikostudios.metajammer.metadata.PngMetadataReader.readMetadata(it) }

            val rawExifEntries = resolver.openInputStream(selectedFile.uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                PREVIEW_TAGS.mapNotNull { tag ->
                    val value = exif.getAttribute(tag)
                    if (value.isNullOrBlank()) return@mapNotNull null

                    // Filter out dummy compatibility values inserted by ExifInterface
                    if (tag == ExifInterface.TAG_ORIENTATION && (value == "0" || value.toIntOrNull() == 0)) return@mapNotNull null
                    if (tag == ExifInterface.TAG_IMAGE_WIDTH && value == "0") {
                        return@mapNotNull pngInfo?.width?.let { MetadataEntry(tag, it.toString()) }
                    }
                    if (tag == ExifInterface.TAG_IMAGE_LENGTH && value == "0") {
                        return@mapNotNull pngInfo?.height?.let { MetadataEntry(tag, it.toString()) }
                    }
                    MetadataEntry(tag, value)
                }
            } ?: emptyList()

            val combined = rawExifEntries.toMutableList()

            if (isPng && pngInfo != null) {
                if (pngInfo.width != null && combined.none { it.key == ExifInterface.TAG_IMAGE_WIDTH }) {
                    combined.add(MetadataEntry(ExifInterface.TAG_IMAGE_WIDTH, pngInfo.width.toString()))
                }
                if (pngInfo.height != null && combined.none { it.key == ExifInterface.TAG_IMAGE_LENGTH }) {
                    combined.add(MetadataEntry(ExifInterface.TAG_IMAGE_LENGTH, pngInfo.height.toString()))
                }
                pngInfo.entries.forEach { pngEntry ->
                    if (combined.none { it.key.equals(pngEntry.key, ignoreCase = true) }) {
                        combined.add(pngEntry)
                    }
                }
            }

            // Check if there is genuine metadata beyond just intrinsic image dimensions
            val hasGenuineMetadata = combined.any { entry ->
                entry.key != ExifInterface.TAG_IMAGE_WIDTH &&
                entry.key != ExifInterface.TAG_IMAGE_LENGTH &&
                entry.key != ExifInterface.TAG_ORIENTATION
            }

            if (!hasGenuineMetadata && combined.all {
                it.key == ExifInterface.TAG_IMAGE_WIDTH ||
                it.key == ExifInterface.TAG_IMAGE_LENGTH ||
                it.key == ExifInterface.TAG_ORIENTATION ||
                it.value == "0"
            }) {
                emptyList()
            } else {
                combined
            }
        } catch (e: Exception) {
            Timber.e(e, "Could not read image metadata for %s", selectedFile.uri)
            listOf(MetadataEntry("Error", "Could not read metadata: ${e.message}"))
        }.ifEmpty {
            listOf(MetadataEntry("Info", "No readable EXIF metadata found"))
        }
    }
}
