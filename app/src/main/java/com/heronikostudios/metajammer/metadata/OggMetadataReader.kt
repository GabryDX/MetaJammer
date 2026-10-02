package com.heronikostudios.metajammer.metadata

import com.heronikostudios.metajammer.domain.model.MetadataEntry
import com.heronikostudios.metajammer.domain.model.MetadataReplacementPlan
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

/**
 * Pure Kotlin parser, stripper, and poisoner for OGG containers (Vorbis & Opus).
 * Operates without external native libraries and without re-encoding audio packets.
 */
object OggMetadataReader {

    private val OGG_MAGIC = byteArrayOf('O'.code.toByte(), 'g'.code.toByte(), 'g'.code.toByte(), 'S'.code.toByte())
    private val VORBIS_COMMENT_MAGIC = byteArrayOf(0x03, 'v'.code.toByte(), 'o'.code.toByte(), 'r'.code.toByte(), 'b'.code.toByte(), 'i'.code.toByte(), 's'.code.toByte())
    private val OPUS_COMMENT_MAGIC = "OpusTags".toByteArray(Charsets.US_ASCII)

    /**
     * Standard Ogg 32-bit CRC calculation (generator polynomial 0x04C11DB7).
     */
    object OggCrc {
        private val CRC_LOOKUP = IntArray(256) { i ->
            var r = i shl 24
            for (j in 0 until 8) {
                r = if ((r and 0x80000000.toInt()) != 0) {
                    (r shl 1) xor 0x04C11DB7
                } else {
                    r shl 1
                }
            }
            r
        }

        fun calculate(data: ByteArray, offset: Int = 0, length: Int = data.size): Int {
            var crc = 0
            val end = offset + length
            for (i in offset until end) {
                val byteVal = if (i >= offset + 22 && i < offset + 26) 0 else (data[i].toInt() and 0xFF)
                crc = (crc shl 8) xor CRC_LOOKUP[((crc ushr 24) and 0xFF) xor byteVal]
            }
            return crc
        }
    }

    data class RawOggPage(
        val headerBytes: ByteArray,
        val flags: Int,
        val granule: Long,
        val serial: Int,
        val sequence: Int,
        val segmentTable: ByteArray,
        val bodyBytes: ByteArray
    ) {
        val totalSize: Int get() = headerBytes.size + bodyBytes.size
    }

    /**
     * Reads a single Ogg page from [input], or returns null on EOF.
     */
    fun readOggPage(input: InputStream): RawOggPage? {
        val baseHeader = ByteArray(27)
        var readCount = 0
        while (readCount < 27) {
            val r = input.read(baseHeader, readCount, 27 - readCount)
            if (r == -1) {
                if (readCount == 0) return null
                throw IllegalStateException("Unexpected EOF reading Ogg page header")
            }
            readCount += r
        }

        for (i in 0 until 4) {
            if (baseHeader[i] != OGG_MAGIC[i]) {
                throw IllegalStateException("Invalid Ogg sync pattern")
            }
        }

        val flags = baseHeader[5].toInt() and 0xFF
        val granuleBuf = ByteBuffer.wrap(baseHeader, 6, 8).order(ByteOrder.LITTLE_ENDIAN)
        val granule = granuleBuf.long
        val serialBuf = ByteBuffer.wrap(baseHeader, 14, 4).order(ByteOrder.LITTLE_ENDIAN)
        val serial = serialBuf.int
        val seqBuf = ByteBuffer.wrap(baseHeader, 18, 4).order(ByteOrder.LITTLE_ENDIAN)
        val sequence = seqBuf.int

        val numSegments = baseHeader[26].toInt() and 0xFF
        val segmentTable = ByteArray(numSegments)
        var segRead = 0
        while (segRead < numSegments) {
            val r = input.read(segmentTable, segRead, numSegments - segRead)
            if (r == -1) throw IllegalStateException("Unexpected EOF reading Ogg segment table")
            segRead += r
        }

        var bodyLength = 0
        for (seg in segmentTable) {
            bodyLength += (seg.toInt() and 0xFF)
        }

        val bodyBytes = ByteArray(bodyLength)
        var bodyRead = 0
        while (bodyRead < bodyLength) {
            val r = input.read(bodyBytes, bodyRead, bodyLength - bodyRead)
            if (r == -1) throw IllegalStateException("Unexpected EOF reading Ogg page payload")
            bodyRead += r
        }

        val fullHeader = ByteArray(27 + numSegments)
        System.arraycopy(baseHeader, 0, fullHeader, 0, 27)
        System.arraycopy(segmentTable, 0, fullHeader, 27, numSegments)

        return RawOggPage(
            headerBytes = fullHeader,
            flags = flags,
            granule = granule,
            serial = serial,
            sequence = sequence,
            segmentTable = segmentTable,
            bodyBytes = bodyBytes
        )
    }

