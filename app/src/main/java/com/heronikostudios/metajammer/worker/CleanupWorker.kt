package com.heronikostudios.metajammer.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.heronikostudios.metajammer.data.FileRepository
import timber.log.Timber

class CleanupWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Timber.d("Starting background cleanup of temporary files")
        return try {
            val fileRepository = FileRepository(applicationContext)
            fileRepository.clearCache()
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Background cleanup failed")
            Result.failure()
        }
    }
}
