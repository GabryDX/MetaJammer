package com.heronikostudios.metajammer.metadata

import com.heronikostudios.metajammer.domain.model.MetadataReplacementPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.Base64
import java.util.zip.CRC32

class SvgMetadataProcessorTest {

    @Test
    fun `cleanSvgString removes comments, metadata tags, and editor attributes`() {
        val sampleSvg = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!-- Generator: Adobe Illustrator 25.0.0, SVG Export Plug-In -->
            <svg xmlns="http://www.w3.org/2000/svg" xmlns:inkscape="http://www.inkscape.org/namespaces/inkscape"
                 xmlns:sodipodi="http://sodipodi.sourceforge.net/DTD/sodipodi-0.dtd"
                 width="100" height="100" viewBox="0 0 100 100"
                 inkscape:version="1.1" sodipodi:docname="/home/user/confidential_diagram.svg">
              <sodipodi:namedview id="namedview1" pagecolor="#ffffff" inkscape:cx="50" inkscape:cy="50" />
              <!-- Sensitive comment in tree -->
              <title>Secret Blueprint</title>
              <desc>Proprietary architecture diagram</desc>
              <metadata>
                <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                  <rdf:Description rdf:about="" dc:creator="John Doe" xmlns:dc="http://purl.org/dc/elements/1.1/" />
                </rdf:RDF>
              </metadata>
              <g inkscape:groupmode="layer" inkscape:label="Layer 1">
                <rect width="80" height="80" fill="blue" />
              </g>
            </svg>
        """.trimIndent()

        val cleaned = SvgMetadataProcessor.cleanSvgString(sampleSvg)

        // Sensitive metadata elements must be removed
        assertFalse("Cleaned SVG should not contain title", cleaned.contains("Secret Blueprint"))
        assertFalse("Cleaned SVG should not contain title tag", cleaned.contains("<title>"))
        assertFalse("Cleaned SVG should not contain desc", cleaned.contains("Proprietary architecture diagram"))
        assertFalse("Cleaned SVG should not contain desc tag", cleaned.contains("<desc>"))
        assertFalse("Cleaned SVG should not contain metadata tag", cleaned.contains("<metadata>"))
        assertFalse("Cleaned SVG should not contain rdf:RDF", cleaned.contains("rdf:RDF"))
        assertFalse("Cleaned SVG should not contain author John Doe", cleaned.contains("John Doe"))

        // Comments must be stripped
        assertFalse("Cleaned SVG should not contain Illustrator generator comment", cleaned.contains("Adobe Illustrator"))
        assertFalse("Cleaned SVG should not contain tree comment", cleaned.contains("Sensitive comment in tree"))

        // Editor paths and attributes must be removed
        assertFalse("Cleaned SVG should not contain local docname path", cleaned.contains("confidential_diagram.svg"))
        assertFalse("Cleaned SVG should not contain inkscape:version", cleaned.contains("inkscape:version"))
        assertFalse("Cleaned SVG should not contain sodipodi:namedview", cleaned.contains("sodipodi:namedview"))

        // Visual elements and dimensions must be preserved
        assertTrue("Cleaned SVG must preserve width attribute", cleaned.contains("width=\"100\"") || cleaned.contains("width='100'"))
        assertTrue("Cleaned SVG must preserve height attribute", cleaned.contains("height=\"100\"") || cleaned.contains("height='100'"))
        assertTrue("Cleaned SVG must preserve rect element", cleaned.contains("<rect") && cleaned.contains("fill=\"blue\""))
    }

    @Test
    fun `cleanSvgString scrubs embedded PNG base64 metadata chunks`() {
        // Build a synthetic PNG containing tEXt metadata chunk
        val pngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val ihdrData = ByteArray(13) { 0 }
        val textData = "Comment\u0000SecretGPSCoordinatesInsidePNG".toByteArray(Charsets.ISO_8859_1)

        val pngStream = ByteArrayOutputStream()
        pngStream.write(pngSignature)
        writePngChunk(pngStream, "IHDR", ihdrData)
        writePngChunk(pngStream, "tEXt", textData)
        writePngChunk(pngStream, "IEND", ByteArray(0))

        val pngBase64 = Base64.getEncoder().encodeToString(pngStream.toByteArray())

        val svgWithEmbeddedPng = """
            <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="200" height="200">
              <image xlink:href="data:image/png;base64,$pngBase64" width="200" height="200"/>
            </svg>
        """.trimIndent()

        val cleaned = SvgMetadataProcessor.cleanSvgString(svgWithEmbeddedPng)

        // Extract cleaned base64 data
        val base64Match = Regex("data:image/png;base64,([A-Za-z0-9+/=]+)").find(cleaned)
        assertTrue("SVG should still contain embedded image", base64Match != null)

        val cleanedBase64 = base64Match!!.groupValues[1]
        val cleanedPngBytes = Base64.getDecoder().decode(cleanedBase64)
        val cleanedPngString = String(cleanedPngBytes, Charsets.ISO_8859_1)

        assertFalse("Cleaned embedded PNG must not contain tEXt chunk", cleanedPngString.contains("tEXt"))
        assertFalse("Cleaned embedded PNG must not contain secret metadata", cleanedPngString.contains("SecretGPSCoordinatesInsidePNG"))
        assertTrue("Cleaned embedded PNG must retain IHDR chunk", cleanedPngString.contains("IHDR"))
        assertTrue("Cleaned embedded PNG must retain IEND chunk", cleanedPngString.contains("IEND"))
    }

    @Test
    fun `poisonSvgString replaces metadata with poisoned plan values`() {
        val sampleSvg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="100" height="100">
              <title>Original Title</title>
              <rect width="100" height="100" fill="green" />
            </svg>
        """.trimIndent()

        val plan = MetadataReplacementPlan(
            dateTime = "2024:05:01 12:00:00",
            make = "Canon",
            model = "EOS R5",
            software = "MetaJammer Fake",
            imageDescription = "Poisoned Vector Graphic",
            userComment = "Spoofed User Comment",
            photographicSensitivity = "100",
            exposureTime = "1/200",
            fNumber = "2.8",
            focalLength = "50mm",
            latitude = 48.8566,
            longitude = 2.3522,
            latitudeRef = "N",
            longitudeRef = "E",
            whiteBalance = "0",
            flash = "0"
        )

        val poisoned = SvgMetadataProcessor.poisonSvgString(sampleSvg, plan)

        assertFalse("Poisoned SVG should not contain original title", poisoned.contains("Original Title"))
        assertTrue("Poisoned SVG should contain new poisoned title", poisoned.contains("Poisoned Vector Graphic"))
        assertTrue("Poisoned SVG should contain fake software in desc", poisoned.contains("MetaJammer Fake"))
        assertTrue("Poisoned SVG should contain poisoned camera model", poisoned.contains("Canon EOS R5"))
        assertTrue("Poisoned SVG should retain graphic element", poisoned.contains("<rect"))
    }

