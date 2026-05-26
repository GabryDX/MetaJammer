package com.heronikostudios.metajammer.domain.model

import android.net.Uri

data class SelectedFile(
    val uri: Uri,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?
)
