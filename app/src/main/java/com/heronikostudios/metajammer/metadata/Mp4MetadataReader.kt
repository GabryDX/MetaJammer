package com.heronikostudios.metajammer.metadata

import com.heronikostudios.metajammer.domain.model.MetadataEntry
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Pure Kotlin parser and scrubber for ISO-BMFF / MP4 container metadata boxes.
 * Parses user data (udta), iTunes metadata (meta/ilst), GPS (©xyz), XMP packets, and custom tags.
 */
object Mp4MetadataReader {

    private val TAG_NAME_MAP = mapOf(
        "\u00A9nam" to "Title",
        "\u00A9ART" to "Artist",
        "\u00A9alb" to "Album",
        "\u00A9day" to "Date",
        "\u00A9gen" to "Genre",
        "\u00A9wrt" to "Composer",
        "\u00A9too" to "Software",
        "\u00A9cmt" to "Comment",
        "desc" to "Description",
        "\u00A9grp" to "Grouping",
        "cprt" to "Copyright",
        "covr" to "Cover Art"
    )

    fun readMetadata(inputStream: InputStream): List<MetadataEntry> {
        val entries = mutableListOf<MetadataEntry>()
        val seenKeys = mutableSetOf<String>()

        fun addEntry(key: String, value: String) {
            val trimmedKey = key.trim()
            val trimmedVal = value.trim()
            if (trimmedKey.isNotBlank() && trimmedVal.isNotBlank()) {
                val uniqueKey = "$trimmedKey::$trimmedVal"
                if (seenKeys.add(uniqueKey)) {
                    entries.add(MetadataEntry(trimmedKey, trimmedVal))
                }
            }
        }

        try {
            while (true) {
                val header = readBoxHeader(inputStream) ?: break
                val (boxSize, boxTag, headerLen) = header
                val payloadSize = if (boxSize > 0) boxSize - headerLen else -1L

                when (boxTag) {
                    "moov" -> {
                        if (payloadSize > 0) {
                            parseMoovBox(inputStream, payloadSize, ::addEntry)
                        } else {
                            break
                        }
                    }
                    "uuid" -> {
                        addEntry("UUID Metadata", "Extended container metadata")
                        if (payloadSize > 0) skipFully(inputStream, payloadSize)
                    }
                    else -> {
                        if (payloadSize > 0) {
                            skipFully(inputStream, payloadSize)
                        } else {
                            break
                        }
                    }
                }
            }
        } catch (_: EOFException) {
            // End of stream reached
        } catch (_: Exception) {
            // Non-fatal parse failure
        }

        return entries
    }

    private fun parseMoovBox(stream: InputStream, moovPayloadSize: Long, onEntry: (String, String) -> Unit) {
        var bytesRead = 0L
        while (bytesRead < moovPayloadSize) {
            val header = readBoxHeader(stream) ?: break
            val (boxSize, boxTag, headerLen) = header
            bytesRead += headerLen
            val payloadSize = boxSize - headerLen

            if (boxTag == "udta") {
                if (payloadSize in 1..20_000_000) {
                    val udtaBytes = readBytesExact(stream, payloadSize.toInt())
                    bytesRead += payloadSize
                    parseUdtaPayload(udtaBytes, onEntry)
                } else if (payloadSize > 0) {
                    skipFully(stream, payloadSize)
                    bytesRead += payloadSize
                }
            } else if (boxTag == "uuid") {
                onEntry("UUID Metadata", "Extended moov metadata")
                if (payloadSize > 0) {
                    skipFully(stream, payloadSize)
                    bytesRead += payloadSize
                }
            } else {
                if (payloadSize > 0) {
                    skipFully(stream, payloadSize)
                    bytesRead += payloadSize
                }
            }
        }
    }

