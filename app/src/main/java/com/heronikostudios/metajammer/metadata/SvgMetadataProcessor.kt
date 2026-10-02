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
        private val METADATA_TAG_PATTERN = Pattern.compile("<metadata.*?>.*?</metadata>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
        private val TITLE_TAG_PATTERN = Pattern.compile("<title.*?>.*?</title>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
        private val DESC_TAG_PATTERN = Pattern.compile("<desc.*?>.*?</desc>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
        private val SODIPODI_TAG_PATTERN = Pattern.compile("<sodipodi:namedview.*?(/>|>.*?</sodipodi:namedview>)", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)

        private val METADATA_ELEMENT_NAMES = setOf(
            "metadata", "title", "desc", "rdf", "rdf:rdf",
            "sodipodi:namedview", "inkscape:perspective", "inkscape:grid", "inkscape:guide",
            "cc:work", "work", "cc:license"
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

                // 1. Title: check root <title> element or Dublin Core title
                val titles = doc.getElementsByTagName("title")
                var titleText = if (titles.length > 0) titles.item(0).textContent?.trim().orEmpty() else ""
                if (titleText.isEmpty()) {
                    titleText = findTextByTagOrLocalName(doc, "title").orEmpty()
                }
                if (titleText.isNotEmpty()) entries.add(MetadataEntry("Title", titleText))

                // 2. Creator / Author: check dc:creator or author
                val creatorText = findTextByTagOrLocalName(doc, "creator") ?: findTextByTagOrLocalName(doc, "author")
                if (!creatorText.isNullOrBlank()) {
                    entries.add(MetadataEntry("Creator", creatorText))
                }

                // 3. Description: check <desc> or dc:description
                val descs = doc.getElementsByTagName("desc")
                var descText = if (descs.length > 0) descs.item(0).textContent?.trim().orEmpty() else ""
                if (descText.isEmpty()) {
                    descText = findTextByTagOrLocalName(doc, "description").orEmpty()
                }
                if (descText.isNotEmpty()) entries.add(MetadataEntry("Description", descText))

                // 4. Date: dc:date
                val dateText = findTextByTagOrLocalName(doc, "date")
                if (!dateText.isNullOrBlank()) {
                    entries.add(MetadataEntry("Date", dateText))
                }

                // 5. Location / Coverage: dc:coverage
                val coverageText = findTextByTagOrLocalName(doc, "coverage")
                if (!coverageText.isNullOrBlank()) {
                    entries.add(MetadataEntry("Location", coverageText))
                }

                // 6. Copyright / Rights: dc:rights
                val rightsText = findTextByTagOrLocalName(doc, "rights")
                if (!rightsText.isNullOrBlank()) {
                    entries.add(MetadataEntry("Copyright", rightsText))
                }

                // 7. Publisher: dc:publisher
                val publisherText = findTextByTagOrLocalName(doc, "publisher")
                if (!publisherText.isNullOrBlank()) {
                    entries.add(MetadataEntry("Publisher", publisherText))
                }

                // 8. Contributor: dc:contributor
                val contributorText = findTextByTagOrLocalName(doc, "contributor")
                if (!contributorText.isNullOrBlank()) {
                    entries.add(MetadataEntry("Contributor", contributorText))
                }

                // 9. Subject / Keywords: dc:subject
                val subjectText = findTextByTagOrLocalName(doc, "subject")
                if (!subjectText.isNullOrBlank()) {
                    entries.add(MetadataEntry("Keywords", subjectText))
                }

                // 10. License: cc:license or license
                val licenseText = findLicense(doc)
                if (!licenseText.isNullOrBlank()) {
                    entries.add(MetadataEntry("License", licenseText))
                }

                // 11. Identifier: dc:identifier
                val idText = findTextByTagOrLocalName(doc, "identifier")
                if (!idText.isNullOrBlank()) {
                    entries.add(MetadataEntry("Identifier", idText))
                }

                // 12. Source: dc:source
                val sourceText = findTextByTagOrLocalName(doc, "source")
                if (!sourceText.isNullOrBlank()) {
                    entries.add(MetadataEntry("Source", sourceText))
                }

                // 13. Editor attributes on root element: inkscape:version, sodipodi:docname
                val root = doc.documentElement
                if (root != null) {
                    val inkscapeVersion = root.getAttribute("inkscape:version")
                    if (inkscapeVersion.isNotBlank()) {
                        entries.add(MetadataEntry("Software", "Inkscape $inkscapeVersion"))
                    }
                    val docname = root.getAttribute("sodipodi:docname")
                    if (docname.isNotBlank()) {
                        entries.add(MetadataEntry("Document Name", docname))
                    }
                }

                // 14. Embedded raster images:
                val images = doc.getElementsByTagName("image")
                var embeddedCount = 0
                for (i in 0 until images.length) {
                    val img = images.item(i) as? Element ?: continue
                    val href = img.getAttribute("xlink:href").ifBlank { img.getAttribute("href") }
                    if (href.startsWith("data:image/", ignoreCase = true)) {
                        embeddedCount++
                    }
                }
                if (embeddedCount > 0) {
                    entries.add(MetadataEntry("Embedded Images", "$embeddedCount embedded image(s)"))
                }

                // 15. Comments
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

                // 16. Metadata tag presence indicator
                val metadatas = doc.getElementsByTagName("metadata")
                if (metadatas.length > 0) {
                    entries.add(MetadataEntry("Metadata Tag", "Present (XMP/RDF)"))
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

                val creatorMatcher = Pattern.compile("<(?:dc:)?creator.*?>([\\s\\S]*?)</(?:dc:)?creator>", Pattern.CASE_INSENSITIVE).matcher(content)
                if (creatorMatcher.find()) {
                    val creator = creatorMatcher.group(1).orEmpty().replace(Regex("<.*?>"), "").trim().replace(Regex("\\s+"), " ")
                    if (creator.isNotEmpty()) entries.add(MetadataEntry("Creator", creator))
                }

                val dateMatcher = Pattern.compile("<(?:dc:)?date.*?>([\\s\\S]*?)</(?:dc:)?date>", Pattern.CASE_INSENSITIVE).matcher(content)
                if (dateMatcher.find()) {
                    val date = dateMatcher.group(1).orEmpty().replace(Regex("<.*?>"), "").trim()
                    if (date.isNotEmpty()) entries.add(MetadataEntry("Date", date))
                }

                val coverageMatcher = Pattern.compile("<(?:dc:)?coverage.*?>([\\s\\S]*?)</(?:dc:)?coverage>", Pattern.CASE_INSENSITIVE).matcher(content)
                if (coverageMatcher.find()) {
                    val coverage = coverageMatcher.group(1).orEmpty().replace(Regex("<.*?>"), "").trim()
                    if (coverage.isNotEmpty()) entries.add(MetadataEntry("Location", coverage))
                }

                val rightsMatcher = Pattern.compile("<(?:dc:)?rights.*?>([\\s\\S]*?)</(?:dc:)?rights>", Pattern.CASE_INSENSITIVE).matcher(content)
                if (rightsMatcher.find()) {
                    val rights = rightsMatcher.group(1).orEmpty().replace(Regex("<.*?>"), "").trim().replace(Regex("\\s+"), " ")
                    if (rights.isNotEmpty()) entries.add(MetadataEntry("Copyright", rights))
                }

                val inkscapeMatcher = Pattern.compile("inkscape:version=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(content)
                if (inkscapeMatcher.find()) {
                    val ver = inkscapeMatcher.group(1).orEmpty()
                    if (ver.isNotEmpty()) entries.add(MetadataEntry("Software", "Inkscape $ver"))
                }

                val docnameMatcher = Pattern.compile("sodipodi:docname=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(content)
                if (docnameMatcher.find()) {
                    val name = docnameMatcher.group(1).orEmpty()
                    if (name.isNotEmpty()) entries.add(MetadataEntry("Document Name", name))
                }

                val metadataMatcher = METADATA_TAG_PATTERN.matcher(content)
                if (metadataMatcher.find()) {
                    entries.add(MetadataEntry("Metadata Tag", "Present (likely XMP/RDF)"))
                }

                var commentMatches = 0
                val commentMatcher = COMMENT_TAG_PATTERN.matcher(content)
                while (commentMatcher.find()) commentMatches++
                if (commentMatches > 0) {
                    entries.add(MetadataEntry("Comments", "$commentMatches comment(s)"))
                }
            }
            return entries
        }

        private fun findTextByTagOrLocalName(doc: Document, name: String): String? {
            val list = doc.getElementsByTagNameNS("*", name)
            for (i in 0 until list.length) {
                val elem = list.item(i)
                val raw = elem.textContent?.trim()?.replace(Regex("\\s+"), " ")
                if (!raw.isNullOrBlank()) return raw
            }
            val byTag = doc.getElementsByTagName(name)
            for (i in 0 until byTag.length) {
                val elem = byTag.item(i)
                val raw = elem.textContent?.trim()?.replace(Regex("\\s+"), " ")
                if (!raw.isNullOrBlank()) return raw
            }
            val byPrefix = doc.getElementsByTagName("dc:$name")
            for (i in 0 until byPrefix.length) {
                val elem = byPrefix.item(i)
                val raw = elem.textContent?.trim()?.replace(Regex("\\s+"), " ")
                if (!raw.isNullOrBlank()) return raw
            }
            return null
        }

        private fun findLicense(doc: Document): String? {
            val list = doc.getElementsByTagNameNS("*", "license")
            for (i in 0 until list.length) {
                val elem = list.item(i) as? Element ?: continue
                val res = elem.getAttribute("rdf:resource").ifBlank { elem.getAttribute("resource") }
                if (res.isNotBlank()) return res
                val text = elem.textContent?.trim()?.replace(Regex("\\s+"), " ")
                if (!text.isNullOrBlank()) return text
            }
            val byTag = doc.getElementsByTagName("cc:license")
            for (i in 0 until byTag.length) {
                val elem = byTag.item(i) as? Element ?: continue
                val res = elem.getAttribute("rdf:resource").ifBlank { elem.getAttribute("resource") }
                if (res.isNotBlank()) return res
            }
            return null
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
