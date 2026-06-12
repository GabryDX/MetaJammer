package com.heronikostudios.metajammer.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.heronikostudios.metajammer.domain.model.SelectedFile
import java.io.File

class FileRepository(private val context: Context) {

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
                    name = sanitizeFileName(cursor.getString(nameIndex))
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
        val tempFile = createSharedTempFile(prefix, suffix)
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: error("Unable to open input stream for $uri")
        return tempFile
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
                treeUri = Uri.parse(path),
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
     * Sanitizes a filename to prevent path traversal and remove illegal characters.
     */
    private fun sanitizeFileName(fileName: String?): String {
        if (fileName.isNullOrBlank()) return "unknown_${System.currentTimeMillis()}"
        
        // 1. Strip path components by getting only the 'name' part
        val nameOnly = File(fileName).name
        
        // 2. Replace common illegal characters with underscores
        val sanitized = nameOnly.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        
        // 3. Ensure it's not empty and doesn't start with a dot (hidden file)
        return if (sanitized.isBlank() || sanitized.startsWith(".")) {
            "file_${System.currentTimeMillis()}_$sanitized"
        } else {
            sanitized
        }
    }
}