    @Test
    fun `readSvgMetadataString extracts titles, descriptions, and comments correctly`() {
        val sampleSvg = """
            <!-- Comment 1 -->
            <!-- Comment 2 -->
            <svg xmlns="http://www.w3.org/2000/svg" width="100" height="100">
              <title>Artwork Title</title>
              <desc>Artwork Description</desc>
              <metadata><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"/></metadata>
            </svg>
        """.trimIndent()

        val entries = SvgMetadataProcessor.readSvgMetadataString(sampleSvg)
        val entriesMap = entries.associate { it.key to it.value }

        assertEquals("Artwork Title", entriesMap["Title"])
        assertEquals("Artwork Description", entriesMap["Description"])
        assertEquals("Present (XMP/RDF)", entriesMap["Metadata Tag"])
        assertEquals("2 comment(s)", entriesMap["Comments"])
    }

    @Test
    fun `readSvgMetadataString parses rich Dublin Core, editor attributes, and comments from sample SVG`() {
        val sampleSvgFile = listOf(
            File("test_data/fake_metadata_sample.svg"),
            File("../test_data/fake_metadata_sample.svg")
        ).firstOrNull { it.exists() } ?: File("test_data/fake_metadata_sample.svg")
        assertTrue("Sample SVG test file must exist", sampleSvgFile.exists())

        val entries = SvgMetadataProcessor.readSvgMetadataString(sampleSvgFile.readText())
        val entriesMap = entries.associate { it.key to it.value }

        assertEquals("Classified Operations & Metadata Analysis 2026", entriesMap["Title"])
        assertEquals("Dr. Jane Doe & Director Alex Vance", entriesMap["Creator"])
        assertTrue(entriesMap["Description"]?.contains("Synthetic high-density vector specimen") == true)
        assertEquals("2026-10-02T12:52:03+02:00", entriesMap["Date"])
        assertEquals("Geneva, Switzerland (46.204391 N, 6.143158 E)", entriesMap["Location"])
        assertEquals("Copyright © 2026 Acme Cybernetics Security Ltd. All Rights Reserved.", entriesMap["Copyright"])
        assertEquals("Acme Cybernetics Security Ltd.", entriesMap["Publisher"])
        assertEquals("Threat Intelligence Research Team", entriesMap["Contributor"])
        assertTrue(entriesMap["Keywords"]?.contains("sanitization") == true)
        assertEquals("http://creativecommons.org/licenses/by-nc/4.0/", entriesMap["License"])
        assertEquals("urn:uuid:7f3b891a-1d54-4a56-829b-00123456789a", entriesMap["Identifier"])
        assertEquals("https://internal.vault.cybernetics.corp/vector/2026/diagram.svg", entriesMap["Source"])
        assertTrue(entriesMap["Software"]?.contains("Inkscape 1.3.2") == true)
        assertEquals("classified_operations_2026.svg", entriesMap["Document Name"])
        assertEquals("1 embedded image(s)", entriesMap["Embedded Images"])
        assertEquals("11 comment(s)", entriesMap["Comments"])
    }

