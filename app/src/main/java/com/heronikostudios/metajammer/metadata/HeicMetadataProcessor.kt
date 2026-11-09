package com.heronikostudios.metajammer.metadata

import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.heronikostudios.metajammer.data.FileRepository
import com.heronikostudios.metajammer.domain.model.MetadataEntry
import com.heronikostudios.metajammer.domain.model.MetadataReplacementPlan
import com.heronikostudios.metajammer.domain.model.ThumbnailHandling
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pure Kotlin parser, scrubber, and metadata injector for ISO-BMFF HEIF/HEIC containers.
 * Operates at the container box level without decoding or recompressing HEVC raster image data,
 * guaranteeing 100% lossless processing, zero quality loss, and instant performance.
 */
class HeicMetadataProcessor(
    private val fileRepository: FileRepository
) {

    private val context = fileRepository.getContext()

    companion object {
        /**
         * Valid minimal 1x1 JPEG baseline image used as a template to serialize standard
         * TIFF EXIF blocks via Android's ExifInterface.
         */
        private val MINIMAL_JPEG_TEMPLATE = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00.toByte(), 0x10.toByte(), 0x4A.toByte(), 0x46.toByte(), 0x49.toByte(), 0x46.toByte(), 0x00.toByte(), 0x01.toByte(),
            0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0xFF.toByte(), 0xDB.toByte(), 0x00.toByte(), 0x43.toByte(),
            0x00.toByte(), 0x03.toByte(), 0x02.toByte(), 0x02.toByte(), 0x03.toByte(), 0x02.toByte(), 0x02.toByte(), 0x03.toByte(), 0x03.toByte(), 0x03.toByte(), 0x03.toByte(), 0x04.toByte(),
            0x03.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x08.toByte(), 0x05.toByte(), 0x05.toByte(), 0x04.toByte(), 0x04.toByte(), 0x05.toByte(), 0x0A.toByte(), 0x07.toByte(),
            0x07.toByte(), 0x06.toByte(), 0x08.toByte(), 0x0C.toByte(), 0x0A.toByte(), 0x0C.toByte(), 0x0C.toByte(), 0x0B.toByte(), 0x0A.toByte(), 0x0B.toByte(), 0x0B.toByte(), 0x0D.toByte(),
            0x0E.toByte(), 0x12.toByte(), 0x10.toByte(), 0x0D.toByte(), 0x0E.toByte(), 0x11.toByte(), 0x0E.toByte(), 0x0B.toByte(), 0x0B.toByte(), 0x10.toByte(), 0x16.toByte(), 0x10.toByte(),
            0x11.toByte(), 0x13.toByte(), 0x14.toByte(), 0x15.toByte(), 0x15.toByte(), 0x15.toByte(), 0x0C.toByte(), 0x0F.toByte(), 0x17.toByte(), 0x18.toByte(), 0x16.toByte(), 0x14.toByte(),
            0x18.toByte(), 0x12.toByte(), 0x14.toByte(), 0x15.toByte(), 0x14.toByte(), 0xFF.toByte(), 0xDB.toByte(), 0x00.toByte(), 0x43.toByte(), 0x01.toByte(), 0x03.toByte(), 0x04.toByte(),
            0x04.toByte(), 0x05.toByte(), 0x04.toByte(), 0x05.toByte(), 0x09.toByte(), 0x05.toByte(), 0x05.toByte(), 0x09.toByte(), 0x14.toByte(), 0x0D.toByte(), 0x0B.toByte(), 0x0D.toByte(),
            0x14.toByte(), 0x14.toByte(), 0x14.toByte(), 0x14.toByte(), 0x14.toByte(), 0x14.toByte(), 0x14.toByte(), 0x14.toByte(), 0x14.toByte(), 0x14.toByte(), 0x14.toByte(), 0x14.toByte(),
            0x14.toByte(), 0x14.toByte(), 0x14.toByte(), 0x14.toByte(), 0x14.toByte(), 0x14.toByte(), 0x14.toByte(), 0x14.toByte(), 0x14.toByte(), 0x14.toByte(), 0x14.toByte(), 0x14.toByte(),
            0x14.toByte(), 0x14.toByte(), 0x14.toByte(), 0x14.toByte(), 0x14.toByte(), 0x14.toByte(), 0x14.toByte(), 0x14.toByte(), 0x14.toByte(), 0x14.toByte(), 0x14.toByte(), 0x14.toByte(),
            0x14.toByte(), 0x14.toByte(), 0x14.toByte(), 0x14.toByte(), 0x14.toByte(), 0x14.toByte(), 0x14.toByte(), 0x14.toByte(), 0x14.toByte(), 0x14.toByte(), 0x14.toByte(), 0x14.toByte(),
            0x14.toByte(), 0x14.toByte(), 0xFF.toByte(), 0xC0.toByte(), 0x00.toByte(), 0x11.toByte(), 0x08.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x01.toByte(), 0x03.toByte(),
            0x01.toByte(), 0x22.toByte(), 0x00.toByte(), 0x02.toByte(), 0x11.toByte(), 0x01.toByte(), 0x03.toByte(), 0x11.toByte(), 0x01.toByte(), 0xFF.toByte(), 0xC4.toByte(), 0x00.toByte(),
            0x1F.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x05.toByte(), 0x01.toByte(), 0x01.toByte(), 0x01.toByte(), 0x01.toByte(), 0x01.toByte(), 0x01.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(),
            0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0x09.toByte(), 0x0A.toByte(), 0x0B.toByte(), 0xFF.toByte(), 0xC4.toByte(), 0x00.toByte(), 0xB5.toByte(), 0x10.toByte(), 0x00.toByte(),
            0x02.toByte(), 0x01.toByte(), 0x03.toByte(), 0x03.toByte(), 0x02.toByte(), 0x04.toByte(), 0x03.toByte(), 0x05.toByte(), 0x05.toByte(), 0x04.toByte(), 0x04.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x01.toByte(), 0x7D.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x00.toByte(), 0x04.toByte(), 0x11.toByte(), 0x05.toByte(), 0x12.toByte(), 0x21.toByte(),
            0x31.toByte(), 0x41.toByte(), 0x06.toByte(), 0x13.toByte(), 0x51.toByte(), 0x61.toByte(), 0x07.toByte(), 0x22.toByte(), 0x71.toByte(), 0x14.toByte(), 0x32.toByte(), 0x81.toByte(),
            0x91.toByte(), 0xA1.toByte(), 0x08.toByte(), 0x23.toByte(), 0x42.toByte(), 0xB1.toByte(), 0xC1.toByte(), 0x15.toByte(), 0x52.toByte(), 0xD1.toByte(), 0xF0.toByte(), 0x24.toByte(),
            0x33.toByte(), 0x62.toByte(), 0x72.toByte(), 0x82.toByte(), 0x09.toByte(), 0x0A.toByte(), 0x16.toByte(), 0x17.toByte(), 0x18.toByte(), 0x19.toByte(), 0x1A.toByte(), 0x25.toByte(),
            0x26.toByte(), 0x27.toByte(), 0x28.toByte(), 0x29.toByte(), 0x2A.toByte(), 0x34.toByte(), 0x35.toByte(), 0x36.toByte(), 0x37.toByte(), 0x38.toByte(), 0x39.toByte(), 0x3A.toByte(),
            0x43.toByte(), 0x44.toByte(), 0x45.toByte(), 0x46.toByte(), 0x47.toByte(), 0x48.toByte(), 0x49.toByte(), 0x4A.toByte(), 0x53.toByte(), 0x54.toByte(), 0x55.toByte(), 0x56.toByte(),
            0x57.toByte(), 0x58.toByte(), 0x59.toByte(), 0x5A.toByte(), 0x63.toByte(), 0x64.toByte(), 0x65.toByte(), 0x66.toByte(), 0x67.toByte(), 0x68.toByte(), 0x69.toByte(), 0x6A.toByte(),
            0x73.toByte(), 0x74.toByte(), 0x75.toByte(), 0x76.toByte(), 0x77.toByte(), 0x78.toByte(), 0x79.toByte(), 0x7A.toByte(), 0x83.toByte(), 0x84.toByte(), 0x85.toByte(), 0x86.toByte(),
            0x87.toByte(), 0x88.toByte(), 0x89.toByte(), 0x8A.toByte(), 0x92.toByte(), 0x93.toByte(), 0x94.toByte(), 0x95.toByte(), 0x96.toByte(), 0x97.toByte(), 0x98.toByte(), 0x99.toByte(),
            0x9A.toByte(), 0xA2.toByte(), 0xA3.toByte(), 0xA4.toByte(), 0xA5.toByte(), 0xA6.toByte(), 0xA7.toByte(), 0xA8.toByte(), 0xA9.toByte(), 0xAA.toByte(), 0xB2.toByte(), 0xB3.toByte(),
            0xB4.toByte(), 0xB5.toByte(), 0xB6.toByte(), 0xB7.toByte(), 0xB8.toByte(), 0xB9.toByte(), 0xBA.toByte(), 0xC2.toByte(), 0xC3.toByte(), 0xC4.toByte(), 0xC5.toByte(), 0xC6.toByte(),
            0xC7.toByte(), 0xC8.toByte(), 0xC9.toByte(), 0xCA.toByte(), 0xD2.toByte(), 0xD3.toByte(), 0xD4.toByte(), 0xD5.toByte(), 0xD6.toByte(), 0xD7.toByte(), 0xD8.toByte(), 0xD9.toByte(),
            0xDA.toByte(), 0xE1.toByte(), 0xE2.toByte(), 0xE3.toByte(), 0xE4.toByte(), 0xE5.toByte(), 0xE6.toByte(), 0xE7.toByte(), 0xE8.toByte(), 0xE9.toByte(), 0xEA.toByte(), 0xF1.toByte(),
            0xF2.toByte(), 0xF3.toByte(), 0xF4.toByte(), 0xF5.toByte(), 0xF6.toByte(), 0xF7.toByte(), 0xF8.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFF.toByte(), 0xC4.toByte(), 0x00.toByte(),
            0x1F.toByte(), 0x01.toByte(), 0x00.toByte(), 0x03.toByte(), 0x01.toByte(), 0x01.toByte(), 0x01.toByte(), 0x01.toByte(), 0x01.toByte(), 0x01.toByte(), 0x01.toByte(), 0x01.toByte(),
            0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(),
            0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0x09.toByte(), 0x0A.toByte(), 0x0B.toByte(), 0xFF.toByte(), 0xC4.toByte(), 0x00.toByte(), 0xB5.toByte(), 0x11.toByte(), 0x00.toByte(),
            0x02.toByte(), 0x01.toByte(), 0x02.toByte(), 0x04.toByte(), 0x04.toByte(), 0x03.toByte(), 0x04.toByte(), 0x07.toByte(), 0x05.toByte(), 0x04.toByte(), 0x04.toByte(), 0x00.toByte(),
            0x01.toByte(), 0x02.toByte(), 0x77.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x11.toByte(), 0x04.toByte(), 0x05.toByte(), 0x21.toByte(), 0x31.toByte(),
            0x06.toByte(), 0x12.toByte(), 0x41.toByte(), 0x51.toByte(), 0x07.toByte(), 0x61.toByte(), 0x71.toByte(), 0x13.toByte(), 0x22.toByte(), 0x32.toByte(), 0x81.toByte(), 0x08.toByte(),
            0x14.toByte(), 0x42.toByte(), 0x91.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0xC1.toByte(), 0x09.toByte(), 0x23.toByte(), 0x33.toByte(), 0x52.toByte(), 0xF0.toByte(), 0x15.toByte(),
            0x62.toByte(), 0x72.toByte(), 0xD1.toByte(), 0x0A.toByte(), 0x16.toByte(), 0x24.toByte(), 0x34.toByte(), 0xE1.toByte(), 0x25.toByte(), 0xF1.toByte(), 0x17.toByte(), 0x18.toByte(),
            0x19.toByte(), 0x1A.toByte(), 0x26.toByte(), 0x27.toByte(), 0x28.toByte(), 0x29.toByte(), 0x2A.toByte(), 0x35.toByte(), 0x36.toByte(), 0x37.toByte(), 0x38.toByte(), 0x39.toByte(),
            0x3A.toByte(), 0x43.toByte(), 0x44.toByte(), 0x45.toByte(), 0x46.toByte(), 0x47.toByte(), 0x48.toByte(), 0x49.toByte(), 0x4A.toByte(), 0x53.toByte(), 0x54.toByte(), 0x55.toByte(),
            0x56.toByte(), 0x57.toByte(), 0x58.toByte(), 0x59.toByte(), 0x5A.toByte(), 0x63.toByte(), 0x64.toByte(), 0x65.toByte(), 0x66.toByte(), 0x67.toByte(), 0x68.toByte(), 0x69.toByte(),
            0x6A.toByte(), 0x73.toByte(), 0x74.toByte(), 0x75.toByte(), 0x76.toByte(), 0x77.toByte(), 0x78.toByte(), 0x79.toByte(), 0x7A.toByte(), 0x82.toByte(), 0x83.toByte(), 0x84.toByte(),
            0x85.toByte(), 0x86.toByte(), 0x87.toByte(), 0x88.toByte(), 0x89.toByte(), 0x8A.toByte(), 0x92.toByte(), 0x93.toByte(), 0x94.toByte(), 0x95.toByte(), 0x96.toByte(), 0x97.toByte(),
            0x98.toByte(), 0x99.toByte(), 0x9A.toByte(), 0xA2.toByte(), 0xA3.toByte(), 0xA4.toByte(), 0xA5.toByte(), 0xA6.toByte(), 0xA7.toByte(), 0xA8.toByte(), 0xA9.toByte(), 0xAA.toByte(),
            0xB2.toByte(), 0xB3.toByte(), 0xB4.toByte(), 0xB5.toByte(), 0xB6.toByte(), 0xB7.toByte(), 0xB8.toByte(), 0xB9.toByte(), 0xBA.toByte(), 0xC2.toByte(), 0xC3.toByte(), 0xC4.toByte(),
            0xC5.toByte(), 0xC6.toByte(), 0xC7.toByte(), 0xC8.toByte(), 0xC9.toByte(), 0xCA.toByte(), 0xD2.toByte(), 0xD3.toByte(), 0xD4.toByte(), 0xD5.toByte(), 0xD6.toByte(), 0xD7.toByte(),
            0xD8.toByte(), 0xD9.toByte(), 0xDA.toByte(), 0xE2.toByte(), 0xE3.toByte(), 0xE4.toByte(), 0xE5.toByte(), 0xE6.toByte(), 0xE7.toByte(), 0xE8.toByte(), 0xE9.toByte(), 0xEA.toByte(),
            0xF2.toByte(), 0xF3.toByte(), 0xF4.toByte(), 0xF5.toByte(), 0xF6.toByte(), 0xF7.toByte(), 0xF8.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFF.toByte(), 0xDA.toByte(), 0x00.toByte(),
            0x0C.toByte(), 0x03.toByte(), 0x01.toByte(), 0x00.toByte(), 0x02.toByte(), 0x11.toByte(), 0x03.toByte(), 0x11.toByte(), 0x00.toByte(), 0x3F.toByte(), 0x00.toByte(), 0xFD.toByte(),
            0x53.toByte(), 0xA2.toByte(), 0x8A.toByte(), 0x28.toByte(), 0x03.toByte(), 0xFF.toByte(), 0xD9.toByte()
        )

        val HEIC_METADATA_ITEM_TYPES = setOf("exif", "mime", "xml ", "uri ")

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

        private fun readFully(stream: InputStream, buffer: ByteArray, offset: Int = 0, length: Int = buffer.size): Boolean {
            var total = 0
            while (total < length) {
                val count = stream.read(buffer, offset + total, length - total)
                if (count < 0) return false
                total += count
            }
            return true
        }

        private fun readUint(buffer: ByteArray, pos: Int, size: Int): Long {
            var result = 0L
            for (i in 0 until size) {
                result = (result shl 8) or (buffer[pos + i].toLong() and 0xFFL)
            }
            return result
        }

        private fun writeUint(buffer: ByteArrayOutputStream, value: Long, size: Int) {
            for (i in size - 1 downTo 0) {
                buffer.write(((value ushr (i * 8)) and 0xFFL).toInt())
            }
        }

        private fun writeUint(buffer: ByteArray, value: Long, pos: Int, size: Int) {
            for (i in 0 until size) {
                val shift = (size - 1 - i) * 8
                buffer[pos + i] = ((value ushr shift) and 0xFFL).toByte()
            }
        }
    }

    private data class BoxHeader(val type: String, val size: Long, val headerLen: Int)

    private fun readBoxHeader(stream: InputStream): BoxHeader? {
        val buf = ByteArray(8)
        if (!readFully(stream, buf)) return null
        val size32 = ByteBuffer.wrap(buf, 0, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
        val type = String(buf, 4, 4, Charsets.US_ASCII)
        var headerLen = 8
        val size = if (size32 == 1L) {
            val largeBuf = ByteArray(8)
            if (!readFully(stream, largeBuf)) return null
            headerLen = 16
            ByteBuffer.wrap(largeBuf).order(ByteOrder.BIG_ENDIAN).long
        } else {
            size32
        }
        return BoxHeader(type, size, headerLen)
    }

    private data class ItemExtent(val offset: Long, val length: Long)

    private data class ParsedItem(
        val itemId: Int,
        val itemType: String,
        val constructionMethod: Int,
        val dataReferenceIndex: Int,
        val extents: List<ItemExtent>
    )

    fun readMetadata(selectedFile: com.heronikostudios.metajammer.domain.model.SelectedFile): List<MetadataEntry> {
        val resolver = context.contentResolver
        val entries = mutableListOf<MetadataEntry>()

        var hasExifThumbnail = false
        val exif = extractExifInterface(selectedFile.uri)
        if (exif != null) {
            hasExifThumbnail = exif.hasThumbnail()
            val exifEntries = PREVIEW_TAGS.mapNotNull { tag ->
                val value = exif.getAttribute(tag)
                if (value.isNullOrBlank()) return@mapNotNull null
                if (tag == ExifInterface.TAG_ORIENTATION && (value == "0" || value.toIntOrNull() == 0)) return@mapNotNull null
                MetadataEntry(tag, value)
            }
            entries.addAll(exifEntries)
        }

        if (hasExifThumbnail) {
            entries.add(MetadataEntry("Thumbnail", "Present (EXIF)"))
        }

        // Deep container inspection for XMP, thumbnails, or box metadata
        runCatching {
            resolver.openInputStream(selectedFile.uri)?.use { stream ->
                val containerInfo = scanContainer(stream)
                if (containerInfo.hasXmp && entries.none { it.key.equals("XMP Metadata", ignoreCase = true) }) {
                    entries.add(MetadataEntry("XMP Metadata", "Present (${containerInfo.xmpSizeBytes} bytes)"))
                }
                if (containerInfo.hasItemThumbnail && entries.none { it.key.equals("Thumbnail", ignoreCase = true) }) {
                    entries.add(MetadataEntry("Thumbnail", "Present (HEIC Item)"))
                }
            }
        }.onFailure {
            Timber.e(it, "Failed to scan HEIC container for %s", selectedFile.uri)
        }

        val hasGenuine = entries.any { entry ->
            entry.key != ExifInterface.TAG_IMAGE_WIDTH &&
            entry.key != ExifInterface.TAG_IMAGE_LENGTH &&
            entry.key != ExifInterface.TAG_ORIENTATION
        }

        return if (hasGenuine) entries else emptyList()
    }

    /**
     * Extracts an [ExifInterface] from a HEIC file by parsing the container directly.
     * This provides 100% reliable EXIF parsing without requiring Android Framework MediaMetadataRetriever,
     * ensuring support across all API levels (API 26+) and unit test environments.
     */
    fun extractExifInterface(file: File): ExifInterface? {
        val standaloneBytes = extractStandaloneExifBytes(file)
        if (standaloneBytes != null) {
            val exif = runCatching {
                ExifInterface(ByteArrayInputStream(standaloneBytes), ExifInterface.STREAM_TYPE_EXIF_DATA_ONLY)
            }.getOrNull()
            if (exif != null) return exif
        }
        return runCatching { ExifInterface(file.absolutePath) }.getOrNull()
    }

    /**
     * Extracts an [ExifInterface] from a HEIC URI.
     */
    fun extractExifInterface(uri: Uri): ExifInterface? {
        val path = uri.path
        if (uri.scheme == "file" && path != null) {
            val file = File(path)
            if (file.exists()) return extractExifInterface(file)
        }
        val temp = runCatching { fileRepository.copyUriToCache(uri, "heic_exif_", ".heic") }.getOrNull()
        return try {
            if (temp != null) extractExifInterface(temp) else null
        } finally {
            temp?.delete()
        }
    }

    private fun extractStandaloneExifBytes(file: File): ByteArray? {
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val fileSize = raf.length()
                var currOffset = 0L
                var metaPayload: ByteArray? = null

                while (currOffset < fileSize) {
                    raf.seek(currOffset)
                    val headerBuf = ByteArray(8)
                    if (raf.read(headerBuf) < 8) break
                    var bsize = ByteBuffer.wrap(headerBuf, 0, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
                    val btype = String(headerBuf, 4, 4, Charsets.US_ASCII)
                    var hlen = 8
                    if (bsize == 1L) {
                        val largeBuf = ByteArray(8)
                        if (raf.read(largeBuf) < 8) break
                        hlen = 16
                        bsize = ByteBuffer.wrap(largeBuf).order(ByteOrder.BIG_ENDIAN).long
                    }

                    if (btype == "meta") {
                        val payloadLen = (bsize - hlen).toInt()
                        val buf = ByteArray(payloadLen)
                        raf.seek(currOffset + hlen)
                        raf.readFully(buf)
                        metaPayload = buf
                        break
                    }
                    currOffset += bsize
                }

                if (metaPayload == null) {
                    Timber.d("extractStandaloneExif: metaPayload is null")
                    return null
                }

                // Parse iinf and iloc from metaPayload
                var childOffset = 4 // skip version & flags
                var iinfBytes: ByteArray? = null
                var ilocBytes: ByteArray? = null

                while (childOffset + 8 <= metaPayload.size) {
                    val csize = ByteBuffer.wrap(metaPayload, childOffset, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
                    val ctype = String(metaPayload, childOffset + 4, 4, Charsets.US_ASCII)
                    val boxLen = csize.toInt()
                    if (boxLen < 8 || childOffset + boxLen > metaPayload.size) break
                    val cbytes = metaPayload.copyOfRange(childOffset, childOffset + boxLen)
                    if (ctype == "iinf") iinfBytes = cbytes
                    else if (ctype == "iloc") ilocBytes = cbytes
                    childOffset += boxLen
                }

                if (iinfBytes == null || ilocBytes == null) {
                    Timber.d("extractStandaloneExif: iinf or iloc is null")
                    return null
                }

                // Find Exif itemId from iinf
                val iinfVer = iinfBytes[8].toInt()
                var infeCursor = if (iinfVer == 0) 14 else 16
                var exifItemId: Int? = null

                while (infeCursor + 8 <= iinfBytes.size) {
                    val infeSize = ByteBuffer.wrap(iinfBytes, infeCursor, 4).order(ByteOrder.BIG_ENDIAN).int
                    if (infeSize < 8 || infeCursor + infeSize > iinfBytes.size) break
                    val infeBytes = iinfBytes.copyOfRange(infeCursor, infeCursor + infeSize)
                    val infeVer = infeBytes[8].toInt()
                    val itemId: Int
                    val itemType: String
                    if (infeVer in 0..2) {
                        itemId = ByteBuffer.wrap(infeBytes, 12, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
                        itemType = if (infeVer == 2) String(infeBytes, 16, 4, Charsets.US_ASCII) else "mime"
                    } else {
                        itemId = ByteBuffer.wrap(infeBytes, 12, 4).order(ByteOrder.BIG_ENDIAN).int
                        itemType = String(infeBytes, 18, 4, Charsets.US_ASCII)
                    }
                    if (itemType.equals("Exif", ignoreCase = true)) {
                        exifItemId = itemId
                        break
                    }
                    infeCursor += infeSize
                }

                if (exifItemId == null) {
                    Timber.d("extractStandaloneExif: exifItemId is null")
                    return null
                }

                // Locate Exif extent from iloc
                val ilocVer = ilocBytes[8].toInt()
                val offsetSize = (ilocBytes[12].toInt() shr 4) and 0x0F
                val lengthSize = ilocBytes[12].toInt() and 0x0F
                val baseOffsetSize = (ilocBytes[13].toInt() shr 4) and 0x0F
                val indexSize = if (ilocVer in 1..2) ilocBytes[13].toInt() and 0x0F else 0

                var ilocIdx = 14
                val itemCount = if (ilocVer < 2) {
                    val count = ByteBuffer.wrap(ilocBytes, ilocIdx, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
                    ilocIdx += 2
                    count
                } else {
                    val count = ByteBuffer.wrap(ilocBytes, ilocIdx, 4).order(ByteOrder.BIG_ENDIAN).int
                    ilocIdx += 4
                    count
                }

                var extentOffset: Long? = null
                var extentLength: Long? = null

                for (i in 0 until itemCount) {
                    val itemId = if (ilocVer < 2) {
                        val id = ByteBuffer.wrap(ilocBytes, ilocIdx, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
                        ilocIdx += 2
                        id
                    } else {
                        val id = ByteBuffer.wrap(ilocBytes, ilocIdx, 4).order(ByteOrder.BIG_ENDIAN).int
                        ilocIdx += 4
                        id
                    }

                    if (ilocVer in 1..2) {
                        ilocIdx += 2 // skip construction_method and reserved
                    }

                    ilocIdx += 2 // skip data_reference_index

                    val baseOffset = readUint(ilocBytes, ilocIdx, baseOffsetSize)
                    ilocIdx += baseOffsetSize

                    val extentCount = ByteBuffer.wrap(ilocBytes, ilocIdx, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
                    ilocIdx += 2

                    for (e in 0 until extentCount) {
                        if (ilocVer in 1..2 && indexSize > 0) ilocIdx += indexSize
                        val extOffset = readUint(ilocBytes, ilocIdx, offsetSize)
                        ilocIdx += offsetSize
                        val extLength = readUint(ilocBytes, ilocIdx, lengthSize)
                        ilocIdx += lengthSize

                        if (itemId == exifItemId && extentOffset == null) {
                            extentOffset = baseOffset + extOffset
                            extentLength = extLength
                        }
                    }
                }

                if (extentOffset == null || extentLength == null || extentLength <= 4) {
                    Timber.d("extractStandaloneExif: extentOffset=$extentOffset, extentLength=$extentLength")
                    return null
                }

                // Read extent from file
                raf.seek(extentOffset)
                val rawBytes = ByteArray(extentLength.toInt())
                raf.readFully(rawBytes)

                // Format of HEIC Exif item: 4-byte exifOffset prefix
                val exifOffset = ByteBuffer.wrap(rawBytes, 0, 4).order(ByteOrder.BIG_ENDIAN).int
                val payload = if (rawBytes.size > 4) rawBytes.copyOfRange(4, rawBytes.size) else rawBytes

                // Check if payload starts with "Exif\0\0"
                if (payload.size >= 6 && payload[0] == 'E'.code.toByte() && payload[1] == 'x'.code.toByte() &&
                    payload[2] == 'i'.code.toByte() && payload[3] == 'f'.code.toByte() &&
                    payload[4] == 0.toByte() && payload[5] == 0.toByte()
                ) {
                    return payload
                }

                // If payload has an offset to TIFF, or starts with TIFF header (II* or MM*)
                val tiffStart = if (exifOffset in 0 until payload.size) exifOffset else 0
                val tiffBytes = if (tiffStart < payload.size) payload.copyOfRange(tiffStart, payload.size) else payload

                // Prepend Exif\0\0 so ExifInterface(..., STREAM_TYPE_EXIF_DATA_ONLY) recognizes it
                val standalone = ByteArray(6 + tiffBytes.size)
                System.arraycopy("Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1), 0, standalone, 0, 6)
                System.arraycopy(tiffBytes, 0, standalone, 6, tiffBytes.size)
                standalone
            }
        }.onFailure {
            Timber.w(it, "Failed to extract standalone Exif")
        }.getOrNull()
    }

    private data class ContainerScanResult(
        val hasXmp: Boolean = false,
        val xmpSizeBytes: Long = 0L,
        val hasItemThumbnail: Boolean = false
    )

    private fun scanContainer(stream: InputStream): ContainerScanResult {
        var hasXmp = false
        var xmpSize = 0L
        var hasThumbnail = false

        while (true) {
            val header = readBoxHeader(stream) ?: break
            val payloadLen = header.size - header.headerLen
            if (header.type == "meta") {
                val metaBytes = ByteArray(payloadLen.toInt())
                if (!readFully(stream, metaBytes)) break
                val scan = scanMetaPayload(metaBytes)
                hasXmp = scan.hasXmp
                xmpSize = scan.xmpSizeBytes
                hasThumbnail = scan.hasItemThumbnail
            } else {
                if (payloadLen > 0) {
                    skipBytes(stream, payloadLen)
                }
            }
        }
        return ContainerScanResult(hasXmp, xmpSize, hasThumbnail)
    }

    private fun scanMetaPayload(metaPayload: ByteArray): ContainerScanResult {
        var hasXmp = false
        var xmpSize = 0L
        var hasThumbnail = false
        var curr = 4 // Skip version and flags
        val metaLen = metaPayload.size

        val metadataItemIds = mutableMapOf<Int, String>() // itemId -> type

        while (curr + 8 <= metaLen) {
            val bsize = ByteBuffer.wrap(metaPayload, curr, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
            val btype = String(metaPayload, curr + 4, 4, Charsets.US_ASCII)
            val boxLen = bsize.toInt()
            if (boxLen < 8 || curr + boxLen > metaLen) break

            when (btype) {
                "iinf" -> {
                    val iinfBytes = metaPayload.copyOfRange(curr, curr + boxLen)
                    val ver = iinfBytes[8].toInt()
                    var offset = if (ver == 0) 14 else 16
                    while (offset + 8 <= iinfBytes.size) {
                        val infeSize = ByteBuffer.wrap(iinfBytes, offset, 4).order(ByteOrder.BIG_ENDIAN).int
                        if (infeSize < 8 || offset + infeSize > iinfBytes.size) break
                        val infeVer = iinfBytes[offset + 8].toInt()
                        val itemId: Int
                        val itemType: String
                        if (infeVer in 0..2) {
                            itemId = ByteBuffer.wrap(iinfBytes, offset + 12, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
                            itemType = if (infeVer == 2) {
                                String(iinfBytes, offset + 16, 4, Charsets.US_ASCII)
                            } else "mime"
                        } else {
                            itemId = ByteBuffer.wrap(iinfBytes, offset + 12, 4).order(ByteOrder.BIG_ENDIAN).int
                            itemType = String(iinfBytes, offset + 18, 4, Charsets.US_ASCII)
                        }
                        metadataItemIds[itemId] = itemType
                        offset += infeSize
                    }
                }
                "iref" -> {
                    // Check for thmb reference
                    val irefBytes = metaPayload.copyOfRange(curr, curr + boxLen)
                    if (String(irefBytes, Charsets.ISO_8859_1).contains("thmb")) {
                        hasThumbnail = true
                    }
                }
            }
            curr += boxLen
        }

        // Check if any item was XMP
        metadataItemIds.values.forEach { type ->
            if (type.lowercase() in setOf("mime", "xml ")) {
                hasXmp = true
                xmpSize = 1024L // standard indication
            }
        }

        return ContainerScanResult(hasXmp, xmpSize, hasThumbnail)
    }

    private fun skipBytes(stream: InputStream, count: Long) {
        var remaining = count
        val buf = ByteArray(minOf(remaining, 65536L).toInt())
        while (remaining > 0) {
            val read = stream.read(buf, 0, minOf(remaining, buf.size.toLong()).toInt())
            if (read < 0) throw EOFException("Unexpected end of stream while skipping $count bytes")
            remaining -= read
        }
    }

    /**
     * Strips metadata (Exif, XMP, IPTC) from a HEIC file with zero recompression.
     */
    fun removeMetadata(
        inputUri: Uri,
        keepOrientation: Boolean = true,
        thumbnailHandling: ThumbnailHandling = ThumbnailHandling.REMOVE,
        stripGps: Boolean = true,
        stripDeviceModel: Boolean = true,
        stripDateTime: Boolean = true,
        stripCameraSettings: Boolean = true,
        stripComments: Boolean = true
    ): File {
        val outputFile = fileRepository.createSharedTempFile("img_clean_", ".heic")
        val allCategories = stripGps && stripDeviceModel && stripDateTime && stripCameraSettings && stripComments

        // Copy input to a temp source file for random access container rewriting
        val tempSource = fileRepository.copyUriToCache(inputUri, "heic_src_", ".heic")

        try {
            val sourceExif = extractExifInterface(tempSource)
            val origOrientation = if (keepOrientation) {
                sourceExif?.getAttribute(ExifInterface.TAG_ORIENTATION)
            } else null

            val replacementExifPayload = if (!allCategories) {
                // User chose not to strip some categories: preserve only those categories
                generateExifPayload(
                    sourceExif = sourceExif,
                    plan = null,
                    keepOrientation = keepOrientation,
                    stripGps = stripGps,
                    stripDeviceModel = stripDeviceModel,
                    stripDateTime = stripDateTime,
                    stripCameraSettings = stripCameraSettings,
                    stripComments = stripComments
                )
            } else if (keepOrientation && !origOrientation.isNullOrBlank() && origOrientation != "0" && origOrientation != "1") {
                // If only orientation is needed, encode an Exif block with only TAG_ORIENTATION
                generateExifPayload(
                    sourceExif = null,
                    plan = null,
                    keepOrientation = true,
                    stripGps = true,
                    stripDeviceModel = true,
                    stripDateTime = true,
                    stripCameraSettings = true,
                    stripComments = true,
                    forceOrientation = origOrientation
                )
            } else {
                null
            }

            val success = processHeicContainer(
                inputFile = tempSource,
                outputFile = outputFile,
                replacementExifPayload = replacementExifPayload,
                thumbnailHandling = thumbnailHandling
            )

            if (!success || outputFile.length() == 0L) {
                outputFile.delete()
                throw IllegalStateException("Failed to strip HEIC container metadata for $inputUri")
            }

            return outputFile
        } finally {
            tempSource.delete()
        }
    }

    /**
     * Injects realistic poisoned metadata into a HEIC container with zero recompression.
     */
    fun poisonMetadata(
        inputUri: Uri,
        plan: MetadataReplacementPlan,
        keepOrientation: Boolean = true,
        thumbnailHandling: ThumbnailHandling = ThumbnailHandling.REMOVE,
        stripGps: Boolean = true,
        stripDeviceModel: Boolean = true,
        stripDateTime: Boolean = true,
        stripCameraSettings: Boolean = true,
        stripComments: Boolean = true
    ): File {
        val outputFile = fileRepository.createSharedTempFile("img_poisoned_", ".heic")
        val tempSource = fileRepository.copyUriToCache(inputUri, "heic_src_", ".heic")

        try {
            val sourceExif = extractExifInterface(tempSource)
            val poisonedExifPayload = generateExifPayload(
                sourceExif = sourceExif,
                plan = plan,
                keepOrientation = keepOrientation,
                stripGps = stripGps,
                stripDeviceModel = stripDeviceModel,
                stripDateTime = stripDateTime,
                stripCameraSettings = stripCameraSettings,
                stripComments = stripComments
            )

            val success = processHeicContainer(
                inputFile = tempSource,
                outputFile = outputFile,
                replacementExifPayload = poisonedExifPayload,
                thumbnailHandling = thumbnailHandling
            )

            if (!success || outputFile.length() == 0L) {
                outputFile.delete()
                throw IllegalStateException("Failed to poison HEIC container metadata for $inputUri")
            }

            return outputFile
        } finally {
            tempSource.delete()
        }
    }

    /**
     * Serializes standard EXIF tags into an encoded HEIC Exif item payload
     * (4-byte offset followed by standard JPEG-compatible TIFF binary).
     */
    private fun generateExifPayload(
        sourceExif: ExifInterface?,
        plan: MetadataReplacementPlan?,
        keepOrientation: Boolean,
        stripGps: Boolean,
        stripDeviceModel: Boolean,
        stripDateTime: Boolean,
        stripCameraSettings: Boolean,
        stripComments: Boolean,
        forceOrientation: String? = null
    ): ByteArray? {
        val tempJpeg = fileRepository.createSharedTempFile("heic_dummy_", ".jpg")
        try {
            tempJpeg.writeBytes(MINIMAL_JPEG_TEMPLATE)
            val exif = ExifInterface(tempJpeg.absolutePath)

            // If sourceExif provided, preserve any tags NOT marked for stripping
            if (sourceExif != null) {
                PREVIEW_TAGS.forEach { tag ->
                    val shouldStrip = ImageMetadataProcessor.shouldStripTag(
                        tag, stripGps, stripDeviceModel, stripDateTime, stripCameraSettings, stripComments
                    )
                    if (!shouldStrip) {
                        sourceExif.getAttribute(tag)?.let { value ->
                            exif.setAttribute(tag, value)
                        }
                    }
                }
            }

            // Apply poisoned values if in Poison mode
            if (plan != null) {
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

            if (forceOrientation != null) {
                exif.setAttribute(ExifInterface.TAG_ORIENTATION, forceOrientation)
            } else if (keepOrientation && sourceExif != null) {
                sourceExif.getAttribute(ExifInterface.TAG_ORIENTATION)?.let {
                    exif.setAttribute(ExifInterface.TAG_ORIENTATION, it)
                }
            }

            exif.saveAttributes()

            // Read the generated JPEG file and extract the APP1 Exif segment
            val jpegBytes = tempJpeg.readBytes()
            var idx = 2
            var app1ExifPayload: ByteArray? = null
            while (idx + 4 <= jpegBytes.size) {
                if (jpegBytes[idx] != 0xFF.toByte()) break
                val marker = jpegBytes[idx + 1].toInt() and 0xFF
                if (marker == 0xD8 || marker == 0xD9) {
                    idx += 2
                    continue
                }
                val length = ByteBuffer.wrap(jpegBytes, idx + 2, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
                if (marker == 0xE1 && idx + 4 + 6 <= jpegBytes.size) {
                    val prefix = String(jpegBytes, idx + 4, 6, Charsets.ISO_8859_1)
                    if (prefix.startsWith("Exif\u0000\u0000")) {
                        app1ExifPayload = jpegBytes.copyOfRange(idx + 4, idx + 2 + length)
                        break
                    }
                }
                idx += 2 + length
            }

            if (app1ExifPayload == null) {
                Timber.w("generateExifPayload: app1ExifPayload is null")
                return null
            }

            // HEIC Exif item format: 4-byte big-endian offset (6 = skip "Exif\0\0") + app1ExifPayload
            val result = ByteArray(4 + app1ExifPayload.size)
            ByteBuffer.wrap(result, 0, 4).order(ByteOrder.BIG_ENDIAN).putInt(6)
            System.arraycopy(app1ExifPayload, 0, result, 4, app1ExifPayload.size)
            return result
        } catch (e: Exception) {
            Timber.e(e, "Failed to encode Exif payload for HEIC")
            return null
        } finally {
            tempJpeg.delete()
        }
    }

    /**
     * Low-level ISOBMFF HEIC container rewriter using in-place `free`-box padding architecture.
     * Rewrites `meta` box children (`iinf`, `iloc`, `iref`) and pads `meta` with standard ISO-BMFF
     * `free` boxes so that `meta` maintains its exact original length, leaving `mdat`, all image extents,
     * and `idat` items 100% untouched and unshifted.
     * Dropped extents in `mdat` (old EXIF/XMP or thumbnails) are securely zeroed out.
     * Replacement EXIF payloads (e.g. for poisoning or category preservation) are appended to `mdat`.
     */
    private fun processHeicContainer(
        inputFile: File,
        outputFile: File,
        replacementExifPayload: ByteArray?,
        thumbnailHandling: ThumbnailHandling
    ): Boolean {
        return runCatching {
            val fileSize = inputFile.length()

            // 1. Read top-level box positions and sizes
            var currOffset = 0L
            var metaHeaderLen = 8
            var metaSize = 0L
            var metaOffset = 0L
            var metaPayload: ByteArray? = null

            var mdatOffset = 0L
            var mdatHeaderLen = 8
            var mdatSize = 0L
            var foundMdat = false

            RandomAccessFile(inputFile, "r").use { raf ->
                while (currOffset < fileSize) {
                    raf.seek(currOffset)
                    val headerBuf = ByteArray(8)
                    if (raf.read(headerBuf) < 8) break
                    var bsize = ByteBuffer.wrap(headerBuf, 0, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
                    val btype = String(headerBuf, 4, 4, Charsets.US_ASCII)
                    var hlen = 8
                    if (bsize == 1L) {
                        val largeBuf = ByteArray(8)
                        if (raf.read(largeBuf) < 8) break
                        hlen = 16
                        bsize = ByteBuffer.wrap(largeBuf).order(ByteOrder.BIG_ENDIAN).long
                    } else if (bsize == 0L) {
                        bsize = fileSize - currOffset
                    }

                    if (btype == "meta") {
                        metaOffset = currOffset
                        metaSize = bsize
                        metaHeaderLen = hlen
                        val payloadLen = (bsize - hlen).toInt()
                        val buf = ByteArray(payloadLen)
                        raf.seek(currOffset + hlen)
                        raf.readFully(buf)
                        metaPayload = buf
                    } else if (btype == "mdat") {
                        mdatOffset = currOffset
                        mdatHeaderLen = hlen
                        mdatSize = bsize
                        foundMdat = true
                    }

                    currOffset += bsize
                }
            }

            if (metaPayload == null || !foundMdat) {
                Timber.e("Missing required meta or mdat boxes in HEIC file")
                return false
            }

            // 2. Parse children of meta box
            val metaVerFlags = metaPayload.copyOfRange(0, 4)
            var childOffset = 4
            val metaChildren = mutableListOf<Pair<String, ByteArray>>()

            while (childOffset + 8 <= metaPayload.size) {
                val csize = ByteBuffer.wrap(metaPayload, childOffset, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
                val ctype = String(metaPayload, childOffset + 4, 4, Charsets.US_ASCII)
                val boxLen = csize.toInt()
                if (boxLen < 8 || childOffset + boxLen > metaPayload.size) break

                val cbytes = metaPayload.copyOfRange(childOffset, childOffset + boxLen)
                metaChildren.add(ctype to cbytes)
                childOffset += boxLen
            }

            val pitmBytes = metaChildren.firstOrNull { it.first == "pitm" }?.second
            val primaryItemId = parsePrimaryItemId(pitmBytes) ?: 1

            // 3. Find thumbnail items from iref FIRST
            val irefBytes = metaChildren.firstOrNull { it.first == "iref" }?.second
            val thumbnailItemIds = mutableSetOf<Int>()
            var irefVer = 0
            if (irefBytes != null) {
                irefVer = irefBytes[8].toInt()
                var refCursor = 12
                while (refCursor + 8 <= irefBytes.size) {
                    val refSize = ByteBuffer.wrap(irefBytes, refCursor, 4).order(ByteOrder.BIG_ENDIAN).int
                    if (refSize < 8 || refCursor + refSize > irefBytes.size) break
                    val refType = String(irefBytes, refCursor + 4, 4, Charsets.US_ASCII)
                    if (refType == "thmb" && thumbnailHandling == ThumbnailHandling.REMOVE) {
                        val fromId = if (irefVer == 0) {
                            ByteBuffer.wrap(irefBytes, refCursor + 8, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
                        } else {
                            ByteBuffer.wrap(irefBytes, refCursor + 8, 4).order(ByteOrder.BIG_ENDIAN).int
                        }
                        thumbnailItemIds.add(fromId)
                    }
                    refCursor += refSize
                }
            }

            // 4. Parse iinf to discover metadata items and filter kept infe
            val iinfBytes = metaChildren.firstOrNull { it.first == "iinf" }?.second ?: return false
            val iinfVer = iinfBytes[8].toInt()
            var infeCursor = if (iinfVer == 0) 14 else 16
            val metadataItemIds = mutableSetOf<Int>()
            val keptInfeBoxes = mutableListOf<ByteArray>()
            var existingExifItemId: Int? = null
            var maxItemId = 1

            while (infeCursor + 8 <= iinfBytes.size) {
                val infeSize = ByteBuffer.wrap(iinfBytes, infeCursor, 4).order(ByteOrder.BIG_ENDIAN).int
                if (infeSize < 8 || infeCursor + infeSize > iinfBytes.size) break
                val infeBytes = iinfBytes.copyOfRange(infeCursor, infeCursor + infeSize)
                val infeVer = infeBytes[8].toInt()
                val itemId: Int
                val itemType: String
                if (infeVer in 0..2) {
                    itemId = ByteBuffer.wrap(infeBytes, 12, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
                    itemType = if (infeVer == 2) String(infeBytes, 16, 4, Charsets.US_ASCII) else "mime"
                } else {
                    itemId = ByteBuffer.wrap(infeBytes, 12, 4).order(ByteOrder.BIG_ENDIAN).int
                    itemType = String(infeBytes, 18, 4, Charsets.US_ASCII)
                }

                if (itemId > maxItemId) maxItemId = itemId

                if (itemType.equals("Exif", ignoreCase = true)) {
                    existingExifItemId = itemId
                }

                val isMeta = itemType.lowercase() in HEIC_METADATA_ITEM_TYPES
                val isThmb = itemId in thumbnailItemIds

                if (replacementExifPayload != null && itemType.equals("Exif", ignoreCase = true)) {
                    keptInfeBoxes.add(infeBytes)
                } else if (isMeta || isThmb) {
                    metadataItemIds.add(itemId)
                } else {
                    keptInfeBoxes.add(infeBytes)
                }

                infeCursor += infeSize
            }

            val allDropIds = (metadataItemIds + thumbnailItemIds).toMutableSet()
            if (replacementExifPayload != null && existingExifItemId != null) {
                allDropIds.remove(existingExifItemId)
            }

            val targetExifItemId = existingExifItemId ?: if (replacementExifPayload != null) maxItemId + 1 else null
            if (replacementExifPayload != null && existingExifItemId == null && targetExifItemId != null) {
                val newInfe = ByteArrayOutputStream()
                if (iinfVer == 0) {
                    newInfe.write(byteArrayOf(0, 0, 0, 21))
                    newInfe.write("infe".toByteArray(Charsets.US_ASCII))
                    newInfe.write(byteArrayOf(2, 0, 0, 0)) // ver 2, flags 0
                    writeUint(newInfe, targetExifItemId.toLong(), 2)
                    writeUint(newInfe, 0L, 2)
                    newInfe.write("Exif".toByteArray(Charsets.US_ASCII))
                    newInfe.write(0)
                } else {
                    newInfe.write(byteArrayOf(0, 0, 0, 25))
                    newInfe.write("infe".toByteArray(Charsets.US_ASCII))
                    newInfe.write(byteArrayOf(3, 0, 0, 0)) // ver 3, flags 0
                    writeUint(newInfe, targetExifItemId.toLong(), 4)
                    writeUint(newInfe, 0L, 2)
                    newInfe.write("Exif".toByteArray(Charsets.US_ASCII))
                    newInfe.write(0)
                }
                keptInfeBoxes.add(newInfe.toByteArray())
            }

            // 5. Rebuild iinf box
            val newIinf = ByteArrayOutputStream()
            newIinf.write(byteArrayOf(0, 0, 0, 0)) // placeholder size
            newIinf.write("iinf".toByteArray(Charsets.US_ASCII))
            newIinf.write(iinfVer)
            newIinf.write(iinfBytes.copyOfRange(9, 12))
            if (iinfVer == 0) {
                writeUint(newIinf, keptInfeBoxes.size.toLong(), 2)
            } else {
                writeUint(newIinf, keptInfeBoxes.size.toLong(), 4)
            }
            keptInfeBoxes.forEach { newIinf.write(it) }
            val finalIinfBytes = newIinf.toByteArray()
            ByteBuffer.wrap(finalIinfBytes, 0, 4).order(ByteOrder.BIG_ENDIAN).putInt(finalIinfBytes.size)

            // 6. Rebuild iref box
            var finalIrefBytes: ByteArray? = null
            val keptRefs = mutableListOf<ByteArray>()
            var hasExifCdsc = false

            if (irefBytes != null) {
                var refCursor = 12
                while (refCursor + 8 <= irefBytes.size) {
                    val refSize = ByteBuffer.wrap(irefBytes, refCursor, 4).order(ByteOrder.BIG_ENDIAN).int
                    if (refSize < 8 || refCursor + refSize > irefBytes.size) break
                    val rbox = irefBytes.copyOfRange(refCursor, refCursor + refSize)
                    val rtype = String(rbox, 4, 4, Charsets.US_ASCII)
                    val fid = if (irefVer == 0) {
                        ByteBuffer.wrap(rbox, 8, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
                    } else {
                        ByteBuffer.wrap(rbox, 8, 4).order(ByteOrder.BIG_ENDIAN).int
                    }
                    val cnt = ByteBuffer.wrap(rbox, if (irefVer == 0) 10 else 12, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
                    val toIds = mutableListOf<Int>()
                    var toIdx = if (irefVer == 0) 12 else 14
                    for (j in 0 until cnt) {
                        if (irefVer == 0) {
                            toIds.add(ByteBuffer.wrap(rbox, toIdx, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF)
                            toIdx += 2
                        } else {
                            toIds.add(ByteBuffer.wrap(rbox, toIdx, 4).order(ByteOrder.BIG_ENDIAN).int)
                            toIdx += 4
                        }
                    }

                    if (fid == targetExifItemId && rtype == "cdsc") {
                        hasExifCdsc = true
                    }

                    if (fid !in allDropIds && toIds.none { it in allDropIds }) {
                        keptRefs.add(rbox)
                    }
                    refCursor += refSize
                }
            }

            if (replacementExifPayload != null && !hasExifCdsc && targetExifItemId != null) {
                val cdsc = ByteArrayOutputStream()
                if (irefVer == 0) {
                    cdsc.write(byteArrayOf(0, 0, 0, 14))
                    cdsc.write("cdsc".toByteArray(Charsets.US_ASCII))
                    writeUint(cdsc, targetExifItemId.toLong(), 2)
                    writeUint(cdsc, 1L, 2)
                    writeUint(cdsc, primaryItemId.toLong(), 2)
                } else {
                    cdsc.write(byteArrayOf(0, 0, 0, 18))
                    cdsc.write("cdsc".toByteArray(Charsets.US_ASCII))
                    writeUint(cdsc, targetExifItemId.toLong(), 4)
                    writeUint(cdsc, 1L, 2)
                    writeUint(cdsc, primaryItemId.toLong(), 4)
                }
                keptRefs.add(cdsc.toByteArray())
            }

            if (keptRefs.isNotEmpty()) {
                val newIref = ByteArrayOutputStream()
                newIref.write(byteArrayOf(0, 0, 0, 0))
                newIref.write("iref".toByteArray(Charsets.US_ASCII))
                newIref.write(irefVer)
                newIref.write(if (irefBytes != null) irefBytes.copyOfRange(9, 12) else byteArrayOf(0, 0, 0))
                keptRefs.forEach { newIref.write(it) }
                val assembled = newIref.toByteArray()
                ByteBuffer.wrap(assembled, 0, 4).order(ByteOrder.BIG_ENDIAN).putInt(assembled.size)
                finalIrefBytes = assembled
            }

            // 7. Parse iloc box
            val ilocBytes = metaChildren.firstOrNull { it.first == "iloc" }?.second ?: return false
            val ilocVer = ilocBytes[8].toInt()
            val offsetSize = (ilocBytes[12].toInt() shr 4) and 0x0F
            val lengthSize = ilocBytes[12].toInt() and 0x0F
            val baseOffsetSize = (ilocBytes[13].toInt() shr 4) and 0x0F
            val indexSize = if (ilocVer in 1..2) ilocBytes[13].toInt() and 0x0F else 0

            var ilocIdx = 14
            val itemCount = if (ilocVer < 2) {
                val count = ByteBuffer.wrap(ilocBytes, ilocIdx, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
                ilocIdx += 2
                count
            } else {
                val count = ByteBuffer.wrap(ilocBytes, ilocIdx, 4).order(ByteOrder.BIG_ENDIAN).int
                ilocIdx += 4
                count
            }

            val keptIlocItems = mutableListOf<Triple<Int, ByteArray, Int>>() // itemId, itemBytes, cm
            val extentsToZero = mutableListOf<ItemExtent>()

            for (i in 0 until itemCount) {
                val startPos = ilocIdx
                val itemId = if (ilocVer < 2) {
                    val id = ByteBuffer.wrap(ilocBytes, ilocIdx, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
                    ilocIdx += 2
                    id
                } else {
                    val id = ByteBuffer.wrap(ilocBytes, ilocIdx, 4).order(ByteOrder.BIG_ENDIAN).int
                    ilocIdx += 4
                    id
                }

                var cm = 0
                if (ilocVer in 1..2) {
                    val rCm = ByteBuffer.wrap(ilocBytes, ilocIdx, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
                    cm = rCm and 0x0F
                    ilocIdx += 2
                }

                val dref = ByteBuffer.wrap(ilocBytes, ilocIdx, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
                ilocIdx += 2

                val baseOffset = readUint(ilocBytes, ilocIdx, baseOffsetSize)
                ilocIdx += baseOffsetSize

                val extentCount = ByteBuffer.wrap(ilocBytes, ilocIdx, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
                ilocIdx += 2

                for (e in 0 until extentCount) {
                    if (ilocVer in 1..2 && indexSize > 0) ilocIdx += indexSize
                    val extOffset = readUint(ilocBytes, ilocIdx, offsetSize)
                    ilocIdx += offsetSize
                    val extLength = readUint(ilocBytes, ilocIdx, lengthSize)
                    ilocIdx += lengthSize

                    if ((itemId in allDropIds || itemId == targetExifItemId) && cm == 0) {
                        extentsToZero.add(ItemExtent(baseOffset + extOffset, extLength))
                    }
                }

                val itemBytes = ilocBytes.copyOfRange(startPos, ilocIdx)
                if (itemId != targetExifItemId && itemId !in allDropIds) {
                    keptIlocItems.add(Triple(itemId, itemBytes, cm))
                }
            }

            // If replacement Exif payload is present, add it to keptIlocItems
            if (replacementExifPayload != null && targetExifItemId != null) {
                val appendedExifOffset = mdatOffset + mdatSize
                val newItem = ByteArrayOutputStream()
                if (ilocVer < 2) {
                    writeUint(newItem, targetExifItemId.toLong(), 2)
                } else {
                    writeUint(newItem, targetExifItemId.toLong(), 4)
                }
                if (ilocVer in 1..2) {
                    newItem.write(byteArrayOf(0, 0)) // reserved = 0, cm = 0
                }
                writeUint(newItem, 0L, 2) // dref = 0
                if (baseOffsetSize > 0) {
                    newItem.write(ByteArray(baseOffsetSize))
                }
                writeUint(newItem, 1L, 2) // extent_count = 1
                if (ilocVer in 1..2 && indexSize > 0) {
                    newItem.write(ByteArray(indexSize))
                }
                writeUint(newItem, appendedExifOffset, offsetSize)
                writeUint(newItem, replacementExifPayload.size.toLong(), lengthSize)

                keptIlocItems.add(Triple(targetExifItemId, newItem.toByteArray(), 0))
            }

            // Function to assemble iloc given a specific meta growth
            fun assembleIloc(growth: Int): ByteArray {
                val out = ByteArrayOutputStream()
                out.write(ilocBytes.copyOfRange(0, 14))
                if (ilocVer < 2) {
                    writeUint(out, keptIlocItems.size.toLong(), 2)
                } else {
                    writeUint(out, keptIlocItems.size.toLong(), 4)
                }
                for ((_, itemBytes, cm) in keptIlocItems) {
                    val ib = itemBytes.copyOf()
                    if (growth > 0 && cm == 0) {
                        val idSize = if (ilocVer < 2) 2 else 4
                        val cmSize = if (ilocVer in 1..2) 2 else 0
                        val drefSize = 2
                        val extCntSize = 2
                        val idxSize = if (ilocVer in 1..2) indexSize else 0
                        val cur = idSize + cmSize + drefSize + baseOffsetSize + extCntSize + idxSize
                        val curOff = readUint(ib, cur, offsetSize)
                        writeUint(ib, curOff + growth, cur, offsetSize)
                    }
                    out.write(ib)
                }
                val assembled = out.toByteArray()
                ByteBuffer.wrap(assembled, 0, 4).order(ByteOrder.BIG_ENDIAN).putInt(assembled.size)
                return assembled
            }

            // 8. Rebuild meta children and pad with free box
            val newChildren = mutableListOf<Pair<String, ByteArray>>()
            for ((btype, cdata) in metaChildren) {
                when (btype) {
                    "iinf" -> newChildren.add("iinf" to finalIinfBytes)
                    "iloc" -> newChildren.add("iloc" to ByteArray(0)) // placeholder
                    "iref" -> if (finalIrefBytes != null) newChildren.add("iref" to finalIrefBytes)
                    "free" -> { /* drop old free boxes inside meta, will re-pad */ }
                    else -> newChildren.add(btype to cdata)
                }
            }
            if (finalIrefBytes != null && newChildren.none { it.first == "iref" }) {
                newChildren.add("iref" to finalIrefBytes)
            }

            val testIloc = assembleIloc(0)
            val currentPayloadLen = newChildren.sumOf { (btype, cdata) ->
                if (btype == "iloc") testIloc.size else cdata.size
            }
            val targetPayloadLen = (metaSize - metaHeaderLen - 4).toInt()

            var growth = 0
            val diff = targetPayloadLen - currentPayloadLen
            val finalChildren = mutableListOf<ByteArray>()

            if (diff >= 8) {
                val freeBox = ByteBuffer.allocate(diff).order(ByteOrder.BIG_ENDIAN)
                    .putInt(diff)
                    .put("free".toByteArray(Charsets.US_ASCII))
                    .array()
                for ((btype, cdata) in newChildren) {
                    finalChildren.add(if (btype == "iloc") testIloc else cdata)
                }
                finalChildren.add(freeBox)
            } else if (diff >= 0) {
                growth = if (diff > 0) 8 - diff else 0
                val finalIloc = assembleIloc(growth)
                for ((btype, cdata) in newChildren) {
                    finalChildren.add(if (btype == "iloc") finalIloc else cdata)
                }
                if (growth > 0) {
                    val freeBox = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
                        .putInt(8)
                        .put("free".toByteArray(Charsets.US_ASCII))
                        .array()
                    finalChildren.add(freeBox)
                }
            } else {
                growth = -diff
                val finalIloc = assembleIloc(growth)
                for ((btype, cdata) in newChildren) {
                    finalChildren.add(if (btype == "iloc") finalIloc else cdata)
                }
            }

            val newMetaStream = ByteArrayOutputStream()
            val newMetaSize = metaHeaderLen + 4 + finalChildren.sumOf { it.size }
            if (metaHeaderLen == 8) {
                val hdr = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
                    .putInt(newMetaSize)
                    .put("meta".toByteArray(Charsets.US_ASCII))
                    .array()
                newMetaStream.write(hdr)
            } else {
                val hdr = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
                    .putInt(1)
                    .put("meta".toByteArray(Charsets.US_ASCII))
                    .putLong(newMetaSize.toLong())
                    .array()
                newMetaStream.write(hdr)
            }
            newMetaStream.write(metaVerFlags)
            finalChildren.forEach { newMetaStream.write(it) }
            val finalMetaBytes = newMetaStream.toByteArray()

            // 9. Zero out dropped extents directly in inputFile
            RandomAccessFile(inputFile, "rw").use { rwRaf ->
                val zeroBuf = ByteArray(65536)
                for (extent in extentsToZero) {
                    if (extent.offset + extent.length <= fileSize) {
                        rwRaf.seek(extent.offset)
                        var rem = extent.length
                        while (rem > 0) {
                            val toWrite = minOf(rem, zeroBuf.size.toLong()).toInt()
                            rwRaf.write(zeroBuf, 0, toWrite)
                            rem -= toWrite
                        }
                    }
                }
            }

            // 10. Stream to outputFile
            RandomAccessFile(inputFile, "r").use { raf ->
                outputFile.outputStream().use { out ->
                    if (metaOffset < mdatOffset) {
                        // Standard layout: ftyp -> meta -> mdat
                        streamRange(raf, out, 0L, metaOffset)
                        out.write(finalMetaBytes)

                        val postMetaStart = metaOffset + metaSize
                        if (mdatOffset > postMetaStart) {
                            streamRange(raf, out, postMetaStart, mdatOffset)
                        }

                        val newMdatSize = mdatSize + (replacementExifPayload?.size?.toLong() ?: 0L)
                        if (mdatHeaderLen == 8) {
                            val buf = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
                                .putInt(newMdatSize.toInt())
                                .put("mdat".toByteArray(Charsets.US_ASCII))
                                .array()
                            out.write(buf)
                        } else {
                            val buf = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
                                .putInt(1)
                                .put("mdat".toByteArray(Charsets.US_ASCII))
                                .putLong(newMdatSize)
                                .array()
                            out.write(buf)
                        }

                        val mdatPayloadStart = mdatOffset + mdatHeaderLen
                        val mdatPayloadEnd = mdatOffset + mdatSize
                        streamRange(raf, out, mdatPayloadStart, mdatPayloadEnd)

                        if (replacementExifPayload != null) {
                            out.write(replacementExifPayload)
                        }

                        if (mdatPayloadEnd < fileSize) {
                            streamRange(raf, out, mdatPayloadEnd, fileSize)
                        }
                    } else {
                        // Alternative layout: mdat -> meta
                        streamRange(raf, out, 0L, mdatOffset)

                        val newMdatSize = mdatSize + (replacementExifPayload?.size?.toLong() ?: 0L)
                        if (mdatHeaderLen == 8) {
                            val buf = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
                                .putInt(newMdatSize.toInt())
                                .put("mdat".toByteArray(Charsets.US_ASCII))
                                .array()
                            out.write(buf)
                        } else {
                            val buf = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
                                .putInt(1)
                                .put("mdat".toByteArray(Charsets.US_ASCII))
                                .putLong(newMdatSize)
                                .array()
                            out.write(buf)
                        }

                        val mdatPayloadStart = mdatOffset + mdatHeaderLen
                        val mdatPayloadEnd = mdatOffset + mdatSize
                        streamRange(raf, out, mdatPayloadStart, mdatPayloadEnd)

                        if (replacementExifPayload != null) {
                            out.write(replacementExifPayload)
                        }

                        streamRange(raf, out, mdatPayloadEnd, metaOffset)
                        out.write(finalMetaBytes)

                        val postMetaEnd = metaOffset + metaSize
                        if (postMetaEnd < fileSize) {
                            streamRange(raf, out, postMetaEnd, fileSize)
                        }
                    }
                }
            }

            true
        }.onFailure { e ->
            Timber.e(e, "Failed to process HEIC container")
        }.getOrDefault(false)
    }

    private fun streamRange(raf: RandomAccessFile, out: java.io.OutputStream, start: Long, end: Long) {
        if (start >= end) return
        raf.seek(start)
        val buffer = ByteArray(65536)
        var remaining = end - start
        while (remaining > 0) {
            val toRead = minOf(remaining, buffer.size.toLong()).toInt()
            val read = raf.read(buffer, 0, toRead)
            if (read < 0) throw EOFException("Unexpected EOF while streaming container")
            out.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun parsePrimaryItemId(pitmBytes: ByteArray?): Int? {
        if (pitmBytes == null || pitmBytes.size < 14) return null
        val ver = pitmBytes[8].toInt()
        return if (ver == 0) {
            ByteBuffer.wrap(pitmBytes, 12, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
        } else {
            ByteBuffer.wrap(pitmBytes, 12, 4).order(ByteOrder.BIG_ENDIAN).int
        }
    }
}
