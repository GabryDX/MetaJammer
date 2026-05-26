package com.heronikostudios.metajammer.data

import android.content.Context
import com.heronikostudios.metajammer.domain.model.ProcessingMode
import com.heronikostudios.metajammer.domain.model.SelectedFile
import com.heronikostudios.metajammer.metadata.ImageMetadataProcessor
import com.heronikostudios.metajammer.metadata.VideoMetadataProcessor
import java.io.File

class MetadataRepository(
    context: Context,
    private val fileRepository: FileRepository
) {
    private val imageProcessor = ImageMetadataProcessor(context, fileRepository)
    private val videoProcessor = VideoMetadataProcessor(context, fileRepository)

    fun processFile(selectedFile: SelectedFile, mode: ProcessingMode): File {
        return when {
            selectedFile.mimeType?.startsWith("image/") == true -> {
                when (mode) {
                    ProcessingMode.POISON_METADATA -> imageProcessor.poisonMetadata(selectedFile.uri)
                    ProcessingMode.REMOVE_METADATA -> imageProcessor.removeMetadata(selectedFile.uri)
                }
            }

            selectedFile.mimeType?.startsWith("video/") == true -> {
                when (mode) {
                    ProcessingMode.POISON_METADATA -> videoProcessor.poisonMetadata(selectedFile.uri)
                    ProcessingMode.REMOVE_METADATA -> videoProcessor.removeMetadata(selectedFile.uri)
                }
            }

            else -> {
                // Generic fallback: just copy file to cache unchanged
                fileRepository.copyUriToCache(selectedFile.uri, prefix = "generic_", suffix = null)
            }
        }
    }
}