    private fun parseUdtaPayload(bytes: ByteArray, onEntry: (String, String) -> Unit) {
        val stream = ByteArrayInputStream(bytes)
        while (true) {
            val header = readBoxHeader(stream) ?: break
            val (boxSize, boxTag, headerLen) = header
            val payloadSize = (boxSize - headerLen).toInt()
            if (payloadSize < 0 || payloadSize > stream.available()) break
            val payload = readBytesExact(stream, payloadSize)

            when (boxTag) {
                "meta" -> {
                    // meta box has 4 bytes version/flags before child boxes
                    val metaPayload = if (payload.size >= 4) payload.copyOfRange(4, payload.size) else payload
                    parseMetaPayload(metaPayload, onEntry)
                }
                "\u00A9xyz", "xyz" -> {
                    // ISO 6709 coordinates (e.g. +46.2044+006.1432+375.40/)
                    if (payload.size >= 4) {
                        // Skip 2 bytes byte-count/language and read text
                        val text = String(payload, 4, payload.size - 4, StandardCharsets.UTF_8).trimEnd('/')
                        if (text.isNotBlank()) onEntry("Location", text)
                    }
                }
                "titl" -> parseUdtaText(payload)?.let { onEntry("Title", it) }
                "auth" -> parseUdtaText(payload)?.let { onEntry("Artist", it) }
                "cprt" -> parseUdtaText(payload)?.let { onEntry("Copyright", it) }
                "dscp" -> parseUdtaText(payload)?.let { onEntry("Description", it) }
                "XMP_" -> onEntry("XMP Metadata", "Adobe XMP Packet (${payload.size} bytes)")
            }
        }
    }

    private fun parseMetaPayload(bytes: ByteArray, onEntry: (String, String) -> Unit) {
        val stream = ByteArrayInputStream(bytes)
        while (true) {
            val header = readBoxHeader(stream) ?: break
            val (boxSize, boxTag, headerLen) = header
            val payloadSize = (boxSize - headerLen).toInt()
            if (payloadSize < 0 || payloadSize > stream.available()) break
            val payload = readBytesExact(stream, payloadSize)

            if (boxTag == "ilst") {
                parseIlstPayload(payload, onEntry)
            }
        }
    }

    private fun parseIlstPayload(bytes: ByteArray, onEntry: (String, String) -> Unit) {
        val stream = ByteArrayInputStream(bytes)
        while (true) {
            val header = readBoxHeader(stream) ?: break
            val (boxSize, boxTag, headerLen) = header
            val payloadSize = (boxSize - headerLen).toInt()
            if (payloadSize < 0 || payloadSize > stream.available()) break
            val payload = readBytesExact(stream, payloadSize)

            if (boxTag == "----") {
                // Freeform iTunes custom metadata: contains 'mean', 'name', and 'data' boxes
                parseFreeformCustomTag(payload, onEntry)
            } else {
                val label = TAG_NAME_MAP[boxTag] ?: boxTag
                parseStandardDataBox(payload)?.let { value ->
                    onEntry(label, value)
                }
            }
        }
    }

    private fun parseStandardDataBox(payload: ByteArray): String? {
        val stream = ByteArrayInputStream(payload)
        while (true) {
            val header = readBoxHeader(stream) ?: break
            val (boxSize, boxTag, headerLen) = header
            val payloadSize = (boxSize - headerLen).toInt()
            if (payloadSize < 0 || payloadSize > stream.available()) break
            val body = readBytesExact(stream, payloadSize)

            if (boxTag == "data" && body.size >= 8) {
                // First 4 bytes: type indicator (1 = UTF-8, 13 = JPEG, 14 = PNG, 21 = integer)
                // Next 4 bytes: locale/country
                val type = ByteBuffer.wrap(body, 0, 4).order(ByteOrder.BIG_ENDIAN).int
                val valueBytes = body.copyOfRange(8, body.size)

                return when (type) {
                    13, 14 -> "Embedded Image (${valueBytes.size} bytes)"
                    1 -> String(valueBytes, StandardCharsets.UTF_8).trim()
                    else -> {
                        val text = String(valueBytes, StandardCharsets.UTF_8).trim()
                        if (text.any { it.isLetterOrDigit() }) text else null
                    }
                }
            }
        }
        return null
    }

