package com.heronikostudios.metajammer.domain.usecase

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

class ShareFileUseCase {

    fun shareUri(
        context: Context,
        uri: Uri,
        mimeType: String?
    ) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "Share processed file").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    }

    fun shareFile(
        context: Context,
        file: File,
        mimeType: String?
    ) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        shareUri(context, uri, mimeType)
    }

    fun shareFiles(
        context: Context,
        files: List<File>,
        mimeType: String?
    ) {
        if (files.isEmpty()) return
        
        if (files.size == 1) {
            shareFile(context, files[0], mimeType)
            return
        }

        val uris = files.map { file ->
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mimeType ?: "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            
            // Add ClipData for permission delegation on modern Android/GrapheneOS
            if (uris.isNotEmpty()) {
                val clipData = ClipData.newRawUri(null, uris.first())
                for (i in 1 until uris.size) {
                    clipData.addItem(ClipData.Item(uris[i]))
                }
                this.clipData = clipData
            }
            
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "Share ${files.size} processed files").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    }
}
