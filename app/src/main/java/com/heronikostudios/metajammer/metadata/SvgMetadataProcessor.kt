package com.heronikostudios.metajammer.metadata

import android.net.Uri
import com.heronikostudios.metajammer.data.FileRepository
import com.heronikostudios.metajammer.domain.model.MetadataEntry
import com.heronikostudios.metajammer.domain.model.MetadataReplacementPlan
import timber.log.Timber
import java.io.File
import java.util.regex.Pattern

class SvgMetadataProcessor(
    private val fileRepository: FileRepository
) {

    private val metadataTagPattern = Pattern.compile("<metadata>.*?</metadata>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
    private val titleTagPattern = Pattern.compile("<title>.*?</title>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
    private val descTagPattern = Pattern.compile("<desc>.*?</desc>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)

    fun readMetadata(uri: Uri): List<MetadataEntry> {
        val entries = mutableListOf<MetadataEntry>()
        try {
            val content = fileRepository.getContext().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return emptyList()
            
            val titleMatcher = titleTagPattern.matcher(content)
            if (titleMatcher.find()) {
                val title = titleMatcher.group().replace(Regex("<.*?>"), "").trim()
                if (title.isNotEmpty()) entries.add(MetadataEntry("Title", title))
            }

            val descMatcher = descTagPattern.matcher(content)
            if (descMatcher.find()) {
                val desc = descMatcher.group().replace(Regex("<.*?>"), "").trim()
                if (desc.isNotEmpty()) entries.add(MetadataEntry("Description", desc))
            }

            val metadataMatcher = metadataTagPattern.matcher(content)
            if (metadataMatcher.find()) {
                entries.add(MetadataEntry("Metadata Tag", "Present (likely XMP/RDF)"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error reading SVG metadata")
        }
        return entries
    }

    fun removeMetadata(uri: Uri): File {
        val content = fileRepository.getContext().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: throw Exception("Could not read SVG")
        
        var cleaned = metadataTagPattern.matcher(content).replaceAll("")
        cleaned = titleTagPattern.matcher(cleaned).replaceAll("")
        cleaned = descTagPattern.matcher(cleaned).replaceAll("")
        
        val outputFile = fileRepository.createCacheFile(prefix = "svg_clean_", suffix = ".svg")
        outputFile.writeText(cleaned)
        return outputFile
    }

    fun poisonMetadata(uri: Uri, plan: MetadataReplacementPlan): File {
        val content = fileRepository.getContext().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: throw Exception("Could not read SVG")
        
        // Remove old metadata
        var cleaned = metadataTagPattern.matcher(content).replaceAll("")
        cleaned = titleTagPattern.matcher(cleaned).replaceAll("")
        cleaned = descTagPattern.matcher(cleaned).replaceAll("")
        
        // Inject new metadata after <svg ...> tag
        val svgTagMatcher = Pattern.compile("<svg.*?>", Pattern.CASE_INSENSITIVE).matcher(cleaned)
        val poisoned = if (svgTagMatcher.find()) {
            val index = svgTagMatcher.end()
            val fakeMetadata = "\n  <title>${plan.imageDescription ?: "Poisoned Image"}</title>\n" +
                               "  <desc>Software: ${plan.software}, Created: ${plan.dateTime}</desc>\n" +
                               "  <metadata>\n" +
                               "    <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n" +
                               "      <rdf:Description rdf:about=\"\" dc:creator=\"${plan.make} ${plan.model}\" dc:date=\"${plan.dateTime}\" />\n" +
                               "    </rdf:RDF>\n" +
                               "  </metadata>"
            StringBuilder(cleaned).insert(index, fakeMetadata).toString()
        } else {
            cleaned // Fallback if <svg> tag not found (unlikely for valid SVG)
        }
        
        val outputFile = fileRepository.createCacheFile(prefix = "svg_poisoned_", suffix = ".svg")
        outputFile.writeText(poisoned)
        return outputFile
    }
}
