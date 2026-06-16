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

    fun removeMetadata(inputUri: Uri): File {
        val extension = fileRepository.getExtension(inputUri)
        val inputFile = fileRepository.copyUriToCache(inputUri, prefix = "media_in_", suffix = extension)
        val outputFile = fileRepository.createSharedTempFile("media_clean_", extension)
        
        return try {
            remuxMedia(inputFile, outputFile, null)
            outputFile
        } catch (e: Exception) {
            Timber.e(e, "Error removing metadata from media")
            // Security: Delete output if failed and throw to prevent leaking original
            outputFile.delete()
            throw e
        } finally {
            inputFile.delete()
        }
    }

    fun poisonMetadata(inputUri: Uri, plan: MetadataReplacementPlan): File {
        val extension = fileRepository.getExtension(inputUri)
        val inputFile = fileRepository.copyUriToCache(inputUri, prefix = "media_in_", suffix = extension)
        val outputFile = fileRepository.createSharedTempFile("media_poisoned_", extension)
        
        return try {
            remuxMedia(inputFile, outputFile, plan)
            outputFile
        } catch (e: Exception) {
            Timber.e(e, "Error poisoning metadata in media")
            outputFile.delete()
            throw e
        } finally {
            inputFile.delete()
        }
    }

    /**
     * Re-muxes a media file to strip metadata atoms.
     * Optionally sets a new location and metadata.
     */
    private fun remuxMedia(input: File, output: File, plan: MetadataReplacementPlan?) {
        val extractor = MediaExtractor()
        extractor.setDataSource(input.absolutePath)

        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        // Inject location if poisoning
        plan?.let { 
            muxer.setLocation(it.latitude.toFloat(), it.longitude.toFloat())
        }

        val trackCount = extractor.trackCount
        val trackMap = HashMap<Int, Int>()

        for (i in 0 until trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            
            // We only mux video and audio tracks to ensure clean stripping of data tracks
            if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                extractor.selectTrack(i)
                val newTrackIndex = muxer.addTrack(format)
                trackMap[i] = newTrackIndex
            }
        }

        if (trackMap.isEmpty()) {
            extractor.release()
            muxer.release()
            throw IllegalStateException("No valid video or audio tracks found")
        }

        muxer.start()

        val bufferSize = 1024 * 1024 // 1MB buffer
        val byteBuffer = ByteBuffer.allocate(bufferSize)
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
                muxer.writeSampleData(muxerTrackIndex, byteBuffer, bufferInfo)
            }
            
            extractor.advance()
        }

        muxer.stop()
        muxer.release()
        extractor.release()
    }
}