    @Test
    fun `cleanSvgString strips all metadata, editor attributes, and scrubs embedded PNG chunks from sample SVG`() {
        val sampleSvgFile = listOf(
            File("test_data/fake_metadata_sample.svg"),
            File("../test_data/fake_metadata_sample.svg")
        ).firstOrNull { it.exists() } ?: File("test_data/fake_metadata_sample.svg")
        assertTrue("Sample SVG test file must exist", sampleSvgFile.exists())

        val originalContent = sampleSvgFile.readText()
        val cleaned = SvgMetadataProcessor.cleanSvgString(originalContent)

        // Metadata elements must be gone
        assertFalse("Cleaned SVG should not contain title tag", cleaned.contains("<title>"))
        assertFalse("Cleaned SVG should not contain desc tag", cleaned.contains("<desc>"))
        assertFalse("Cleaned SVG should not contain metadata tag", cleaned.contains("<metadata"))
        assertFalse("Cleaned SVG should not contain rdf:RDF", cleaned.contains("rdf:RDF"))
        assertFalse("Cleaned SVG should not contain dc:creator", cleaned.contains("dc:creator"))
        assertFalse("Cleaned SVG should not contain dc:coverage", cleaned.contains("dc:coverage"))
        assertFalse("Cleaned SVG should not contain dc:date", cleaned.contains("dc:date"))
        assertFalse("Cleaned SVG should not contain dc:rights", cleaned.contains("dc:rights"))

        // XML comments must be stripped
        assertFalse("Cleaned SVG should not contain comments", cleaned.contains("<!--"))
        assertFalse("Cleaned SVG should not contain host comment", cleaned.contains("workstation-09.internal.corp"))
        assertFalse("Cleaned SVG should not contain generator comment", cleaned.contains("Antigravity Vector Engine"))

        // Editor attributes and elements must be removed
        assertFalse("Cleaned SVG should not contain inkscape:version attribute", cleaned.contains("inkscape:version=\""))
        assertFalse("Cleaned SVG should not contain inkscape version value", cleaned.contains("1.3.2 (091e20e"))
        assertFalse("Cleaned SVG should not contain sodipodi:docname attribute", cleaned.contains("sodipodi:docname=\""))
        assertFalse("Cleaned SVG should not contain document name value", cleaned.contains("classified_operations_2026.svg"))
        assertFalse("Cleaned SVG should not contain sodipodi:namedview element", cleaned.contains("<sodipodi:namedview"))

        // Embedded image must still exist, but without PNG metadata chunks
        assertTrue("Cleaned SVG must retain embedded image tag", cleaned.contains("<image"))
        val base64Match = Regex("data:image/png;base64,([A-Za-z0-9+/=]+)").find(cleaned)
        assertTrue("Cleaned SVG must retain embedded PNG base64", base64Match != null)
        val decodedPng = Base64.getDecoder().decode(base64Match!!.groupValues[1])
        val pngString = String(decodedPng, Charsets.ISO_8859_1)
        assertFalse("Embedded PNG must not contain tEXt metadata chunk", pngString.contains("tEXt"))
        assertFalse("Embedded PNG must not contain author in chunk", pngString.contains("Dr. Jane Doe"))

        // Visual elements must be preserved
        assertTrue("Cleaned SVG must preserve width attribute", cleaned.contains("width=\"800\"") || cleaned.contains("width='800'"))
        assertTrue("Cleaned SVG must preserve height attribute", cleaned.contains("height=\"600\"") || cleaned.contains("height='600'"))
        assertTrue("Cleaned SVG must preserve rect elements", cleaned.contains("<rect"))
        assertTrue("Cleaned SVG must preserve text elements", cleaned.contains("<text"))
    }

    private fun writePngChunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
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
