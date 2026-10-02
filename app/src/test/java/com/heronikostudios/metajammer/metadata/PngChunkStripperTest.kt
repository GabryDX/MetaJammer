package com.heronikostudios.metajammer.metadata

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.CRC32

class PngChunkStripperTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `stripPngChunks purges ancillary metadata chunks while keeping structure`() {
        val pngFile = tempFolder.newFile("sample_with_meta.png")
        val strippedFile = tempFolder.newFile("sample_clean.png")

        // Build a synthetic PNG containing IHDR, tEXt (metadata), and IEND
        val pngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val ihdrData = ByteArray(13) { 0 }
        val textData = "Author\u0000MetaJammerUser".toByteArray(Charsets.ISO_8859_1)

        val outputBytes = java.io.ByteArrayOutputStream()
        outputBytes.write(pngSignature)
        writeChunk(outputBytes, "IHDR", ihdrData)
        writeChunk(outputBytes, "tEXt", textData)
        writeChunk(outputBytes, "IEND", ByteArray(0))

        pngFile.writeBytes(outputBytes.toByteArray())

        val contentBefore = pngFile.readText(Charsets.ISO_8859_1)
        assertTrue(contentBefore.contains("tEXt"))
        assertTrue(contentBefore.contains("MetaJammerUser"))

        val success = ImageMetadataProcessor.stripPngChunks(pngFile, strippedFile)

        assertTrue("stripPngChunks should return true for valid PNG", success)
        val contentAfter = strippedFile.readText(Charsets.ISO_8859_1)
        assertFalse("Cleaned PNG should not contain tEXt chunk", contentAfter.contains("tEXt"))
        assertFalse("Cleaned PNG should not contain metadata value", contentAfter.contains("MetaJammerUser"))
        assertTrue("Cleaned PNG should preserve IHDR chunk", contentAfter.contains("IHDR"))
        assertTrue("Cleaned PNG should preserve IEND chunk", contentAfter.contains("IEND"))
    }

    @Test
    fun `stripPngChunks removes pHYs chunk`() {
        val pngFile = tempFolder.newFile("sample_with_phys.png")
        val strippedFile = tempFolder.newFile("sample_clean_phys.png")

        val pngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val ihdrData = ByteArray(13) { 0 }
        // pHYs chunk: 4 bytes X, 4 bytes Y, 1 byte unit
        val physData = byteArrayOf(0, 0, 0x0B.toByte(), 0x13.toByte(), 0, 0, 0x0B.toByte(), 0x13.toByte(), 1)

        val outputBytes = java.io.ByteArrayOutputStream()
        outputBytes.write(pngSignature)
        writeChunk(outputBytes, "IHDR", ihdrData)
        writeChunk(outputBytes, "pHYs", physData)
        writeChunk(outputBytes, "IEND", ByteArray(0))

        pngFile.writeBytes(outputBytes.toByteArray())

        val contentBefore = pngFile.readText(Charsets.ISO_8859_1)
        assertTrue("PNG should contain pHYs chunk before stripping", contentBefore.contains("pHYs"))

        val success = ImageMetadataProcessor.stripPngChunks(pngFile, strippedFile)
        assertTrue("stripPngChunks should return true", success)

        val contentAfter = strippedFile.readText(Charsets.ISO_8859_1)
        assertFalse("Cleaned PNG must not contain pHYs chunk", contentAfter.contains("pHYs"))
        assertTrue("Cleaned PNG should preserve IHDR chunk", contentAfter.contains("IHDR"))
        assertTrue("Cleaned PNG should preserve IEND chunk", contentAfter.contains("IEND"))
    }

    @Test
    fun `stripPngChunks on reviewer test image removes pHYs and all metadata chunks`() {
        val testImage = File("images/test/1.png")
        if (!testImage.exists()) return

        val contentBefore = testImage.readText(Charsets.ISO_8859_1)
        assertTrue("1.png should contain pHYs", contentBefore.contains("pHYs"))
        assertTrue("1.png should contain eXIf", contentBefore.contains("eXIf"))

        val strippedFile = tempFolder.newFile("1_stripped.png")
        val success = ImageMetadataProcessor.stripPngChunks(testImage, strippedFile)
        assertTrue("stripPngChunks on 1.png should succeed", success)

        val contentAfter = strippedFile.readText(Charsets.ISO_8859_1)
        assertFalse("Cleaned 1.png must not contain pHYs chunk", contentAfter.contains("pHYs"))
        assertFalse("Cleaned 1.png must not contain eXIf chunk", contentAfter.contains("eXIf"))
        assertFalse("Cleaned 1.png must not contain tEXt chunk", contentAfter.contains("tEXt"))
        assertFalse("Cleaned 1.png must not contain iTXt chunk", contentAfter.contains("iTXt"))
        assertFalse("Cleaned 1.png must not contain zTXt chunk", contentAfter.contains("zTXt"))
        assertTrue("Cleaned 1.png should preserve IHDR chunk", contentAfter.contains("IHDR"))
        assertTrue("Cleaned 1.png should preserve IDAT chunk", contentAfter.contains("IDAT"))
        assertTrue("Cleaned 1.png should preserve IEND chunk", contentAfter.contains("IEND"))
    }

    private fun writeChunk(out: java.io.ByteArrayOutputStream, type: String, data: ByteArray) {
        val lengthBuffer = ByteBuffer.allocate(4).putInt(data.size).array()
        out.write(lengthBuffer)

        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        out.write(typeBytes)
        out.write(data)

        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        val crcBuffer = ByteBuffer.allocate(4).putInt(crc.value.toInt()).array()
        out.write(crcBuffer)
    }
}
