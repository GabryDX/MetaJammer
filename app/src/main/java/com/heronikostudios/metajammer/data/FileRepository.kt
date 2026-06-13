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
        } ?: error("Unable to open input stream for $uri")
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
        configuredPath: String?
    ): Uri? {
        val path = configuredPath ?: "Pictures/MetaJammer"

        return if (path.startsWith("content://")) {
            saveToCustomFolder(
                treeUri = path.toUri(),
                sourceFile = sourceFile,
                displayName = displayName,
                mimeType = mimeType
            )
        } else {
            saveToMediaStorePath(
                sourceFile = sourceFile,
                displayName = displayName,
                mimeType = mimeType,
                relativePath = path
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

        val uri = resolver.insert(collection, values) ?: return null

        resolver.openOutputStream(uri)?.use { output ->
            sourceFile.inputStream().use { input ->
                input.copyTo(output)
            }
        } ?: return null

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
        mimeType: String?
    ): Uri? {
        val folder = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        val outFile = folder.createFile(mimeType ?: "application/octet-stream", displayName) ?: return null

        context.contentResolver.openOutputStream(outFile.uri)?.use { output ->
            sourceFile.inputStream().use { input ->
                input.copyTo(output)
            }
        } ?: return null

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

    /**
     * Clears all temporary files in the 'shared' cache directory.
     */
    fun clearCache() {
        val sharedDir = File(context.cacheDir, "shared")
        if (sharedDir.exists()) {
            sharedDir.listFiles()?.forEach { it.delete() }
        }
    }

    /**
     * Sanitizes a filename to prevent path traversal and remove illegal characters.
     */
    @Deprecated("Use SanitizationUtils.sanitizeFileName", replaceWith = ReplaceWith("SanitizationUtils.sanitizeFileName(fileName)"))
    private fun sanitizeFileName(fileName: String?): String {
        return SanitizationUtils.sanitizeFileName(fileName)
    }
}
