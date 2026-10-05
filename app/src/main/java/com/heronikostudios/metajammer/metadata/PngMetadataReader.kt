package com.heronikostudios.metajammer.metadata

import com.heronikostudios.metajammer.domain.model.MetadataEntry
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.zip.InflaterInputStream
import kotlin.math.roundToInt

data class PngMetadataInfo(
    val width: Int? = null,
    val height: Int? = null,
    val entries: List<MetadataEntry> = emptyList()
)

object PngMetadataReader {
    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    fun readMetadata(bytes: ByteArray): PngMetadataInfo {
        return readMetadata(java.io.ByteArrayInputStream(bytes))
    }

    fun readMetadata(inputStream: java.io.InputStream): PngMetadataInfo {
        val sig = ByteArray(8)
        if (!readFully(inputStream, sig) || !sig.contentEquals(PNG_SIGNATURE)) return PngMetadataInfo()

        var width: Int? = null
        var height: Int? = null
        val entries = mutableListOf<MetadataEntry>()
        val headerBuf = ByteArray(8)

        while (readFully(inputStream, headerBuf)) {
            val length = ByteBuffer.wrap(headerBuf, 0, 4).order(ByteOrder.BIG_ENDIAN).int
            if (length < 0) break
            val chunkType = String(headerBuf, 4, 4, StandardCharsets.US_ASCII)

            when (chunkType) {
                "IHDR" -> {
                    if (length >= 8) {
                        val data = ByteArray(length)
                        if (!readFully(inputStream, data)) break
                        width = ByteBuffer.wrap(data, 0, 4).order(ByteOrder.BIG_ENDIAN).int
                        height = ByteBuffer.wrap(data, 4, 4).order(ByteOrder.BIG_ENDIAN).int
                        skipFully(inputStream, 4L)
                    } else {
                        skipFully(inputStream, length + 4L)
                    }
                }
                "tEXt" -> {
                    val data = ByteArray(length)
                    if (!readFully(inputStream, data)) break
                    parseTextChunk(data, 0, length)?.let { (key, value) ->
                        if (value.isNotBlank()) entries.add(MetadataEntry(key, value))
                    }
                    skipFully(inputStream, 4L)
                }
                "iTXt" -> {
                    val data = ByteArray(length)
                    if (!readFully(inputStream, data)) break
                    parseItxtChunk(data, 0, length)?.let { (key, value) ->
                        if (value.isNotBlank() && !key.startsWith("XML:com.adobe.xmp", ignoreCase = true)) {
                            entries.add(MetadataEntry(key, value))
                        }
                    }
                    skipFully(inputStream, 4L)
                }
                "pHYs" -> {
                    if (length >= 9) {
                        val data = ByteArray(length)
                        if (!readFully(inputStream, data)) break
                        val ppuX = ByteBuffer.wrap(data, 0, 4).order(ByteOrder.BIG_ENDIAN).int
                        val ppuY = ByteBuffer.wrap(data, 4, 4).order(ByteOrder.BIG_ENDIAN).int
                        val unit = data[8].toInt() and 0xFF
                        if (unit == 1) {
                            val dpiX = (ppuX * 0.0254).roundToInt()
                            val dpiY = (ppuY * 0.0254).roundToInt()
                            entries.add(MetadataEntry("Resolution", "${dpiX}x${dpiY} DPI (${ppuX}x${ppuY} ppm)"))
                        } else {
                            entries.add(MetadataEntry("PixelAspectRatio", "${ppuX}:${ppuY}"))
                        }
                        skipFully(inputStream, 4L)
                    } else {
                        skipFully(inputStream, length + 4L)
                    }
                }
                "tIME" -> {
                    if (length >= 7) {
                        val data = ByteArray(length)
                        if (!readFully(inputStream, data)) break
                        val year = ByteBuffer.wrap(data, 0, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
                        val month = data[2].toInt() and 0xFF
                        val day = data[3].toInt() and 0xFF
                        val hour = data[4].toInt() and 0xFF
                        val min = data[5].toInt() and 0xFF
                        val sec = data[6].toInt() and 0xFF
                        val timeStr = String.format("%04d:%02d:%02d %02d:%02d:%02d", year, month, day, hour, min, sec)
                        entries.add(MetadataEntry("DateTimeModified", timeStr))
                        skipFully(inputStream, 4L)
                    } else {
                        skipFully(inputStream, length + 4L)
                    }
                }
                "IEND" -> break
                else -> {
                    if (!skipFully(inputStream, length + 4L)) break
                }
            }
        }

        return PngMetadataInfo(width, height, entries)
    }

    private fun readFully(input: java.io.InputStream, buffer: ByteArray, offset: Int = 0, length: Int = buffer.size): Boolean {
        var total = 0
        while (total < length) {
            val count = input.read(buffer, offset + total, length - total)
            if (count < 0) return false
            total += count
        }
        return true
    }

    private fun skipFully(input: java.io.InputStream, bytesToSkip: Long): Boolean {
        var remaining = bytesToSkip
        val skipBuf = ByteArray(minOf(remaining, 8192L).toInt())
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else {
                val read = input.read(skipBuf, 0, minOf(remaining, skipBuf.size.toLong()).toInt())
                if (read < 0) return false
                remaining -= read
            }
        }
        return true
    }

    private fun parseTextChunk(bytes: ByteArray, offset: Int, length: Int): Pair<String, String>? {
        var nullIndex = -1
        for (i in 0 until length) {
            if (bytes[offset + i] == 0.toByte()) {
                nullIndex = i
                break
            }
        }
        if (nullIndex <= 0) return null
        val keyword = String(bytes, offset, nullIndex, StandardCharsets.ISO_8859_1)
        val text = String(bytes, offset + nullIndex + 1, length - nullIndex - 1, StandardCharsets.ISO_8859_1)
        return keyword to text
    }

    private fun parseItxtChunk(bytes: ByteArray, offset: Int, length: Int): Pair<String, String>? {
        var nullIndex = -1
        for (i in 0 until length) {
            if (bytes[offset + i] == 0.toByte()) {
                nullIndex = i
                break
            }
        }
        if (nullIndex <= 0 || nullIndex + 5 > length) return null
        val keyword = String(bytes, offset, nullIndex, StandardCharsets.ISO_8859_1)
        val compressionFlag = bytes[offset + nullIndex + 1].toInt()
        val compressionMethod = bytes[offset + nullIndex + 2].toInt()

        var langNull = -1
        for (i in (nullIndex + 3) until length) {
            if (bytes[offset + i] == 0.toByte()) {
                langNull = i
                break
            }
        }
        if (langNull < 0) return null

        var transNull = -1
        for (i in (langNull + 1) until length) {
            if (bytes[offset + i] == 0.toByte()) {
                transNull = i
                break
            }
        }
        if (transNull < 0) return null

        val textStart = transNull + 1
        val textLength = length - textStart
        if (textLength < 0) return null

        val text = if (compressionFlag == 1 && compressionMethod == 0) {
            runCatching {
                val bis = java.io.ByteArrayInputStream(bytes, offset + textStart, textLength)
                InflaterInputStream(bis).use { it.readBytes().toString(StandardCharsets.UTF_8) }
            }.getOrNull() ?: ""
        } else {
            String(bytes, offset + textStart, textLength, StandardCharsets.UTF_8)
        }

        return keyword to text
    }
}
