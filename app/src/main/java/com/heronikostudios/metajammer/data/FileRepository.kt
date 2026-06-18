package com.heronikostudios.metajammer.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
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

class FileRepository(private val context: Context) {

    fun getContext(): Context = context

    fun getSelectedFile(uri: Uri): SelectedFile {
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

    fun copyUriToCache(uri: Uri, prefix: String = "input_", suffix: String? = null): File {
        val resolvedSuffix = suffix ?: getExtension(uri)
        val tempFile = createSharedTempFile(prefix, resolvedSuffix)
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: run {
            Timber.e("Unable to open input stream for %s", uri)
            error("Unable to open input stream for $uri")
        }
        return tempFile
    }

    fun getExtension(uri: Uri): String {
        val mimeType = context.contentResolver.getType(uri)
        var extension: String? = null

        if (mimeType != null) {
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

        return if (extension != null) ".$extension" else ""
    }

    fun saveToDefaultFolder(
        sourceFile: File,
        displayName: String,
        mimeType: String?,
        configuredPath: String?,
        subPath: String? = null
    ): Uri? {
        val path = configuredPath ?: "Download/MetaJammer"
        val finalRelativePath = if (subPath != null) {
            if (path.endsWith("/")) "$path$subPath" else "$path/$subPath"
        } else {
            path
        }

        return if (path.startsWith("content://")) {
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
        val resolver = context.contentResolver

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
                input.copyTo(output)
            }
        } ?: run {
            Timber.e("Failed to open output stream for MediaStore URI: %s", uri)
            return null
        }

        val completedValues = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }
        resolver.update(uri, completedValues, null, null)

        return uri
    }

    fun saveToCustomFolder(
        treeUri: Uri,
        sourceFile: File,
        displayName: String,
        mimeType: String?,
        subPath: String? = null
    ): Uri? {
        val rootFolder = DocumentFile.fromTreeUri(context, treeUri) ?: run {
            Timber.e("Failed to get DocumentFile from tree URI: %s", treeUri)
            return null
        }

        val targetFolder = if (subPath != null) {
            val parts = subPath.split("/").filter { it.isNotEmpty() }
            var currentFolder = rootFolder
            parts.forEach { part ->
                currentFolder = currentFolder.findFile(part) ?: currentFolder.createDirectory(part) ?: run {
                    Timber.e("Failed to find or create subdirectory: %s", part)
                    return null
                }
            }
            currentFolder
        } else {
            rootFolder
        }

        val outFile = targetFolder.createFile(mimeType ?: "application/octet-stream", displayName) ?: run {
            Timber.e("Failed to create file in custom folder")
            return null
        }

        context.contentResolver.openOutputStream(outFile.uri)?.use { output ->
            sourceFile.inputStream().use { input ->
                input.copyTo(output)
            }
        } ?: run {
            Timber.e("Failed to open output stream for custom folder file: %s", outFile.uri)
            return null
        }

        return outFile.uri
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
            sharedDir.listFiles()?.forEach { it.delete() }
        }
        // Also clear root cache directory for any stray files like processing_plans
        context.cacheDir.listFiles()?.forEach { 
            if (it.isFile) it.delete()
        }
    }

}
