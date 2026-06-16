package com.heronikostudios.metajammer.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.heronikostudios.metajammer.R
import com.heronikostudios.metajammer.data.FileRepository
import com.heronikostudios.metajammer.data.MetadataRepository
import com.heronikostudios.metajammer.domain.model.MetadataReplacementPlan
import com.heronikostudios.metajammer.domain.model.ProcessingMode
import com.heronikostudios.metajammer.domain.model.ThumbnailHandling
import com.heronikostudios.metajammer.util.SanitizationUtils
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import androidx.core.net.toUri

class MetadataProcessingWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    private val fileRepository = FileRepository(applicationContext)
    private val metadataRepository = MetadataRepository(fileRepository)

    companion object {
        const val KEY_INPUT_URIS = "input_uris"
        const val KEY_MODE = "mode"
        const val KEY_KEEP_ORIENTATION = "keep_orientation"
        const val KEY_THUMBNAIL_HANDLING = "thumbnail_handling"
        const val KEY_PLANS_FILE_PATH = "plans_file_path"
        const val KEY_SAVING_PATH = "saving_path"
        const val KEY_DEFAULT_PREFIX = "default_prefix"
        const val KEY_DEFAULT_SUFFIX = "default_suffix"
        const val KEY_USE_RANDOM_NAMES = "use_random_names"

        const val CHANNEL_ID = "metadata_processing"
        const val NOTIFICATION_ID = 1001
    }

    override suspend fun doWork(): Result {
        val inputUriStrings = inputData.getStringArray(KEY_INPUT_URIS) ?: return Result.failure()
        val modeString = inputData.getString(KEY_MODE) ?: return Result.failure()
        val keepOrientation = inputData.getBoolean(KEY_KEEP_ORIENTATION, true)
        val thumbnailHandlingString = inputData.getString(KEY_THUMBNAIL_HANDLING) ?: ThumbnailHandling.REMOVE.name
        val plansFilePath = inputData.getString(KEY_PLANS_FILE_PATH)
        val savingPath = inputData.getString(KEY_SAVING_PATH)
        val defaultPrefix = inputData.getString(KEY_DEFAULT_PREFIX) ?: ""
        val defaultSuffix = inputData.getString(KEY_DEFAULT_SUFFIX) ?: "_processed"
        val useRandomNames = inputData.getBoolean(KEY_USE_RANDOM_NAMES, false)

        val mode = ProcessingMode.valueOf(modeString)
        val thumbnailHandling = ThumbnailHandling.valueOf(thumbnailHandlingString)

        val plans: Map<String, MetadataReplacementPlan> = if (plansFilePath != null) {
            runCatching {
                val json = File(plansFilePath).readText()
                Json.decodeFromString<Map<String, MetadataReplacementPlan>>(json)
            }.onFailure {
                Timber.e(it, "Failed to load replacement plans from %s", plansFilePath)
            }.getOrDefault(emptyMap())
        } else {
            emptyMap()
        }

        setForeground(createForegroundInfo(inputUriStrings.size))

        var processedCount = 0
        val savedUris = mutableListOf<String>()

        inputUriStrings.forEachIndexed { index, uriString ->
            val uri = uriString.toUri()
            val selectedFile = fileRepository.getSelectedFile(uri)
            val plan = plans[uriString]

            runCatching {
                val processedFile = metadataRepository.processFile(
                    selectedFile = selectedFile,
                    mode = mode,
                    keepOrientation = keepOrientation,
                    thumbnailHandling = thumbnailHandling,
                    replacementPlan = plan
                )

                val displayName = SanitizationUtils.generateOutputName(
                    originalName = selectedFile.displayName,
                    useRandomFileNames = useRandomNames,
                    prefix = defaultPrefix,
                    suffix = defaultSuffix
                )

                val savedUri = fileRepository.saveToDefaultFolder(
                    sourceFile = processedFile,
                    displayName = displayName,
                    mimeType = selectedFile.mimeType,
                    configuredPath = savingPath
                )
                
                // Cleanup processedFile after saving
                runCatching { processedFile.delete() }
                
                savedUri?.let { savedUris.add(it.toString()) }
                processedCount++
                
                // Update progress
                setProgress(workDataOf("progress" to (index + 1) * 100 / inputUriStrings.size))
            }.onFailure {
                Timber.e(it, "Failed to process file: %s", uriString)
            }
        }

        showCompletionNotification(processedCount)

        // Cleanup plans file
        plansFilePath?.let { File(it).delete() }

        return Result.success(workDataOf("saved_uris" to savedUris.toTypedArray()))
    }

    private fun createForegroundInfo(totalFiles: Int): ForegroundInfo {
        createNotificationChannel()

        val title = applicationContext.getString(R.string.notification_processing_title)
        val content = applicationContext.getString(R.string.notification_processing_message, totalFiles)

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Use foreground as temporary icon
            .setOngoing(true)
            .setProgress(100, 0, false)
            .build()

        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private fun showCompletionNotification(count: Int) {
        val title = applicationContext.getString(R.string.notification_complete_title)
        val content = applicationContext.getString(R.string.notification_complete_message, count)

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        try {
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID + 1, notification)
        } catch (e: SecurityException) {
            // Permission not granted
            Timber.e(e, "Failed to show completion notification: permission not granted")
        }
    }

    private fun createNotificationChannel() {
        val name = applicationContext.getString(R.string.notification_channel_name)
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(CHANNEL_ID, name, importance)
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
