package com.heronikostudios.metajammer.domain.usecase

import android.content.Context
import android.content.Intent
import android.net.Uri

class ShareFileUseCase {

    operator fun invoke(
        context: Context,
        uri: Uri,
        mimeType: String?
    ) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Share processed file"))
    }
}
