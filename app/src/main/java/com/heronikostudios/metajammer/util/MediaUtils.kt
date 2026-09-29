package com.heronikostudios.metajammer.util

import android.media.MediaMetadataRetriever

/**
 * Executes the given [block] on this [MediaMetadataRetriever] and safely releases native resources.
 *
 * MediaMetadataRetriever only implements AutoCloseable on API 29+ (Android 10).
 * Calling Kotlin's stdlib `.use { ... }` causes NoSuchMethodError / IncompatibleClassChangeError
 * at runtime on API 26-28 devices. This helper ensures backward compatibility across all supported versions.
 */
inline fun <R> MediaMetadataRetriever.useCompat(block: (MediaMetadataRetriever) -> R): R {
    try {
        return block(this)
    } finally {
        runCatching { release() }
    }
}
