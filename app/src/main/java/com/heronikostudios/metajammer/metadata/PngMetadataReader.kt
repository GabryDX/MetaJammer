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
        if (bytes.size < 8) return PngMetadataInfo()
        for (i in 0 until 8) {
            if (bytes[i] != PNG_SIGNATURE[i]) return PngMetadataInfo()
        }

        var width: Int? = null
        var height: Int? = null
        val entries = mutableListOf<MetadataEntry>()

        var offset = 8
        while (offset + 8 <= bytes.size) {
            val length = ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.BIG_ENDIAN).int
            val chunkType = String(bytes, offset + 4, 4, StandardCharsets.US_ASCII)
            val chunkDataOffset = offset + 8

            if (length < 0 || chunkDataOffset + length > bytes.size) {
                break
            }

            when (chunkType) {
                "IHDR" -> {
                    if (length >= 8) {
                        width = ByteBuffer.wrap(bytes, chunkDataOffset, 4).order(ByteOrder.BIG_ENDIAN).int
                        height = ByteBuffer.wrap(bytes, chunkDataOffset + 4, 4).order(ByteOrder.BIG_ENDIAN).int
                    }
                }
                "tEXt" -> {
                    parseTextChunk(bytes, chunkDataOffset, length)?.let { (key, value) ->
                        if (value.isNotBlank()) entries.add(MetadataEntry(key, value))
                    }
                }
                "iTXt" -> {
                    parseItxtChunk(bytes, chunkDataOffset, length)?.let { (key, value) ->
                        if (value.isNotBlank() && !key.startsWith("XML:com.adobe.xmp", ignoreCase = true)) {
                            entries.add(MetadataEntry(key, value))
                        }
                    }
                }
                "pHYs" -> {
                    if (length >= 9) {
                        val ppuX = ByteBuffer.wrap(bytes, chunkDataOffset, 4).order(ByteOrder.BIG_ENDIAN).int
                        val ppuY = ByteBuffer.wrap(bytes, chunkDataOffset + 4, 4).order(ByteOrder.BIG_ENDIAN).int
                        val unit = bytes[chunkDataOffset + 8].toInt() and 0xFF
                        if (unit == 1) {
                            val dpiX = (ppuX * 0.0254).roundToInt()
                            val dpiY = (ppuY * 0.0254).roundToInt()
                            entries.add(MetadataEntry("Resolution", "${dpiX}x${dpiY} DPI (${ppuX}x${ppuY} ppm)"))
                        } else {
                            entries.add(MetadataEntry("PixelAspectRatio", "${ppuX}:${ppuY}"))
                        }
                    }
                }
                "tIME" -> {
                    if (length >= 7) {
                        val year = ByteBuffer.wrap(bytes, chunkDataOffset, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
                        val month = bytes[chunkDataOffset + 2].toInt() and 0xFF
                        val day = bytes[chunkDataOffset + 3].toInt() and 0xFF
                        val hour = bytes[chunkDataOffset + 4].toInt() and 0xFF
                        val min = bytes[chunkDataOffset + 5].toInt() and 0xFF
                        val sec = bytes[chunkDataOffset + 6].toInt() and 0xFF
                        val timeStr = String.format("%04d:%02d:%02d %02d:%02d:%02d", year, month, day, hour, min, sec)
                        entries.add(MetadataEntry("DateTimeModified", timeStr))
                    }
                }
                "IEND" -> break
            }

            offset += 8 + length + 4 // 4 len + 4 type + data + 4 crc
        }

        return PngMetadataInfo(width, height, entries)
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
