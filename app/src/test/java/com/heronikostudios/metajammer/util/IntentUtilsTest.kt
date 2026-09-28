package com.heronikostudios.metajammer.util

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class IntentUtilsTest {

    @Test
    fun testNullIntentReturnsEmptyList() {
        val result = IntentUtils.extractSharedUris(null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun testUnrelatedActionReturnsEmptyList() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("content://media/external/images/media/1")
        }
        val result = IntentUtils.extractSharedUris(intent)
        assertTrue(result.isEmpty())
    }

    @Test
    fun testActionSendWithContentUriReturnsSingleUri() {
        val uri = Uri.parse("content://media/external/images/media/42")
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
        }
        val result = IntentUtils.extractSharedUris(intent)
        assertEquals(1, result.size)
        assertEquals(uri, result[0])
    }

    @Test
    fun testActionSendFiltersOutNonContentUris() {
        val fileUri = Uri.parse("file:///data/data/com.example/secret.txt")
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, fileUri)
        }
        val result = IntentUtils.extractSharedUris(intent)
        assertTrue("Non-content URIs must be excluded for security", result.isEmpty())
    }

    @Test
    fun testActionSendMultipleWithContentUrisReturnsAll() {
        val uri1 = Uri.parse("content://media/external/images/media/1")
        val uri2 = Uri.parse("content://media/external/images/media/2")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(uri1, uri2))
        }
        val result = IntentUtils.extractSharedUris(intent)
        assertEquals(2, result.size)
        assertEquals(uri1, result[0])
        assertEquals(uri2, result[1])
    }

    @Test
    fun testActionSendMultipleFiltersNonContentUris() {
        val contentUri = Uri.parse("content://media/external/images/media/100")
        val fileUri = Uri.parse("file:///etc/hosts")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(contentUri, fileUri))
        }
        val result = IntentUtils.extractSharedUris(intent)
        assertEquals(1, result.size)
        assertEquals(contentUri, result[0])
    }
}
