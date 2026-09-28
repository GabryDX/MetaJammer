package com.heronikostudios.metajammer.domain.model

enum class MetadataDiffStatus {
    REMOVED,
    POISONED,
    KEPT
}

data class MetadataDiffEntry(
    val key: String,
    val originalValue: String?,
    val newValue: String?,
    val status: MetadataDiffStatus
)
