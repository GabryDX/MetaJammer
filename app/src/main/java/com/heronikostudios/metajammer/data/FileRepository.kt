package com.heronikostudios.metajammer.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.heronikostudios.metajammer.domain.model.SelectedFile
import com.heronikostudios.metajammer.util.SanitizationUtils
import timber.log.Timber
import java.io.File
import java.util.Locale
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

open class FileRepository(private val context: Context) {

    fun getContext(): Context = context

    open fun getSelectedFile(uri: Uri): SelectedFile {
        val resolver = context.contentResolver
        var name = "unknown"
        var size: Long? = null
        val mimeType = resolver.getType(uri)

        resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)

                if (nameIndex != -1) {
                    name = SanitizationUtils.sanitizeFileName(cursor.getString(nameIndex))
                }
                if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }

        return SelectedFile(
            uri = uri,
            displayName = name,
            mimeType = mimeType,
            sizeBytes = size
        )
    }

    fun copyUriToCache(uri: Uri, prefix: String, suffix: String? = null): File {
        val resolvedSuffix = suffix ?: getExtension(uri)
        val tempFile = createSharedTempFile(prefix, resolvedSuffix)
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output, bufferSize = 64 * 1024)
            }
        }
        return tempFile
    }

    fun getExtension(uri: Uri): String {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri)
        var extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)

        if (extension == null) {
            val path = uri.path
            if (path != null) {
                val lastDot = path.lastIndexOf('.')
                if (lastDot != -1) {
                    extension = path.substring(lastDot + 1).lowercase(Locale.ROOT)
                }
            }
        }

        return if (extension != null) ".$extension" else ""
    }

    fun getExtensionFromMime(mimeType: String?): String {
        return when {
            mimeType?.startsWith("image/jpeg") == true -> ".jpg"
            mimeType?.startsWith("image/png") == true -> ".png"
            mimeType?.startsWith("image/webp") == true -> ".webp"
            mimeType?.startsWith("image/heif") == true -> ".heic"
            mimeType?.startsWith("image/heic") == true -> ".heic"
            mimeType?.startsWith("video/mp4") == true -> ".mp4"
            mimeType?.startsWith("video/quicktime") == true -> ".mov"
            mimeType?.startsWith("audio/mpeg") == true -> ".mp3"
            mimeType?.startsWith("audio/mp4") == true -> ".m4a"
            mimeType?.startsWith("audio/x-m4a") == true -> ".m4a"
            mimeType == "application/pdf" -> ".pdf"
            else -> ".bin"
        }
    }

    suspend fun saveToDefaultFolder(
        sourceFile: File,
        displayName: String,
        mimeType: String?,
        configuredPath: String?,
        subPath: String? = null
    ): Uri? = withContext(Dispatchers.IO) {
        val path = configuredPath ?: "Download/MetaJammer"
        val finalRelativePath = if (subPath != null) {
            if (path.endsWith("/")) "$path$subPath" else "$path/$subPath"
        } else {
            path
        }

        if (path.startsWith("content://")) {
            saveToCustomFolder(
                treeUri = path.toUri(),
                sourceFile = sourceFile,
                displayName = displayName,
                mimeType = mimeType,
                subPath = subPath
            )
        } else {
            saveToMediaStorePath(
                sourceFile = sourceFile,
                displayName = displayName,
                mimeType = mimeType,
                relativePath = finalRelativePath
            )
        }
    }

    private fun saveToMediaStorePath(
        sourceFile: File,
        displayName: String,
        mimeType: String?,
        relativePath: String
    ): Uri? {
        return runCatching {
            val resolver = context.contentResolver

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val collection = when {
                    mimeType?.startsWith("image/") == true -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    mimeType?.startsWith("video/") == true -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
                }

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType ?: "application/octet-stream")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val uri = resolver.insert(collection, values) ?: run {
                    Timber.e("Failed to insert media into MediaStore")
                    return null
                }

                resolver.openOutputStream(uri)?.use { output ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                    }
                } ?: run {
                    Timber.e("Failed to open output stream for MediaStore URI: %s", uri)
                    return null
                }

                val completedValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(uri, completedValues, null, null)

                uri
            } else {
                // Pre-Android 10 (Android 8 & 9, API 26-28): Direct file write to external storage + MediaScanner
                @Suppress("DEPRECATION")
                val targetDir = when {
                    mimeType?.startsWith("image/") == true ->
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    mimeType?.startsWith("video/") == true ->
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                    else ->
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                }
                val destDir = File(targetDir, "MetaJammer")
                if (!destDir.exists()) {
                    destDir.mkdirs()
                }
                val destFile = File(destDir, displayName)
                sourceFile.inputStream().use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                    }
                }

                // Index in MediaStore via MediaScannerConnection to obtain content URI
                var scannedUri: Uri? = null
                val latch = java.util.concurrent.CountDownLatch(1)
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destFile.absolutePath),
                    arrayOf(mimeType ?: "application/octet-stream")
                ) { _, uri ->
                    scannedUri = uri
                    latch.countDown()
                }
                latch.await(2, java.util.concurrent.TimeUnit.SECONDS)

                scannedUri ?: Uri.fromFile(destFile)
            }
        }.onFailure { e ->
            Timber.e(e, "Failed to save to MediaStore path for %s", displayName)
        }.getOrNull()
    }

    suspend fun saveToCustomFolder(
        treeUri: Uri,
        sourceFile: File,
        displayName: String,
        mimeType: String?,
        subPath: String? = null
    ): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val rootFolder = DocumentFile.fromTreeUri(context, treeUri) ?: run {
                Timber.e("Failed to get DocumentFile from tree URI: %s", treeUri)
                return@runCatching null
            }

            val targetFolder = if (subPath != null) {
                val parts = subPath.split("/").filter { it.isNotEmpty() }
                var currentFolder = rootFolder
                parts.forEach { part ->
                    currentFolder = currentFolder.findFile(part) ?: currentFolder.createDirectory(part) ?: run {
                        Timber.e("Failed to find or create subdirectory: %s", part)
                        return@runCatching null
                    }
                }
                currentFolder
            } else {
                rootFolder
            }

            val outFile = targetFolder.createFile(mimeType ?: "application/octet-stream", displayName) ?: run {
                Timber.e("Failed to create file in custom folder")
                return@runCatching null
            }

            context.contentResolver.openOutputStream(outFile.uri)?.use { output ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            } ?: run {
                Timber.e("Failed to open output stream for custom folder file: %s", outFile.uri)
                return@runCatching null
            }

            outFile.uri
        }.onFailure { e ->
            Timber.e(e, "Failed to save to custom folder: %s", displayName)
        }.getOrNull()
    }

    /**
     * Creates a temporary file in a specific 'shared' subdirectory of the cache.
     * This subdirectory is the only one exposed via FileProvider.
     */
    fun createSharedTempFile(prefix: String, suffix: String?): File {
        val sharedDir = File(context.cacheDir, "shared")
        if (!sharedDir.exists()) {
            sharedDir.mkdirs()
        }
        return File.createTempFile(prefix, suffix, sharedDir)
    }

    fun createCacheFile(prefix: String, suffix: String?): File = createSharedTempFile(prefix, suffix)

    /**
     * Clears all temporary files in the 'shared' cache directory.
     */
    fun clearCache() {
        val sharedDir = File(context.cacheDir, "shared")
        if (sharedDir.exists()) {
            sharedDir.listFiles()?.forEach { 
                runCatching { it.deleteRecursively() }
                    .onFailure { e -> Timber.w(e, "Failed to delete file from shared cache: %s", it.name) }
            }
        }
        // Also clear root cache directory for any stray files like processing_plans
        context.cacheDir.listFiles()?.forEach { 
            if (it.isFile) {
                runCatching { it.delete() }
                    .onFailure { e -> Timber.w(e, "Failed to delete file from root cache: %s", it.name) }
            }
        }
    }
}
