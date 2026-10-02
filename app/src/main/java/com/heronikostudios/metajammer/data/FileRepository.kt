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
        var mimeType = resolver.getType(uri)

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

        if (mimeType.isNullOrBlank() || mimeType == "application/octet-stream" || mimeType == "binary/octet-stream") {
            val nameExt = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            if (nameExt.isNotEmpty()) {
                mimeType = when (nameExt) {
                    "jpg", "jpeg" -> "image/jpeg"
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    "heic", "heif" -> "image/heic"
                    "mp4" -> "video/mp4"
                    "mov" -> "video/quicktime"
                    "m4a" -> "audio/mp4"
                    "mp3" -> "audio/mpeg"
                    "ogg" -> "audio/ogg"
                    "pdf" -> "application/pdf"
                    "svg" -> "image/svg+xml"
                    else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(nameExt)
                }
            }

            if (mimeType.isNullOrBlank() || mimeType == "application/octet-stream") {
                val extension = getExtension(uri).removePrefix(".").lowercase(Locale.ROOT)
                if (extension.isNotEmpty()) {
                    mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                }
            }

            if (mimeType.isNullOrBlank() || mimeType == "application/octet-stream") {
                detectMimeTypeFromMagic(uri)?.let {
                    mimeType = it
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

    private fun detectMimeTypeFromMagic(uri: Uri): String? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(64)
                val read = stream.read(buffer)
                if (read < 4) return@use null

                // JPEG: FF D8 FF
                if ((buffer[0].toInt() and 0xFF) == 0xFF &&
                    (buffer[1].toInt() and 0xFF) == 0xD8 &&
                    (buffer[2].toInt() and 0xFF) == 0xFF
                ) {
                    return@use "image/jpeg"
                }

                // PNG: 89 50 4E 47 0D 0A 1A 0A
                if (read >= 8 &&
                    (buffer[0].toInt() and 0xFF) == 0x89 &&
                    buffer[1] == 'P'.code.toByte() &&
                    buffer[2] == 'N'.code.toByte() &&
                    buffer[3] == 'G'.code.toByte()
                ) {
                    return@use "image/png"
                }

                // PDF: %PDF-
                if (buffer[0] == '%'.code.toByte() &&
                    buffer[1] == 'P'.code.toByte() &&
                    buffer[2] == 'D'.code.toByte() &&
                    buffer[3] == 'F'.code.toByte()
                ) {
                    return@use "application/pdf"
                }

                // WebP: RIFF....WEBP
                if (read >= 12 &&
                    buffer[0] == 'R'.code.toByte() &&
                    buffer[1] == 'I'.code.toByte() &&
                    buffer[2] == 'F'.code.toByte() &&
                    buffer[3] == 'F'.code.toByte() &&
                    buffer[8] == 'W'.code.toByte() &&
                    buffer[9] == 'E'.code.toByte() &&
                    buffer[10] == 'B'.code.toByte() &&
                    buffer[11] == 'P'.code.toByte()
                ) {
                    return@use "image/webp"
                }

                // MP4 / MOV: bytes 4..7 == 'ftyp'
                if (read >= 8 &&
                    buffer[4] == 'f'.code.toByte() &&
                    buffer[5] == 't'.code.toByte() &&
                    buffer[6] == 'y'.code.toByte() &&
                    buffer[7] == 'p'.code.toByte()
                ) {
                    return@use "video/mp4"
                }

                // SVG: starts with <?xml or <svg
                val prefix = String(buffer, 0, minOf(read, 32), java.nio.charset.StandardCharsets.US_ASCII).trim()
                if (prefix.startsWith("<?xml") || prefix.startsWith("<svg") || prefix.contains("<svg")) {
                    return@use "image/svg+xml"
                }

                // MP3: ID3 or sync word
                if (buffer[0] == 'I'.code.toByte() && buffer[1] == 'D'.code.toByte() && buffer[2] == '3'.code.toByte()) {
                    return@use "audio/mpeg"
                }

                null
            }
        }.getOrNull()
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
        var extension: String? = null
        if (!mimeType.isNullOrBlank() && mimeType != "application/octet-stream") {
            extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        }

        if (extension == null) {
            val path = uri.path
            if (path != null) {
                val lastDot = path.lastIndexOf('.')
                if (lastDot != -1) {
                    extension = path.substring(lastDot + 1).lowercase(Locale.ROOT)
                }
            }
        }

        if (extension == null) {
            runCatching {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            val displayName = cursor.getString(nameIndex)
                            val lastDot = displayName.lastIndexOf('.')
                            if (lastDot != -1) {
                                extension = displayName.substring(lastDot + 1).lowercase(Locale.ROOT)
                            }
                        }
                    }
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
            mimeType?.startsWith("image/svg+xml") == true -> ".svg"
            mimeType?.startsWith("video/mp4") == true -> ".mp4"
            mimeType?.startsWith("video/quicktime") == true -> ".mov"
            mimeType?.startsWith("audio/mpeg") == true -> ".mp3"
            mimeType?.startsWith("audio/mp4") == true -> ".m4a"
            mimeType?.startsWith("audio/x-m4a") == true -> ".m4a"
            mimeType?.startsWith("audio/ogg") == true -> ".ogg"
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
            val normalizedRelativePath = if (relativePath.endsWith("/")) relativePath else "$relativePath/"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val collection = when {
                    mimeType?.startsWith("image/") == true && (normalizedRelativePath.startsWith("Pictures/") || normalizedRelativePath.startsWith("DCIM/")) ->
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    mimeType?.startsWith("video/") == true && (normalizedRelativePath.startsWith("Movies/") || normalizedRelativePath.startsWith("DCIM/")) ->
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    mimeType?.startsWith("audio/") == true && (normalizedRelativePath.startsWith("Music/") || normalizedRelativePath.startsWith("Alarms/") || normalizedRelativePath.startsWith("Podcasts/") || normalizedRelativePath.startsWith("Ringtones/") || normalizedRelativePath.startsWith("Audiobooks/")) ->
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    normalizedRelativePath.startsWith("Download/") ->
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI
                    normalizedRelativePath.startsWith("Documents/") ->
                        MediaStore.Files.getContentUri("external")
                    else ->
                        MediaStore.Files.getContentUri("external")
                }

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType ?: "application/octet-stream")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, normalizedRelativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                var uri = runCatching { resolver.insert(collection, values) }.getOrNull()

                // Fallback: If insertion into Documents or MediaStore.Files failed, try Downloads collection as safe fallback
                if (uri == null && collection != MediaStore.Downloads.EXTERNAL_CONTENT_URI) {
                    val fallbackValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeType ?: "application/octet-stream")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/MetaJammer/")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    uri = runCatching { resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, fallbackValues) }.getOrNull()
                }

                if (uri == null) {
                    Timber.e("Failed to insert media into MediaStore for %s (collection: %s, path: %s)", displayName, collection, normalizedRelativePath)
                    return null
                }

                resolver.openOutputStream(uri)?.use { output ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                    }
                } ?: run {
                    Timber.e("Failed to open output stream for MediaStore URI: %s", uri)
                    resolver.delete(uri, null, null)
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
                    mimeType?.startsWith("audio/") == true ->
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                    normalizedRelativePath.startsWith("Documents/") ->
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                    else ->
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                }
                val subFolder = if (normalizedRelativePath.trimEnd('/').contains("/")) {
                    normalizedRelativePath.trimEnd('/').substringAfter("/")
                } else {
                    "MetaJammer"
                }
                val destDir = File(targetDir, subFolder)
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
