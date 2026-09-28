package com.heronikostudios.metajammer.metadata

import android.net.Uri
import com.heronikostudios.metajammer.data.FileRepository
import com.heronikostudios.metajammer.domain.model.MetadataEntry
import com.heronikostudios.metajammer.domain.model.MetadataReplacementPlan
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.File
import java.io.StringWriter
import java.util.Base64
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

class SvgMetadataProcessor(
    private val fileRepository: FileRepository
) {

    companion object {
        private val COMMENT_TAG_PATTERN = Pattern.compile("<!--[\\s\\S]*?-->")
        private val METADATA_TAG_PATTERN = Pattern.compile("<metadata>.*?</metadata>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
        private val TITLE_TAG_PATTERN = Pattern.compile("<title>.*?</title>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
        private val DESC_TAG_PATTERN = Pattern.compile("<desc>.*?</desc>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
        private val SODIPODI_TAG_PATTERN = Pattern.compile("<sodipodi:namedview.*?(/>|>.*?</sodipodi:namedview>)", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)

        private val METADATA_ELEMENT_NAMES = setOf(
            "metadata", "title", "desc", "rdf:rdf",
            "sodipodi:namedview", "inkscape:perspective", "inkscape:grid", "inkscape:guide"
        )

        private fun createSafeDocumentBuilderFactory(): DocumentBuilderFactory {
            return DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                // Security: Disable external DTDs and entities to prevent XXE attacks
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
                isXIncludeAware = false
                isExpandEntityReferences = false
            }
        }

        fun parseDocument(xmlString: String): Document {
            val sanitized = xmlString.trim().removePrefix("\uFEFF")
            val factory = createSafeDocumentBuilderFactory()
            val builder = factory.newDocumentBuilder()
            return builder.parse(ByteArrayInputStream(sanitized.toByteArray(Charsets.UTF_8)))
        }

        fun documentToXmlString(doc: Document): String {
            val transformer = TransformerFactory.newInstance().newTransformer().apply {
                setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
                setOutputProperty(OutputKeys.METHOD, "xml")
                setOutputProperty(OutputKeys.INDENT, "yes")
                setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            }
            val writer = StringWriter()
            transformer.transform(DOMSource(doc), StreamResult(writer))
            return writer.toString()
        }

        /**
         * Cleans an SVG document by stripping XML comments, metadata elements (<title>, <desc>, <metadata>, <rdf:RDF>, editor tags),
         * editor tracking attributes (Inkscape, Sodipodi, Illustrator), and scrubbing embedded base64 raster images.
         */
        fun cleanSvgDocument(doc: Document) {
            // 1. Remove XML comments from entire document (including root level)
            removeComments(doc)

            // 2. Remove metadata, title, desc, rdf, and editor-specific elements
            removeMetadataNodes(doc.documentElement)

            // 3. Remove editor-specific attributes throughout the tree
            removeEditorAttributes(doc.documentElement)

            // 4. Scrub embedded base64 raster images (PNG, JPEG) inside <image> tags
            scrubEmbeddedImages(doc.documentElement)
        }

        private fun removeComments(node: Node) {
            val children = node.childNodes
            val toRemove = mutableListOf<Node>()
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child.nodeType == Node.COMMENT_NODE) {
                    toRemove.add(child)
                } else if (child.hasChildNodes()) {
                    removeComments(child)
                }
            }
            toRemove.forEach { it.parentNode?.removeChild(it) }
        }

        private fun removeMetadataNodes(node: Node) {
            val childNodes = node.childNodes
            val toRemove = mutableListOf<Node>()
            for (i in 0 until childNodes.length) {
                val child = childNodes.item(i)
                val nodeName = child.nodeName.lowercase()
                val localName = child.localName?.lowercase()
                if (nodeName in METADATA_ELEMENT_NAMES || localName in METADATA_ELEMENT_NAMES) {
                    toRemove.add(child)
                } else if (child.hasChildNodes()) {
                    removeMetadataNodes(child)
                }
            }
            toRemove.forEach { it.parentNode?.removeChild(it) }
        }

        private fun removeEditorAttributes(node: Node) {
            if (node is Element) {
                val attrs = node.attributes
                val toRemove = mutableListOf<String>()
                for (i in 0 until attrs.length) {
                    val attrName = attrs.item(i).nodeName
                    val lower = attrName.lowercase()
                    if (lower.startsWith("inkscape:") ||
                        lower.startsWith("sodipodi:") ||
                        lower.startsWith("sketch:") ||
                        lower.startsWith("i:") ||
                        lower.startsWith("adobe-") ||
                        lower == "xmlns:inkscape" ||
                        lower == "xmlns:sodipodi" ||
                        lower == "xmlns:i" ||
                        lower == "xmlns:graph"
                    ) {
                        toRemove.add(attrName)
                    }
                }
                toRemove.forEach { node.removeAttribute(it) }
            }
            val children = node.childNodes
            for (i in 0 until children.length) {
                removeEditorAttributes(children.item(i))
            }
        }

        private fun scrubEmbeddedImages(node: Node) {
            if (node is Element && (node.nodeName.equals("image", ignoreCase = true) || node.localName.equals("image", ignoreCase = true))) {
                val hrefAttr = when {
                    node.hasAttribute("xlink:href") -> "xlink:href"
                    node.hasAttribute("href") -> "href"
                    else -> null
                }
                if (hrefAttr != null) {
                    val hrefVal = node.getAttribute(hrefAttr)
                    val pngPrefix = "data:image/png;base64,"
                    val jpegPrefix = "data:image/jpeg;base64,"
                    val jpgPrefix = "data:image/jpg;base64,"

                    when {
                        hrefVal.startsWith(pngPrefix, ignoreCase = true) -> {
                            val rawBase64 = hrefVal.substring(pngPrefix.length).trim()
                            runCatching {
                                val decoded = Base64.getDecoder().decode(rawBase64)
                                val stripped = ImageMetadataProcessor.stripPngChunks(decoded)
                                if (stripped != null) {
                                    val reencoded = Base64.getEncoder().encodeToString(stripped)
                                    node.setAttribute(hrefAttr, "$pngPrefix$reencoded")
                                }
                            }.onFailure { e ->
                                Timber.w(e, "Failed to strip embedded PNG chunks in SVG")
                            }
                        }
                        hrefVal.startsWith(jpegPrefix, ignoreCase = true) || hrefVal.startsWith(jpgPrefix, ignoreCase = true) -> {
                            val prefix = if (hrefVal.startsWith(jpegPrefix, ignoreCase = true)) jpegPrefix else jpgPrefix
                            val rawBase64 = hrefVal.substring(prefix.length).trim()
                            runCatching {
                                val decoded = Base64.getDecoder().decode(rawBase64)
                                val stripped = ImageMetadataProcessor.stripJpegMarkers(decoded)
                                if (stripped != null) {
                                    val reencoded = Base64.getEncoder().encodeToString(stripped)
                                    node.setAttribute(hrefAttr, "$prefix$reencoded")
                                }
                            }.onFailure { e ->
                                Timber.w(e, "Failed to strip embedded JPEG markers in SVG")
                            }
                        }
                    }
                }
            }
            val children = node.childNodes
            for (i in 0 until children.length) {
                scrubEmbeddedImages(children.item(i))
            }
        }

        fun cleanSvgString(content: String): String {
            return runCatching {
                val doc = parseDocument(content)
                cleanSvgDocument(doc)
                documentToXmlString(doc)
            }.getOrElse {
                Timber.w(it, "DOM parsing failed for SVG, falling back to regex clean")
                fallbackRegexClean(content)
            }
        }

        fun poisonSvgString(content: String, plan: MetadataReplacementPlan): String {
            return runCatching {
                val doc = parseDocument(content)
                cleanSvgDocument(doc)

                val root = doc.documentElement
                val titleElem = doc.createElement("title").apply {
                    textContent = plan.imageDescription
                }
                val descElem = doc.createElement("desc").apply {
                    textContent = "Software: ${plan.software}, Created: ${plan.dateTime}"
                }
                val metadataElem = doc.createElement("metadata").apply {
                    val rdf = doc.createElementNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "rdf:RDF")
                    val desc = doc.createElementNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "rdf:Description").apply {
                        setAttributeNS("http://purl.org/dc/elements/1.1/", "dc:creator", "${plan.make} ${plan.model}")
                        setAttributeNS("http://purl.org/dc/elements/1.1/", "dc:date", plan.dateTime)
                    }
                    rdf.appendChild(desc)
                    appendChild(rdf)
                }

                val firstChild = root.firstChild
                root.insertBefore(titleElem, firstChild)
                root.insertBefore(descElem, firstChild)
                root.insertBefore(metadataElem, firstChild)

                documentToXmlString(doc)
            }.getOrElse {
                Timber.w(it, "DOM parsing failed for SVG poisoning, falling back to regex")
                fallbackRegexPoison(content, plan)
            }
        }

        fun readSvgMetadataString(content: String): List<MetadataEntry> {
            val entries = mutableListOf<MetadataEntry>()
            runCatching {
                val doc = parseDocument(content)
                val titles = doc.getElementsByTagName("title")
                if (titles.length > 0) {
                    val titleText = titles.item(0).textContent?.trim().orEmpty()
                    if (titleText.isNotEmpty()) entries.add(MetadataEntry("Title", titleText))
                }

                val descs = doc.getElementsByTagName("desc")
                if (descs.length > 0) {
                    val descText = descs.item(0).textContent?.trim().orEmpty()
                    if (descText.isNotEmpty()) entries.add(MetadataEntry("Description", descText))
                }

                val metadatas = doc.getElementsByTagName("metadata")
                if (metadatas.length > 0) {
                    entries.add(MetadataEntry("Metadata Tag", "Present (XMP/RDF)"))
                }

                var commentCount = 0
                fun countComments(n: Node) {
                    val children = n.childNodes
                    for (i in 0 until children.length) {
                        val child = children.item(i)
                        if (child.nodeType == Node.COMMENT_NODE) commentCount++
                        if (child.hasChildNodes()) countComments(child)
                    }
                }
                countComments(doc)
                if (commentCount > 0) {
                    entries.add(MetadataEntry("Comments", "$commentCount comment(s)"))
                }
            }.onFailure {
                // Fallback to regex reading if XML parsing fails
                val titleMatcher = TITLE_TAG_PATTERN.matcher(content)
                if (titleMatcher.find()) {
                    val title = titleMatcher.group().replace(Regex("<.*?>"), "").trim()
                    if (title.isNotEmpty()) entries.add(MetadataEntry("Title", title))
                }

                val descMatcher = DESC_TAG_PATTERN.matcher(content)
                if (descMatcher.find()) {
                    val desc = descMatcher.group().replace(Regex("<.*?>"), "").trim()
                    if (desc.isNotEmpty()) entries.add(MetadataEntry("Description", desc))
                }

                val metadataMatcher = METADATA_TAG_PATTERN.matcher(content)
                if (metadataMatcher.find()) {
                    entries.add(MetadataEntry("Metadata Tag", "Present (likely XMP/RDF)"))
                }
            }
            return entries
        }

        private fun fallbackRegexClean(content: String): String {
            var cleaned = COMMENT_TAG_PATTERN.matcher(content).replaceAll("")
            cleaned = METADATA_TAG_PATTERN.matcher(cleaned).replaceAll("")
            cleaned = TITLE_TAG_PATTERN.matcher(cleaned).replaceAll("")
            cleaned = DESC_TAG_PATTERN.matcher(cleaned).replaceAll("")
            cleaned = SODIPODI_TAG_PATTERN.matcher(cleaned).replaceAll("")
            return cleaned
        }

        private fun fallbackRegexPoison(content: String, plan: MetadataReplacementPlan): String {
            val cleaned = fallbackRegexClean(content)
            val svgTagMatcher = Pattern.compile("<svg.*?>", Pattern.CASE_INSENSITIVE).matcher(cleaned)
            return if (svgTagMatcher.find()) {
                val index = svgTagMatcher.end()
                val fakeMetadata = "\n  <title>${plan.imageDescription}</title>\n" +
                                   "  <desc>Software: ${plan.software}, Created: ${plan.dateTime}</desc>\n" +
                                   "  <metadata>\n" +
                                   "    <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n" +
                                   "      <rdf:Description rdf:about=\"\" dc:creator=\"${plan.make} ${plan.model}\" dc:date=\"${plan.dateTime}\" />\n" +
                                   "    </rdf:RDF>\n" +
                                   "  </metadata>"
                StringBuilder(cleaned).insert(index, fakeMetadata).toString()
            } else {
                cleaned
            }
        }
    }

    fun readMetadata(uri: Uri): List<MetadataEntry> {
        return try {
            val content = fileRepository.getContext().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: return emptyList()
            readSvgMetadataString(content)
        } catch (e: Exception) {
            Timber.e(e, "Error reading SVG metadata")
            emptyList()
        }
    }

    fun removeMetadata(uri: Uri): File {
        val content = fileRepository.getContext().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalStateException("Could not read SVG from $uri")

        val cleaned = cleanSvgString(content)
        val outputFile = fileRepository.createCacheFile(prefix = "svg_clean_", suffix = ".svg")
        outputFile.writeText(cleaned)
        return outputFile
    }

    fun poisonMetadata(uri: Uri, plan: MetadataReplacementPlan): File {
        val content = fileRepository.getContext().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalStateException("Could not read SVG from $uri")

        val poisoned = poisonSvgString(content, plan)
        val outputFile = fileRepository.createCacheFile(prefix = "svg_poisoned_", suffix = ".svg")
        outputFile.writeText(poisoned)
        return outputFile
    }
}
