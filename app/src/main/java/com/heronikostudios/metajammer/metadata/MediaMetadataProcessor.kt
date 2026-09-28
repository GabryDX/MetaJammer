package com.heronikostudios.metajammer.metadata

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import com.heronikostudios.metajammer.data.FileRepository
import com.heronikostudios.metajammer.domain.model.MetadataReplacementPlan
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer

/**
 * Handles metadata processing for media files (Video and Audio) by re-muxing the container.
 * This effectively strips location data and other atoms from MP4, MOV, and M4A containers.
 */
class MediaMetadataProcessor(
    private val fileRepository: FileRepository
) {

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 1024 * 1024 // 1MB fallback
    }

    fun removeMetadata(inputUri: Uri, mimeType: String? = null): File {
        val extension = if (mimeType != null) {
            fileRepository.getExtensionFromMime(mimeType)
        } else {
            fileRepository.getExtension(inputUri)
        }
        val outputFile = fileRepository.createSharedTempFile("media_clean_", extension)
        
        return try {
            remuxMedia(inputUri, outputFile, null, mimeType)
            outputFile
        } catch (e: Exception) {
            Timber.e(e, "Error removing metadata from media")
            // Security: Delete output if failed and throw to prevent leaking original
            outputFile.delete()
            throw e
        }
    }

    fun poisonMetadata(inputUri: Uri, plan: MetadataReplacementPlan, mimeType: String? = null): File {
        val extension = if (mimeType != null) {
            fileRepository.getExtensionFromMime(mimeType)
        } else {
            fileRepository.getExtension(inputUri)
        }
        val outputFile = fileRepository.createSharedTempFile("media_poisoned_", extension)
        
        return try {
            remuxMedia(inputUri, outputFile, plan, mimeType)
            outputFile
        } catch (e: Exception) {
            Timber.e(e, "Error poisoning metadata in media")
            outputFile.delete()
            throw e
        }
    }

    /**
     * Re-muxes a media file to strip metadata atoms.
     * Preserves display orientation and optionally sets a new location.
     */
    private fun remuxMedia(inputUri: Uri, output: File, plan: MetadataReplacementPlan?, mimeType: String?) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        
        try {
            val context = fileRepository.getContext()
            context.contentResolver.openFileDescriptor(inputUri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)

                val outputFormat = when (mimeType) {
                    "video/webm", "audio/webm" -> MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
                    "audio/ogg" -> MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG
                    else -> MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                }

                val activeMuxer = MediaMuxer(output.absolutePath, outputFormat)
                muxer = activeMuxer

                // Inject location if poisoning
                plan?.let { 
                    activeMuxer.setLocation(it.latitude.toFloat(), it.longitude.toFloat())
                }

                var videoRotation: Int? = null
                val trackCount = extractor.trackCount
                val trackMap = HashMap<Int, Int>()

                for (i in 0 until trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                    
                    // We only mux video and audio tracks to ensure clean stripping of data tracks
                    if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                        extractor.selectTrack(i)

                        if (mime.startsWith("video/") && format.containsKey(MediaFormat.KEY_ROTATION)) {
                            videoRotation = format.getInteger(MediaFormat.KEY_ROTATION)
                        }

                        val newTrackIndex = activeMuxer.addTrack(format)
                        if (newTrackIndex != -1) {
                            trackMap[i] = newTrackIndex
                        }
                    }
                }

                if (trackMap.isEmpty()) {
                    throw IllegalStateException("No valid video or audio tracks found")
                }

                // If orientation wasn't in format, query via MediaMetadataRetriever
                if (videoRotation == null || videoRotation == 0) {
                    runCatching {
                        val retriever = android.media.MediaMetadataRetriever()
                        retriever.use { r ->
                            r.setDataSource(pfd.fileDescriptor)
                            r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()
                        }
                    }.getOrNull()?.let { rotation ->
                        if (rotation != 0) videoRotation = rotation
                    }
                }

                videoRotation?.let { rotation ->
                    Timber.d("Preserving video orientation: %d degrees", rotation)
                    activeMuxer.setOrientationHint(rotation)
                }

                // Calculate optimal buffer size based on tracks' MAX_INPUT_SIZE
                var maxInputSize = 0
                for (i in 0 until trackCount) {
                    if (trackMap.containsKey(i)) {
                        val format = extractor.getTrackFormat(i)
                        if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                            val size = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                            if (size > maxInputSize) maxInputSize = size
                        }
                    }
                }
                val bufferSize = if (maxInputSize > 0) maxInputSize else DEFAULT_BUFFER_SIZE
                Timber.d("Using buffer size: $bufferSize bytes (maxInputSize: $maxInputSize)")

                activeMuxer.start()

                val byteBuffer = ByteBuffer.allocateDirect(bufferSize)
                val bufferInfo = MediaCodec.BufferInfo()

                while (true) {
                    bufferInfo.offset = 0
                    bufferInfo.size = extractor.readSampleData(byteBuffer, 0)
                    
                    if (bufferInfo.size < 0) {
                        break
                    }

                    bufferInfo.presentationTimeUs = extractor.sampleTime
                    
                    // Map extractor flags to muxer flags
                    var sampleFlags = 0
                    if ((extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                        sampleFlags = sampleFlags or MediaCodec.BUFFER_FLAG_KEY_FRAME
                    }
                    bufferInfo.flags = sampleFlags
                    
                    val trackIndex = extractor.sampleTrackIndex
                    val muxerTrackIndex = trackMap[trackIndex]
                    
                    if (muxerTrackIndex != null) {
                        activeMuxer.writeSampleData(muxerTrackIndex, byteBuffer, bufferInfo)
                    }
                    
                    extractor.advance()
                }
            } ?: throw IllegalStateException("Could not open file descriptor for $inputUri")
        } finally {
            try {
                muxer?.stop()
            } catch (e: Exception) {
                Timber.w(e, "Error stopping muxer")
            }
            try {
                muxer?.release()
            } catch (e: Exception) {
                Timber.w(e, "Error releasing muxer")
            }
            extractor.release()
        }
    }
}