    private fun parseFreeformCustomTag(payload: ByteArray, onEntry: (String, String) -> Unit) {
        val stream = ByteArrayInputStream(payload)
        var customName: String? = null
        var customValue: String? = null

        while (true) {
            val header = readBoxHeader(stream) ?: break
            val (boxSize, boxTag, headerLen) = header
            val payloadSize = (boxSize - headerLen).toInt()
            if (payloadSize < 0 || payloadSize > stream.available()) break
            val body = readBytesExact(stream, payloadSize)

            when (boxTag) {
                "name" -> {
                    if (body.size >= 4) {
                        customName = String(body, 4, body.size - 4, StandardCharsets.UTF_8).trim()
                    }
                }
                "data" -> {
                    if (body.size >= 8) {
                        customValue = String(body, 8, body.size - 8, StandardCharsets.UTF_8).trim()
                    }
                }
            }
        }

        if (!customName.isNullOrBlank() && !customValue.isNullOrBlank()) {
            onEntry(customName, customValue)
        }
    }

    private fun parseUdtaText(payload: ByteArray): String? {
        if (payload.size <= 4) return null
        return String(payload, 4, payload.size - 4, StandardCharsets.UTF_8).trim().ifBlank { null }
    }

    /**
     * Strips ISO-BMFF metadata at container level by replacing 'udta' and metadata 'uuid' boxes
     * with standard 'free' (padding) boxes. This completely scrubs all container metadata
     * (including ilst, meta, udta, comments, GPS, XMP) without modifying video/audio tracks
     * or changing chunk offsets (stco/co64).
     */
    fun stripContainerMetadata(input: InputStream, output: OutputStream): Boolean {
        var strippedAny = false

        while (true) {
            val headerBytes = ByteArray(8)
            val read = readFullyOrEof(input, headerBytes)
            if (read < 8) {
                if (read > 0) output.write(headerBytes, 0, read)
                break
            }

            val size = ByteBuffer.wrap(headerBytes, 0, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
            val tag = String(headerBytes, 4, 4, StandardCharsets.ISO_8859_1)

            if (size == 1L) {
                // 64-bit size
                val extBytes = ByteArray(8)
                input.read(extBytes)
                val extSize = ByteBuffer.wrap(extBytes).order(ByteOrder.BIG_ENDIAN).long
                val payloadSize = extSize - 16L
                if (tag == "moov") {
                    output.write(headerBytes)
                    output.write(extBytes)
                    strippedAny = stripMoovContainer(input, output, payloadSize) || strippedAny
                } else if (tag == "uuid") {
                    writeFreeBox(output, extSize, ext = true)
                    skipFully(input, payloadSize)
                    strippedAny = true
                } else {
                    output.write(headerBytes)
                    output.write(extBytes)
                    copyBytes(input, output, payloadSize)
                }
            } else if (size == 0L) {
                // Extends to EOF
                output.write(headerBytes)
                input.copyTo(output)
                break
            } else {
                val payloadSize = size - 8L
                if (tag == "moov") {
                    output.write(headerBytes)
                    strippedAny = stripMoovContainer(input, output, payloadSize) || strippedAny
                } else if (tag == "uuid") {
                    writeFreeBox(output, size, ext = false)
                    skipFully(input, payloadSize)
                    strippedAny = true
                } else {
                    output.write(headerBytes)
                    copyBytes(input, output, payloadSize)
                }
            }
        }

        return strippedAny
    }

    private fun stripMoovContainer(input: InputStream, output: OutputStream, moovPayloadSize: Long): Boolean {
        var bytesProcessed = 0L
        var stripped = false

        while (bytesProcessed < moovPayloadSize) {
            val headerBytes = ByteArray(8)
            val read = readFullyOrEof(input, headerBytes)
            if (read < 8) break
            bytesProcessed += 8

            val size = ByteBuffer.wrap(headerBytes, 0, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
            val tag = String(headerBytes, 4, 4, StandardCharsets.ISO_8859_1)

            if (size == 1L) {
                val extBytes = ByteArray(8)
                input.read(extBytes)
                bytesProcessed += 8
                val extSize = ByteBuffer.wrap(extBytes).order(ByteOrder.BIG_ENDIAN).long
                val payloadSize = extSize - 16L
                if (tag == "udta" || tag == "uuid") {
                    writeFreeBox(output, extSize, ext = true)
                    skipFully(input, payloadSize)
                    stripped = true
                } else {
                    output.write(headerBytes)
                    output.write(extBytes)
                    copyBytes(input, output, payloadSize)
                }
                bytesProcessed += payloadSize
            } else {
                val payloadSize = size - 8L
                if (tag == "udta" || tag == "uuid") {
                    writeFreeBox(output, size, ext = false)
                    skipFully(input, payloadSize)
                    stripped = true
                } else {
                    output.write(headerBytes)
                    copyBytes(input, output, payloadSize)
                }
                bytesProcessed += payloadSize
            }
        }
        return stripped
    }

    private fun writeFreeBox(output: OutputStream, totalSize: Long, ext: Boolean) {
        if (ext) {
            val buf = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
            buf.putInt(1)
            buf.put("free".toByteArray(StandardCharsets.ISO_8859_1))
            buf.putLong(totalSize)
            output.write(buf.array())
            writeZeroPayload(output, totalSize - 16L)
        } else {
            val buf = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            buf.putInt(totalSize.toInt())
            buf.put("free".toByteArray(StandardCharsets.ISO_8859_1))
            output.write(buf.array())
            writeZeroPayload(output, totalSize - 8L)
        }
    }

    private fun writeZeroPayload(output: OutputStream, count: Long) {
        val zeroChunk = ByteArray(8192)
        var remaining = count
        while (remaining > 0) {
            val toWrite = minOf(remaining, zeroChunk.size.toLong()).toInt()
            output.write(zeroChunk, 0, toWrite)
            remaining -= toWrite
        }
    }

    private fun copyBytes(input: InputStream, output: OutputStream, count: Long) {
        val buffer = ByteArray(32768)
        var remaining = count
        while (remaining > 0) {
            val toRead = minOf(remaining, buffer.size.toLong()).toInt()
            val read = input.read(buffer, 0, toRead)
            if (read == -1) break
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private data class BoxHeader(val size: Long, val tag: String, val headerLen: Int)

    private fun readBoxHeader(stream: InputStream): BoxHeader? {
        val header = ByteArray(8)
        val read = readFullyOrEof(stream, header)
        if (read < 8) return null

        val size = ByteBuffer.wrap(header, 0, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
        val tag = String(header, 4, 4, StandardCharsets.ISO_8859_1)

        return if (size == 1L) {
            val ext = ByteArray(8)
            val extRead = readFullyOrEof(stream, ext)
            if (extRead < 8) return null
            val extSize = ByteBuffer.wrap(ext).order(ByteOrder.BIG_ENDIAN).long
            BoxHeader(extSize, tag, 16)
        } else {
            BoxHeader(size, tag, 8)
        }
    }

    private fun readFullyOrEof(stream: InputStream, b: ByteArray): Int {
        var offset = 0
        while (offset < b.size) {
            val read = stream.read(b, offset, b.size - offset)
            if (read == -1) break
            offset += read
        }
        return offset
    }

    private fun readBytesExact(stream: InputStream, count: Int): ByteArray {
        val b = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = stream.read(b, offset, count - offset)
            if (read == -1) throw EOFException("Unexpected EOF while reading $count bytes")
            offset += read
        }
        return b
    }

    private fun skipFully(stream: InputStream, count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = stream.skip(remaining)
            if (skipped <= 0) {
                // Fallback to reading
                if (stream.read() == -1) throw EOFException("Unexpected EOF while skipping")
                remaining -= 1
            } else {
                remaining -= skipped
            }
        }
    }
}
