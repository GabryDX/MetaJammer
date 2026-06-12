package com.heronikostudios.metajammer.metadata

import android.net.Uri
import com.heronikostudios.metajammer.data.FileRepository
import java.io.File

/**
 * Handles metadata processing for video files.
 * 
 * NOTE: Current implementation is a placeholder. Real metadata stripping for videos
 * requires re-muxing the file (e.g., using MediaMuxer and MediaExtractor) to strip
 * location data and other atoms from the MP4/MOV container.
 */
class VideoMetadataProcessor(
    private val fileRepository: FileRepository
) {

    fun removeMetadata(inputUri: Uri): File {
        // TODO: Implement MediaMuxer based stripping to actually remove metadata atoms.
        // For now, we perform a passthrough but warn the user in the UI.
        return copyVideo(inputUri, "video_clean_")
    }

    fun poisonMetadata(inputUri: Uri): File {
        // TODO: Implement MediaMuxer based poisoning.
        return copyVideo(inputUri, "video_poisoned_")
    }

    private fun copyVideo(inputUri: Uri, prefix: String): File {
        val inputFile = fileRepository.copyUriToCache(inputUri, prefix = "video_in_", suffix = ".mp4")
        val outputFile = fileRepository.createSharedTempFile(prefix, ".mp4")
        inputFile.copyTo(outputFile, overwrite = true)
        return outputFile
    }
}
