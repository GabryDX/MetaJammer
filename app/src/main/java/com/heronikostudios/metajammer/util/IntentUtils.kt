package com.heronikostudios.metajammer.util

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

object IntentUtils {
    /**
     * Extracts content URIs from ACTION_SEND or ACTION_SEND_MULTIPLE incoming intents.
     */
    fun extractSharedUris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()

        val action = intent.action
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) {
            return emptyList()
        }

        val uris = when (action) {
            Intent.ACTION_SEND -> {
                val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                uri?.let(::listOf) ?: emptyList()
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: emptyList()
            }

            else -> emptyList()
        }

        return uris.filter { uri ->
            uri.scheme == "content"
        }
    }
}
