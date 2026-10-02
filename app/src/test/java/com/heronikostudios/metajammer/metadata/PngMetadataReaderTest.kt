package com.heronikostudios.metajammer.metadata

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

class PngMetadataReaderTest {

    private fun createChunk(type: String, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val lenBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(data.size).array()
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        out.write(lenBytes)
        out.write(typeBytes)
        out.write(data)

        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        val crcBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(crc.value.toInt()).array()
        out.write(crcBytes)
        return out.toByteArray()
    }

    private fun createIhdrChunk(width: Int, height: Int): ByteArray {
        val data = ByteBuffer.allocate(13).order(ByteOrder.BIG_ENDIAN)
            .putInt(width)
            .putInt(height)
            .put(8.toByte()) // bit depth
            .put(6.toByte()) // color type RGBA
            .put(0.toByte()) // compression
            .put(0.toByte()) // filter
            .put(0.toByte()) // interlace
            .array()
        return createChunk("IHDR", data)
    }

    private fun createTextChunk(keyword: String, text: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(keyword.toByteArray(Charsets.ISO_8859_1))
        out.write(0)
        out.write(text.toByteArray(Charsets.ISO_8859_1))
        return createChunk("tEXt", out.toByteArray())
    }

    private fun createPhysChunk(ppuX: Int, ppuY: Int, unit: Int): ByteArray {
        val data = ByteBuffer.allocate(9).order(ByteOrder.BIG_ENDIAN)
            .putInt(ppuX)
            .putInt(ppuY)
            .put(unit.toByte())
            .array()
        return createChunk("pHYs", data)
    }

    private fun createTimeChunk(year: Int, month: Int, day: Int, hour: Int, min: Int, sec: Int): ByteArray {
        val data = ByteBuffer.allocate(7).order(ByteOrder.BIG_ENDIAN)
            .putShort(year.toShort())
            .put(month.toByte())
            .put(day.toByte())
            .put(hour.toByte())
            .put(min.toByte())
            .put(sec.toByte())
            .array()
        return createChunk("tIME", data)
    }

    @Test
    fun testParseIhdrAndAncillaryChunks() {
        val pngStream = ByteArrayOutputStream()
        pngStream.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        pngStream.write(createIhdrChunk(1920, 1080))
        pngStream.write(createTextChunk("Author", "Test Photographer"))
        pngStream.write(createTextChunk("Title", "Sunset View"))
        pngStream.write(createPhysChunk(2835, 2835, 1)) // ~72 DPI
        pngStream.write(createTimeChunk(2026, 10, 2, 14, 30, 0))
        pngStream.write(createChunk("IEND", ByteArray(0)))

        val info = PngMetadataReader.readMetadata(pngStream.toByteArray())
        assertEquals(1920, info.width)
        assertEquals(1080, info.height)

        val author = info.entries.find { it.key == "Author" }
        assertNotNull(author)
        assertEquals("Test Photographer", author?.value)

        val title = info.entries.find { it.key == "Title" }
        assertNotNull(title)
        assertEquals("Sunset View", title?.value)

        val resolution = info.entries.find { it.key == "Resolution" }
        assertNotNull(resolution)
        assertTrue(resolution!!.value.contains("72x72 DPI"))

        val time = info.entries.find { it.key == "DateTimeModified" }
        assertNotNull(time)
        assertEquals("2026:10:02 14:30:00", time?.value)
    }

    @Test
    fun testCleanPngReturnsNoEntries() {
        val pngStream = ByteArrayOutputStream()
        pngStream.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        pngStream.write(createIhdrChunk(800, 600))
        pngStream.write(createChunk("IDAT", byteArrayOf(1, 2, 3, 4)))
        pngStream.write(createChunk("IEND", ByteArray(0)))

        val info = PngMetadataReader.readMetadata(pngStream.toByteArray())
        assertEquals(800, info.width)
        assertEquals(600, info.height)
        assertTrue(info.entries.isEmpty())
    }

    @Test
    fun testNonPngReturnsEmptyInfo() {
        val nonPng = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val info = PngMetadataReader.readMetadata(nonPng)
        assertNull(info.width)
        assertNull(info.height)
        assertTrue(info.entries.isEmpty())
    }
}
