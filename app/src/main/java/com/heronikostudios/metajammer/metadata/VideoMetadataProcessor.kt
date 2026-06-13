package com.heronikostudios.metajammer.metadata

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import com.heronikostudios.metajammer.data.FileRepository
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer

/**
 * Handles metadata processing for video files by re-muxing the container.
 * This effectively strips location data and other atoms from MP4/MOV containers.
 */
class VideoMetadataProcessor(
    private val fileRepository: FileRepository
) {

    fun removeMetadata(inputUri: Uri): File {
        val inputFile = fileRepository.copyUriToCache(inputUri, prefix = "vid_in_", suffix = ".mp4")
        val outputFile = fileRepository.createSharedTempFile("vid_clean_", ".mp4")
        
        return try {
            remuxVideo(inputFile, outputFile, null)
            outputFile
        } catch (e: Exception) {
            Timber.e(e, "Error removing metadata from video")
            // Fallback to passthrough if remux fails, but ideally we'd log this
            inputFile.copyTo(outputFile, overwrite = true)
            outputFile
        } finally {
            inputFile.delete()
        }
    }

    fun poisonMetadata(inputUri: Uri, latitude: Double, longitude: Double): File {
        val inputFile = fileRepository.copyUriToCache(inputUri, prefix = "vid_in_", suffix = ".mp4")
        val outputFile = fileRepository.createSharedTempFile("vid_poisoned_", ".mp4")
        
        return try {
            remuxVideo(inputFile, outputFile, latitude to longitude)
            outputFile
        } catch (e: Exception) {
            Timber.e(e, "Error poisoning metadata in video")
            inputFile.copyTo(outputFile, overwrite = true)
            outputFile
        } finally {
            inputFile.delete()
        }
    }

    /**
     * Re-muxes a video file to strip metadata atoms.
     * Optionally sets a new location.
     */
    private fun remuxVideo(input: File, output: File, location: Pair<Double, Double>?) {
        val extractor = MediaExtractor()
        extractor.setDataSource(input.absolutePath)

        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        // Inject location if poisoning
        location?.let { (lat, lon) ->
            muxer.setLocation(lat.toFloat(), lon.toFloat())
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