    /**
     * Reads and parses metadata (Vorbis or Opus comments) from an OGG stream.
     */
    fun readMetadata(inputStream: InputStream): List<MetadataEntry> {
        val entries = mutableListOf<MetadataEntry>()
        try {
            val currentPacket = ByteArrayOutputStream()
            var commentPacketFound = false

            while (true) {
                val page = readOggPage(inputStream) ?: break
                var curOffset = 0

                for (seg in page.segmentTable) {
                    val segLen = seg.toInt() and 0xFF
                    currentPacket.write(page.bodyBytes, curOffset, segLen)
                    curOffset += segLen

                    if (segLen < 255) {
                        // End of packet
                        val packetBytes = currentPacket.toByteArray()
                        currentPacket.reset()

                        val isVorbisComment = packetBytes.size >= 7 &&
                            packetBytes.copyOfRange(0, 7).contentEquals(VORBIS_COMMENT_MAGIC)
                        val isOpusComment = packetBytes.size >= 8 &&
                            packetBytes.copyOfRange(0, 8).contentEquals(OPUS_COMMENT_MAGIC)

                        if (isVorbisComment || isOpusComment) {
                            val headerOffset = if (isVorbisComment) 7 else 8
                            parseVorbisCommentPacket(packetBytes, headerOffset, entries)
                            commentPacketFound = true
                            return entries
                        } else if (commentPacketFound) {
                            return entries
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Error parsing OGG metadata")
        }
        return entries
    }

    private fun parseVorbisCommentPacket(packetBytes: ByteArray, offset: Int, entries: MutableList<MetadataEntry>) {
        if (offset + 8 > packetBytes.size) return
        var pos = offset

        val vendorLen = ByteBuffer.wrap(packetBytes, pos, 4).order(ByteOrder.LITTLE_ENDIAN).int
        pos += 4
        if (vendorLen in 1..(packetBytes.size - pos)) {
            val vendorStr = String(packetBytes, pos, vendorLen, Charsets.UTF_8)
            if (vendorStr.isNotBlank()) {
                entries.add(MetadataEntry("Vendor", vendorStr.trim()))
            }
            pos += vendorLen
        } else if (vendorLen < 0) {
            return
        }

        if (pos + 4 > packetBytes.size) return
        val count = ByteBuffer.wrap(packetBytes, pos, 4).order(ByteOrder.LITTLE_ENDIAN).int
        pos += 4
        if (count < 0) return

        for (i in 0 until count) {
            if (pos + 4 > packetBytes.size) break
            val commentLen = ByteBuffer.wrap(packetBytes, pos, 4).order(ByteOrder.LITTLE_ENDIAN).int
            pos += 4
            if (commentLen < 0 || pos + commentLen > packetBytes.size) break

            val commentStr = String(packetBytes, pos, commentLen, Charsets.UTF_8)
            pos += commentLen

            if (commentStr.contains('=')) {
                val parts = commentStr.split('=', limit = 2)
                val rawKey = parts[0].trim().uppercase(Locale.ROOT)
                val rawValue = parts[1].trim()

                when (rawKey) {
                    "METADATA_BLOCK_PICTURE" -> entries.add(MetadataEntry("Embedded Picture", "Present (Vorbis Picture Block)"))
                    "XMP" -> entries.add(MetadataEntry("XMP Packet", "Present (XMP Metadata)"))
                    else -> {
                        val friendlyKey = formatKey(rawKey)
                        if (rawValue.isNotBlank()) {
                            entries.add(MetadataEntry(friendlyKey, rawValue))
                        }
                    }
                }
            } else if (commentStr.isNotBlank()) {
                entries.add(MetadataEntry("Comment", commentStr.trim()))
            }
        }
    }

    private fun formatKey(rawKey: String): String {
        return when (rawKey) {
            "TITLE" -> "Title"
            "ARTIST" -> "Artist"
            "PERFORMER" -> "Performer"
            "ALBUM" -> "Album"
            "DATE" -> "Date"
            "GENRE" -> "Genre"
            "COMMENT", "DESCRIPTION" -> "Comment"
            "COPYRIGHT" -> "Copyright"
            "LOCATION" -> "Location"
            "GPS_COORDINATES" -> "GPS Coordinates"
            "ORGANIZATION" -> "Organization"
            "CONTACT" -> "Contact"
            "LICENSE" -> "License"
            "DEVICE_ID" -> "Device ID"
            "SECURITY_CLASSIFICATION" -> "Classification"
            "AUDITOR" -> "Auditor"
            "LEAD_INVESTIGATOR" -> "Investigator"
            "ENCODER" -> "Encoder"
            "TRACKNUMBER" -> "Track Number"
            "TRACKTOTAL" -> "Track Total"
            "DISCNUMBER" -> "Disc Number"
            "DISCTOTAL" -> "Disc Total"
            "UUID" -> "UUID"
            else -> rawKey.lowercase(Locale.ROOT)
                .split('_', ' ')
                .filter { it.isNotEmpty() }
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase(Locale.ROOT) } }
        }
    }

    /**
     * Strips all Vorbis/Opus comment metadata, pictures, and vendor tracking tags from the OGG stream.
     */
    fun stripMetadata(inputStream: InputStream, outputStream: OutputStream): Boolean {
        return processOggStream(inputStream, outputStream, replacementPacket = null)
    }

    /**
     * Replaces Vorbis/Opus comment metadata with spoofed/poisoned metadata fields from [plan].
     */
    fun poisonMetadata(inputStream: InputStream, outputStream: OutputStream, plan: MetadataReplacementPlan): Boolean {
        return processOggStream(inputStream, outputStream, replacementPacket = plan)
    }

    private fun processOggStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        replacementPacket: MetadataReplacementPlan?
    ): Boolean {
        return try {
            val pages = mutableListOf<RawOggPage>()
            while (true) {
                val page = readOggPage(inputStream) ?: break
                pages.add(page)
            }

            if (pages.isEmpty()) return false

            val page0 = pages[0]
            val serial = page0.serial

            // Determine if Vorbis or Opus
            val isOpus = page0.bodyBytes.size >= 8 &&
                page0.bodyBytes.copyOfRange(0, 8).contentEquals("OpusHead".toByteArray(Charsets.US_ASCII))

            // Build new comment packet
            val newCommentPacket = if (replacementPacket == null) {
                buildCleanCommentPacket(isOpus)
            } else {
                buildPoisonCommentPacket(isOpus, replacementPacket)
            }

            // Find pages spanning the original comment packet
            var pageIdx = 1
            var foundEnd = false
            var leftoverPage: Pair<RawOggPage, ByteArray>? = null

            while (pageIdx < pages.size && !foundEnd) {
                val p = pages[pageIdx]
                var curOffset = 0
                for (segI in p.segmentTable.indices) {
                    val segLen = p.segmentTable[segI].toInt() and 0xFF
                    curOffset += segLen
                    if (segLen < 255) {
                        foundEnd = true
                        val remainingSegs = p.segmentTable.copyOfRange(segI + 1, p.segmentTable.size)
                        if (remainingSegs.isNotEmpty()) {
                            val remainingBody = p.bodyBytes.copyOfRange(curOffset, p.bodyBytes.size)
                            val leftoverHeader = buildPageHeader(
                                flags = p.flags and 0x01.inv(),
                                granule = p.granule,
                                serial = p.serial,
                                seq = 0,
                                segmentTable = remainingSegs
                            )
                            leftoverPage = Pair(
                                RawOggPage(leftoverHeader, p.flags and 0x01.inv(), p.granule, p.serial, 0, remainingSegs, remainingBody),
                                remainingBody
                            )
                        }
                        break
                    }
                }
                pageIdx++
            }

            // Write page 0
            writePageRaw(outputStream, page0.headerBytes, page0.bodyBytes)

            var curSeq = 1

            // Build and write new comment page(s)
            val commentPages = buildOggPagesForPacket(newCommentPacket, serial, curSeq)
            for (cp in commentPages) {
                outputStream.write(cp)
                curSeq++
            }

            // If there were leftover packet data from the last comment page
            leftoverPage?.let { (lPage, lBody) ->
                writePageWithSeq(outputStream, lPage.headerBytes, lBody, curSeq)
                curSeq++
            }

            // Write all remaining pages from pageIdx onwards with continuous sequence numbers
            for (i in pageIdx until pages.size) {
                val p = pages[i]
                writePageWithSeq(outputStream, p.headerBytes, p.bodyBytes, curSeq)
                curSeq++
            }

            outputStream.flush()
            true
        } catch (e: Exception) {
            Timber.e(e, "Error processing Ogg stream")
            false
        }
    }

    private fun buildCleanCommentPacket(isOpus: Boolean): ByteArray {
        val out = ByteArrayOutputStream()
        if (isOpus) {
            out.write(OPUS_COMMENT_MAGIC)
            out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0).array()) // vendor len = 0
            out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0).array()) // comment count = 0
        } else {
            out.write(VORBIS_COMMENT_MAGIC)
            out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0).array()) // vendor len = 0
            out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0).array()) // comment count = 0
            out.write(1) // framing bit
        }
        return out.toByteArray()
    }

    private fun buildPoisonCommentPacket(isOpus: Boolean, plan: MetadataReplacementPlan): ByteArray {
        val out = ByteArrayOutputStream()
        val magic = if (isOpus) OPUS_COMMENT_MAGIC else VORBIS_COMMENT_MAGIC
        out.write(magic)

        val vendor = (plan.software.ifBlank { "MetaJammer" }).toByteArray(Charsets.UTF_8)
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(vendor.size).array())
        out.write(vendor)

        val comments = mutableListOf<String>()
        if (plan.imageDescription.isNotBlank()) comments.add("TITLE=${plan.imageDescription}")
        val artist = "${plan.make} ${plan.model}".trim()
        if (artist.isNotBlank()) comments.add("ARTIST=$artist")
        if (plan.dateTime.isNotBlank()) comments.add("DATE=${plan.dateTime}")
        if (plan.latitude != 0.0 || plan.longitude != 0.0) {
            comments.add("LOCATION=${plan.latitude}, ${plan.longitude}")
            comments.add("GPS_COORDINATES=${plan.latitude} ${plan.latitudeRef}, ${plan.longitude} ${plan.longitudeRef}")
        }
        if (plan.software.isNotBlank()) comments.add("ENCODER=${plan.software}")
        if (plan.userComment.isNotBlank()) comments.add("COMMENT=${plan.userComment}")

        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(comments.size).array())
        for (c in comments) {
            val cBytes = c.toByteArray(Charsets.UTF_8)
            out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(cBytes.size).array())
            out.write(cBytes)
        }

        if (!isOpus) {
            out.write(1) // Vorbis framing bit
        }

        return out.toByteArray()
    }

    private fun buildOggPagesForPacket(packet: ByteArray, serial: Int, startSeq: Int): List<ByteArray> {
        val pages = mutableListOf<ByteArray>()
        var offset = 0
        var curSeq = startSeq

        while (offset < packet.size || (offset == 0 && packet.isEmpty())) {
            val chunkSegs = mutableListOf<Int>()
            var chunkBytes = 0

            while (offset + chunkBytes < packet.size && chunkSegs.size < 255) {
                val take = minOf(packet.size - (offset + chunkBytes), 255)
                chunkSegs.add(take)
                chunkBytes += take
                if (take < 255) break
            }

            if (chunkSegs.isEmpty()) {
                chunkSegs.add(0)
            }

            val flags = if (offset > 0) 0x01 else 0x00
            val segArray = ByteArray(chunkSegs.size) { chunkSegs[it].toByte() }
            val header = buildPageHeader(
                flags = flags,
                granule = 0L,
                serial = serial,
                seq = curSeq,
                segmentTable = segArray
            )

            val chunkData = packet.copyOfRange(offset, offset + chunkBytes)
            val fullPage = ByteArray(header.size + chunkData.size)
            System.arraycopy(header, 0, fullPage, 0, header.size)
            System.arraycopy(chunkData, 0, fullPage, header.size, chunkData.size)

            // Calculate CRC and insert at bytes 22..25
            val crc = OggCrc.calculate(fullPage)
            val crcBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(crc).array()
            System.arraycopy(crcBuf, 0, fullPage, 22, 4)

            pages.add(fullPage)
            offset += chunkBytes
            curSeq++
            if (offset >= packet.size) break
        }

        return pages
    }

    private fun buildPageHeader(flags: Int, granule: Long, serial: Int, seq: Int, segmentTable: ByteArray): ByteArray {
        val out = ByteArray(27 + segmentTable.size)
        System.arraycopy(OGG_MAGIC, 0, out, 0, 4)
        out[4] = 0 // version
        out[5] = flags.toByte()

        val gBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(granule).array()
        System.arraycopy(gBuf, 0, out, 6, 8)

        val sBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(serial).array()
        System.arraycopy(sBuf, 0, out, 14, 4)

        val seqBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(seq).array()
        System.arraycopy(seqBuf, 0, out, 18, 4)

        // bytes 22..25 are zero placeholder for CRC
        out[26] = segmentTable.size.toByte()
        System.arraycopy(segmentTable, 0, out, 27, segmentTable.size)
        return out
    }

    private fun writePageRaw(out: OutputStream, header: ByteArray, body: ByteArray) {
        out.write(header)
        out.write(body)
    }

    private fun writePageWithSeq(out: OutputStream, header: ByteArray, body: ByteArray, newSeq: Int) {
        val fullPage = ByteArray(header.size + body.size)
        System.arraycopy(header, 0, fullPage, 0, header.size)
        System.arraycopy(body, 0, fullPage, header.size, body.size)

        // Update sequence number
        val seqBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(newSeq).array()
        System.arraycopy(seqBuf, 0, fullPage, 18, 4)

        // Clear existing CRC
        fullPage[22] = 0
        fullPage[23] = 0
        fullPage[24] = 0
        fullPage[25] = 0

        // Recalculate CRC
        val crc = OggCrc.calculate(fullPage)
        val crcBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(crc).array()
        System.arraycopy(crcBuf, 0, fullPage, 22, 4)

        out.write(fullPage)
    }
}
