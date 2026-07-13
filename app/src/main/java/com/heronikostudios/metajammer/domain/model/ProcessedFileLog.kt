package com.heronikostudios.metajammer.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ProcessedFileLog(
    val uri: String,
    val displayName: String,
    val mimeType: String?,
    val timestamp: Long,
    val sizeBytes: Long?
)
