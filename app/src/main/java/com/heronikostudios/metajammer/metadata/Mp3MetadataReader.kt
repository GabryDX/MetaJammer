package com.heronikostudios.metajammer.metadata

import com.heronikostudios.metajammer.domain.model.MetadataEntry
import com.heronikostudios.metajammer.domain.model.MetadataReplacementPlan
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Pure Kotlin parser, stripper, and poisoner for MP3 (MPEG Audio Layer III) containers.
 * Handles ID3v2 (v2.2, v2.3, v2.4), ID3v1, and encapsulated tracking objects (GEOB / C2PA)
 * with zero external dependencies and zero audio re-compression.
 */
object Mp3MetadataReader {

    private const val BUFFER_SIZE = 64 * 1024
    private const val ID3V1_TAG_SIZE = 128
    private const val MAX_ID3V2_SCAN_SIZE = 16 * 1024 * 1024 // 16 MB max tag size sanity bound

    private val ID3V2_MAGIC = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte())
    private val ID3V1_MAGIC = byteArrayOf('T'.code.toByte(), 'A'.code.toByte(), 'G'.code.toByte())

    /**
     * Inspects and extracts metadata entries from an MP3 input stream.
     */
    fun readMetadata(input: InputStream): List<MetadataEntry> {
        val entries = mutableListOf<MetadataEntry>()

        try {
            // Read leading ID3v2 tags
            val id3v2Header = ByteArray(10)
            if (readFully(input, id3v2Header)) {
                if (isId3v2Header(id3v2Header)) {
                    val majorVersion = id3v2Header[3].toInt() and 0xFF
                    val revision = id3v2Header[4].toInt() and 0xFF
                    val flags = id3v2Header[5].toInt() and 0xFF
                    val tagSize = decodeSyncsafeInteger(id3v2Header, 6)

                    entries.add(MetadataEntry("ID3 Version", "ID3v2.$majorVersion.$revision"))
                    entries.add(MetadataEntry("Tag Header Size", "${tagSize + 10} bytes"))

                    if (tagSize in 1..MAX_ID3V2_SCAN_SIZE) {
                        val tagBody = ByteArray(tagSize)
                        if (readFully(input, tagBody)) {
                            parseId3v2Frames(tagBody, majorVersion, entries)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Error parsing MP3 ID3 metadata")
        }

        return entries
    }

    /**
     * Losslessly strips all ID3v2 tags from the header and ID3v1 tags from the footer.
     * Audio frames are passed through directly with zero recompression.
     */
    fun stripMetadata(input: InputStream, output: OutputStream): Boolean {
        return try {
            // Step 1: Skip all leading ID3v2 tags
            val headerBuffer = ByteArray(10)
            while (true) {
                if (!readFully(input, headerBuffer)) {
                    // Reached EOF before 10 bytes: write what was read and exit
                    return true
                }

                if (isId3v2Header(headerBuffer)) {
                    val flags = headerBuffer[5].toInt() and 0xFF
                    val tagSize = decodeSyncsafeInteger(headerBuffer, 6)
                    val hasFooter = (flags and 0x10) != 0
                    val toSkip = tagSize.toLong() + (if (hasFooter) 10L else 0L)
                    Timber.d("Skipping ID3v2 tag: %d bytes (hasFooter: %b)", tagSize, hasFooter)
                    skipFully(input, toSkip)
                } else {
                    // Non-ID3 bytes: this is the start of the audio stream.
                    // Pass these 10 bytes to the streaming pipeline.
                    streamStrippedAudio(firstBytes = headerBuffer, input = input, output = output)
                    break
                }
            }
            output.flush()
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to strip MP3 metadata")
            false
        }
    }

    /**
     * Strips original ID3 metadata and injects a clean, synthetic ID3v2.3 tag
     * with fake/poisoned metadata before the raw MPEG audio frames.
     */
    fun poisonMetadata(input: InputStream, output: OutputStream, plan: MetadataReplacementPlan): Boolean {
        return try {
            // Synthesize fake ID3v2.3 tag
            val poisonedTag = buildPoisonedId3v2Tag(plan)
            output.write(poisonedTag)

            // Strip and append raw audio frames
            stripMetadata(input, output)
        } catch (e: Exception) {
            Timber.e(e, "Failed to poison MP3 metadata")
            false
        }
    }

    private fun isId3v2Header(header: ByteArray): Boolean {
        return header.size >= 3 &&
            header[0] == ID3V2_MAGIC[0] &&
            header[1] == ID3V2_MAGIC[1] &&
            header[2] == ID3V2_MAGIC[2]
    }

    private fun decodeSyncsafeInteger(data: ByteArray, offset: Int): Int {
        val b0 = data[offset].toInt() and 0x7F
        val b1 = data[offset + 1].toInt() and 0x7F
        val b2 = data[offset + 2].toInt() and 0x7F
        val b3 = data[offset + 3].toInt() and 0x7F
        return (b0 shl 21) or (b1 shl 14) or (b2 shl 7) or b3
    }

    private fun encodeSyncsafeInteger(value: Int): ByteArray {
        return byteArrayOf(
            ((value ushr 21) and 0x7F).toByte(),
            ((value ushr 14) and 0x7F).toByte(),
            ((value ushr 7) and 0x7F).toByte(),
            (value and 0x7F).toByte()
        )
    }

    /**
     * Parses frames inside an ID3v2 payload.
     */
    private fun parseId3v2Frames(tagBody: ByteArray, majorVersion: Int, entries: MutableList<MetadataEntry>) {
        var offset = 0
        val isV2 = majorVersion == 2

        while (offset < tagBody.size) {
            val headerSize = if (isV2) 6 else 10
            if (offset + headerSize > tagBody.size) break

            // Padding check: frames ending with 0x00
            if (tagBody[offset] == 0.toByte()) break

            val frameId = if (isV2) {
                String(tagBody, offset, 3, StandardCharsets.US_ASCII)
            } else {
                String(tagBody, offset, 4, StandardCharsets.US_ASCII)
            }

            val frameSize = if (isV2) {
                ((tagBody[offset + 3].toInt() and 0xFF) shl 16) or
                    ((tagBody[offset + 4].toInt() and 0xFF) shl 8) or
                    (tagBody[offset + 5].toInt() and 0xFF)
            } else if (majorVersion == 4) {
                decodeSyncsafeInteger(tagBody, offset + 4)
            } else {
                ((tagBody[offset + 4].toInt() and 0xFF) shl 24) or
                    ((tagBody[offset + 5].toInt() and 0xFF) shl 16) or
                    ((tagBody[offset + 6].toInt() and 0xFF) shl 8) or
                    (tagBody[offset + 7].toInt() and 0xFF)
            }

            offset += headerSize
            if (frameSize <= 0 || offset + frameSize > tagBody.size) break

            when {
                // Encapsulated tracking objects (C2PA / GEOB)
                frameId == "GEOB" || frameId == "GEO" -> {
                    val geobDesc = parseGeobFrame(tagBody, offset, frameSize)
                    entries.add(MetadataEntry("C2PA / Encapsulated Object", geobDesc))
                }

                // Embedded pictures / Album Art
                frameId == "APIC" || frameId == "PIC" -> {
                    entries.add(MetadataEntry("Embedded Album Art", "Present ($frameSize bytes)"))
                }

                // Comments
                frameId == "COMM" || frameId == "COM" -> {
                    val comment = parseCommentFrame(tagBody, offset, frameSize)
                    if (comment.isNotBlank()) {
                        entries.add(MetadataEntry("Comment", comment))
                    }
                }

                // Standard text frames (T...)
                frameId.startsWith("T") -> {
                    val label = mapTextFrameId(frameId)
                    val text = parseTextFrame(tagBody, offset, frameSize)
                    if (text.isNotBlank()) {
                        entries.add(MetadataEntry(label, text))
                    }
                }

                // Private tracking frames
                frameId == "PRIV" -> {
                    entries.add(MetadataEntry("Private Tag", "Custom identifier ($frameSize bytes)"))
                }
            }

            offset += frameSize
        }
    }

    private fun mapTextFrameId(id: String): String = when (id) {
        "TIT2", "TT2" -> "Title"
        "TPE1", "TP1" -> "Artist"
        "TALB", "TAL" -> "Album"
        "TYER", "TYE", "TDRC" -> "Date"
        "TCON", "TCO" -> "Genre"
        "TENC", "TEN" -> "Encoder"
        "TSSE" -> "Software"
        "TCOP", "TCR" -> "Copyright"
        "TRCK", "TRK" -> "Track Number"
        "TCOM", "TCM" -> "Composer"
        "TPE2", "TP2" -> "Band / Orchestra"
        "TPUB" -> "Publisher"
        else -> "ID3 $id"
    }

    private fun parseTextFrame(bytes: ByteArray, offset: Int, length: Int): String {
        if (length <= 1) return ""
        val encoding = bytes[offset].toInt() and 0xFF
        return decodeString(bytes, offset + 1, length - 1, encoding).trim('\u0000', ' ')
    }

    private fun parseCommentFrame(bytes: ByteArray, offset: Int, length: Int): String {
        if (length <= 4) return ""
        val encoding = bytes[offset].toInt() and 0xFF
        // bytes[1..3] is language
        var descEnd = offset + 4
        if (encoding == 1 || encoding == 2) {
            while (descEnd + 1 < offset + length) {
                if (bytes[descEnd] == 0.toByte() && bytes[descEnd + 1] == 0.toByte()) {
                    descEnd += 2
                    break
                }
                descEnd += 2
            }
        } else {
            while (descEnd < offset + length) {
                if (bytes[descEnd] == 0.toByte()) {
                    descEnd += 1
                    break
                }
                descEnd++
            }
        }
        val textLen = (offset + length) - descEnd
        return if (textLen > 0) {
            decodeString(bytes, descEnd, textLen, encoding).trim('\u0000', ' ')
        } else ""
    }

    private fun parseGeobFrame(bytes: ByteArray, offset: Int, length: Int): String {
        if (length <= 1) return "Empty GEOB frame"
        val encoding = bytes[offset].toInt() and 0xFF
        // Find mime end
        var mimeEnd = offset + 1
        while (mimeEnd < offset + length && bytes[mimeEnd] != 0.toByte()) {
            mimeEnd++
        }
        val mime = String(bytes, offset + 1, mimeEnd - (offset + 1), StandardCharsets.ISO_8859_1)
        return if (mime.contains("c2pa", ignoreCase = true)) {
            "Content Authenticity Initiative (C2PA) Manifest ($mime, $length bytes)"
        } else {
            "Object ($mime, $length bytes)"
        }
    }

    private fun decodeString(bytes: ByteArray, offset: Int, length: Int, encoding: Int): String {
        if (length <= 0) return ""
        val charset: Charset = when (encoding) {
            0 -> StandardCharsets.ISO_8859_1
            1 -> StandardCharsets.UTF_16
            2 -> StandardCharsets.UTF_16BE
            3 -> StandardCharsets.UTF_8
            else -> StandardCharsets.ISO_8859_1
        }
        return try {
            String(bytes, offset, length, charset)
        } catch (_: Exception) {
            String(bytes, offset, length, StandardCharsets.ISO_8859_1)
        }
    }

    /**
     * Streams the audio payload while stripping trailing ID3v1 tags (last 128 bytes starting with "TAG").
     */
    private fun streamStrippedAudio(firstBytes: ByteArray, input: InputStream, output: OutputStream) {
        val tailBuffer = ByteArrayOutputStream(ID3V1_TAG_SIZE)
        tailBuffer.write(firstBytes)

        val readBuffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val bytesRead = input.read(readBuffer)
            if (bytesRead < 0) break

            tailBuffer.write(readBuffer, 0, bytesRead)
            val currentBytes = tailBuffer.toByteArray()

            if (currentBytes.size > ID3V1_TAG_SIZE) {
                val flushLen = currentBytes.size - ID3V1_TAG_SIZE
                output.write(currentBytes, 0, flushLen)

                tailBuffer.reset()
                tailBuffer.write(currentBytes, flushLen, ID3V1_TAG_SIZE)
            }
        }

        // Check if the remaining 128 bytes are an ID3v1 tag
        val finalTail = tailBuffer.toByteArray()
        if (finalTail.size == ID3V1_TAG_SIZE &&
            finalTail[0] == ID3V1_MAGIC[0] &&
            finalTail[1] == ID3V1_MAGIC[1] &&
            finalTail[2] == ID3V1_MAGIC[2]
        ) {
            Timber.d("Detected and stripped trailing ID3v1 tag")
        } else {
            output.write(finalTail)
        }
    }

    /**
     * Synthesizes an ID3v2.3 tag containing poisoned/fake metadata.
     */
    private fun buildPoisonedId3v2Tag(plan: MetadataReplacementPlan): ByteArray {
        val framesOut = ByteArrayOutputStream()

        val fakeTitle = plan.title ?: plan.imageDescription.ifBlank { "Voice Memo" }
        val fakeArtist = plan.artist ?: "${plan.make} ${plan.model}".trim().ifBlank { "MetaJammer Audio" }
        val fakeAlbum = plan.album ?: "Private Recordings"
        val fakeYear = plan.year ?: if (plan.dateTime.length >= 4) plan.dateTime.substring(0, 4) else "2026"
        val fakeEncoder = plan.software.ifBlank { "MetaJammer Audio Engine" }
        val fakeComment = plan.userComment.ifBlank { "Sanitized and poisoned with MetaJammer" }

        writeTextFrame(framesOut, "TIT2", fakeTitle)
        writeTextFrame(framesOut, "TPE1", fakeArtist)
        writeTextFrame(framesOut, "TALB", fakeAlbum)
        writeTextFrame(framesOut, "TYER", fakeYear)
        writeTextFrame(framesOut, "TSSE", fakeEncoder)
        writeCommentFrame(framesOut, fakeComment)

        val framesBytes = framesOut.toByteArray()
        val tagSize = framesBytes.size

        val header = ByteArrayOutputStream(10 + tagSize)
        header.write(ID3V2_MAGIC)
        header.write(0x03) // Major version ID3v2.3
        header.write(0x00) // Revision
        header.write(0x00) // Flags (no unsync, no ext header, no experimental)
        header.write(encodeSyncsafeInteger(tagSize))
        header.write(framesBytes)

        return header.toByteArray()
    }

    private fun writeTextFrame(out: ByteArrayOutputStream, frameId: String, text: String) {
        val isAscii = text.all { it.code in 0..255 }
        val encodingByte: Byte = if (isAscii) 0x00 else 0x01
        val textBytes = if (isAscii) {
            text.toByteArray(StandardCharsets.ISO_8859_1)
        } else {
            // UTF-16 with BOM (LE)
            val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
            bom + text.toByteArray(StandardCharsets.UTF_16LE)
        }

        val payloadSize = 1 + textBytes.size
        out.write(frameId.toByteArray(StandardCharsets.US_ASCII))
        out.write((payloadSize ushr 24) and 0xFF)
        out.write((payloadSize ushr 16) and 0xFF)
        out.write((payloadSize ushr 8) and 0xFF)
        out.write(payloadSize and 0xFF)
        out.write(0x00) // Flags
        out.write(0x00)
        out.write(encodingByte.toInt())
        out.write(textBytes)
    }

    private fun writeCommentFrame(out: ByteArrayOutputStream, comment: String) {
        val isAscii = comment.all { it.code in 0..255 }
        val encodingByte: Byte = if (isAscii) 0x00 else 0x01
        val lang = "eng".toByteArray(StandardCharsets.US_ASCII)
        val shortDesc = if (isAscii) byteArrayOf(0x00) else byteArrayOf(0x00, 0x00)
        val commentBytes = if (isAscii) {
            comment.toByteArray(StandardCharsets.ISO_8859_1)
        } else {
            val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
            bom + comment.toByteArray(StandardCharsets.UTF_16LE)
        }

        val payloadSize = 1 + lang.size + shortDesc.size + commentBytes.size
        out.write("COMM".toByteArray(StandardCharsets.US_ASCII))
        out.write((payloadSize ushr 24) and 0xFF)
        out.write((payloadSize ushr 16) and 0xFF)
        out.write((payloadSize ushr 8) and 0xFF)
        out.write(payloadSize and 0xFF)
        out.write(0x00) // Flags
        out.write(0x00)
        out.write(encodingByte.toInt())
        out.write(lang)
        out.write(shortDesc)
        out.write(commentBytes)
    }

    private fun readFully(stream: InputStream, buffer: ByteArray): Boolean {
        var totalRead = 0
        while (totalRead < buffer.size) {
            val r = stream.read(buffer, totalRead, buffer.size - totalRead)
            if (r < 0) return false
            totalRead += r
        }
        return true
    }

    private fun skipFully(stream: InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        val discard = ByteArray(BUFFER_SIZE)
        while (remaining > 0) {
            val skipped = stream.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else {
                val toRead = minOf(remaining, BUFFER_SIZE.toLong()).toInt()
                val r = stream.read(discard, 0, toRead)
                if (r < 0) break
                remaining -= r
            }
        }
    }
}
