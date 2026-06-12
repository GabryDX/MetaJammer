package com.heronikostudios.metajammer.metadata

import android.net.Uri
import com.heronikostudios.metajammer.data.FileRepository
import java.io.File

class VideoMetadataProcessor(
    private val fileRepository: FileRepository
) {

    fun removeMetadata(inputUri: Uri): File {
        // MVP placeholder:
        // Copy the file into cache as a passthrough.
        // Real metadata rewriting for video containers requires more specialized tooling.
        return copyVideo(inputUri, "video_clean_")
    }

    fun poisonMetadata(inputUri: Uri): File {
        // MVP placeholder:
        // Copy the file into cache as a passthrough.
        return copyVideo(inputUri, "video_poisoned_")
    }

    private fun copyVideo(inputUri: Uri, prefix: String): File {
        val inputFile = fileRepository.copyUriToCache(inputUri, prefix = "video_in_", suffix = ".mp4")
        val outputFile = fileRepository.createSharedTempFile(prefix, ".mp4")
        inputFile.copyTo(outputFile, overwrite = true)
        return outputFile
    }
}
