package com.heronikostudios.metajammer.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.net.toUri
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
class FileRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var repository: FileRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        repository = FileRepository(context)
    }

    @After
    fun tearDown() {
        repository.clearCache()
    }

    @Test
    fun `getExtensionFromMime correctly resolves all supported file types`() {
        assertEquals(".jpg", repository.getExtensionFromMime("image/jpeg"))
        assertEquals(".jpg", repository.getExtensionFromMime("image/jpeg;charset=utf-8"))
        assertEquals(".png", repository.getExtensionFromMime("image/png"))
        assertEquals(".webp", repository.getExtensionFromMime("image/webp"))
        assertEquals(".heic", repository.getExtensionFromMime("image/heic"))
        assertEquals(".heic", repository.getExtensionFromMime("image/heif"))
        assertEquals(".mp4", repository.getExtensionFromMime("video/mp4"))
        assertEquals(".mov", repository.getExtensionFromMime("video/quicktime"))
        assertEquals(".mp3", repository.getExtensionFromMime("audio/mpeg"))
        assertEquals(".m4a", repository.getExtensionFromMime("audio/mp4"))
        assertEquals(".m4a", repository.getExtensionFromMime("audio/x-m4a"))
        assertEquals(".pdf", repository.getExtensionFromMime("application/pdf"))
        assertEquals(".bin", repository.getExtensionFromMime("application/octet-stream"))
        assertEquals(".bin", repository.getExtensionFromMime("unknown/mime"))
        assertEquals(".bin", repository.getExtensionFromMime(null))
    }

    @Test
    fun `createSharedTempFile creates file under cacheDir shared directory`() {
        val tempFile = repository.createSharedTempFile("test_prefix", ".tmp")
        assertTrue("Temp file must exist", tempFile.exists())
        assertEquals("Parent folder must be named 'shared'", "shared", tempFile.parentFile?.name)
        assertTrue(tempFile.name.startsWith("test_prefix"))
        assertTrue(tempFile.name.endsWith(".tmp"))
    }

    @Test
    fun `clearCache removes files in shared directory and root cache files`() {
        val sharedFile = repository.createSharedTempFile("clear_test", ".dat")
        sharedFile.writeText("shared data")
        assertTrue(sharedFile.exists())

        val rootCacheFile = File(context.cacheDir, "root_cache_file.tmp")
        rootCacheFile.writeText("root data")
        assertTrue(rootCacheFile.exists())

        repository.clearCache()

        assertFalse("Shared cache file must be deleted", sharedFile.exists())
        assertFalse("Root cache file must be deleted", rootCacheFile.exists())
    }

    @Test
    @Config(sdk = [28])
    fun `saveToDefaultFolder on Android 9 API 28 writes directly to public external storage and scans`() = runTest {
        val sourceFile = tempFolder.newFile("sample_image.png")
        sourceFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5))

        val resultUri = repository.saveToDefaultFolder(
            sourceFile = sourceFile,
            displayName = "saved_sample_api28.png",
            mimeType = "image/png",
            configuredPath = null
        )

        assertNotNull("Result URI must not be null on API 28", resultUri)

        // Verify the file was written to Pictures/MetaJammer
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val metaJammerDir = File(picturesDir, "MetaJammer")
        val outputFile = File(metaJammerDir, "saved_sample_api28.png")
        assertTrue("Output file must exist in Pictures/MetaJammer on API 28", outputFile.exists())
        assertEquals(5, outputFile.length())
    }

    @Test
    @Config(sdk = [28])
    fun `saveToDefaultFolder on Android 9 API 28 writes PDF to Downloads folder`() = runTest {
        val sourceFile = tempFolder.newFile("document.pdf")
        sourceFile.writeBytes("PDF content test".toByteArray())

        val resultUri = repository.saveToDefaultFolder(
            sourceFile = sourceFile,
            displayName = "test_doc_api28.pdf",
            mimeType = "application/pdf",
            configuredPath = null
        )

        assertNotNull("Result URI must not be null on API 28", resultUri)

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val metaJammerDir = File(downloadsDir, "MetaJammer")
        val outputFile = File(metaJammerDir, "test_doc_api28.pdf")
        assertTrue("PDF must be saved to Downloads/MetaJammer on API 28", outputFile.exists())
    }

    @Test
    @Config(sdk = [28])
    fun `saveToDefaultFolder on Android 9 API 28 writes video to Movies folder`() = runTest {
        val sourceFile = tempFolder.newFile("video.mp4")
        sourceFile.writeBytes(byteArrayOf(0, 0, 0, 0x18, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte()))

        val resultUri = repository.saveToDefaultFolder(
            sourceFile = sourceFile,
            displayName = "test_vid_api28.mp4",
            mimeType = "video/mp4",
            configuredPath = null
        )

        assertNotNull("Result URI must not be null on API 28", resultUri)

        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val metaJammerDir = File(moviesDir, "MetaJammer")
        val outputFile = File(metaJammerDir, "test_vid_api28.mp4")
        assertTrue("Video must be saved to Movies/MetaJammer on API 28", outputFile.exists())
    }

    @Test
    @Config(sdk = [28])
    fun `saveToDefaultFolder on API 28 handles non-existent source file gracefully without crashing`() = runTest {
        val nonExistent = File(context.cacheDir, "non_existent_file_${System.nanoTime()}.bin")
        assertFalse(nonExistent.exists())

        val resultUri = repository.saveToDefaultFolder(
            sourceFile = nonExistent,
            displayName = "non_existent.bin",
            mimeType = "application/octet-stream",
            configuredPath = null
        )

        assertNull("Must return null when source file cannot be read, instead of throwing", resultUri)
    }

    @Test
    @Config(sdk = [33])
    fun `saveToDefaultFolder on Android 13 API 33 inserts into MediaStore`() = runTest {
        val sourceFile = tempFolder.newFile("test_api33.jpg")
        sourceFile.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))

        val resultUri = repository.saveToDefaultFolder(
            sourceFile = sourceFile,
            displayName = "test_api33.jpg",
            mimeType = "image/jpeg",
            configuredPath = "Pictures/MetaJammer"
        )

        assertNotNull("Result URI must not be null on API 33", resultUri)
        assertTrue("Result URI should be content:// scheme", resultUri.toString().startsWith("content://"))
    }

    @Test
    fun `saveToDefaultFolder routes content scheme to saveToCustomFolder`() = runTest {
        val sourceFile = tempFolder.newFile("test_custom.jpg")
        sourceFile.writeBytes(byteArrayOf(1, 2, 3))

        // Providing an invalid or unresolvable content URI should return null safely without throwing
        val invalidTreeUri = "content://com.android.externalstorage.documents/tree/primary%3AInvalidFolder"
        val resultUri = repository.saveToDefaultFolder(
            sourceFile = sourceFile,
            displayName = "test_custom.jpg",
            mimeType = "image/jpeg",
            configuredPath = invalidTreeUri
        )

        assertNull("Invalid tree URI must safely return null without throwing", resultUri)
    }

    @Test
    fun `saveToCustomFolder returns null gracefully when treeUri cannot be resolved`() = runTest {
        val sourceFile = tempFolder.newFile("source.jpg")
        sourceFile.writeText("hello")

        val dummyUri = "content://dummy.provider/invalid/tree".toUri()
        val result = repository.saveToCustomFolder(
            treeUri = dummyUri,
            sourceFile = sourceFile,
            displayName = "out.jpg",
            mimeType = "image/jpeg"
        )

        assertNull("Non-existent tree URI must return null without crashing", result)
    }

    @Test
    fun `copyUriToCache copies file contents to temporary cache file`() {
        val originalFile = tempFolder.newFile("origin.txt")
        originalFile.writeText("MetaJammer File Copy Test")

        val copiedFile = repository.copyUriToCache(originalFile.toUri(), "test_copy", ".txt")

        assertTrue("Copied file must exist", copiedFile.exists())
        assertEquals("MetaJammer File Copy Test", copiedFile.readText())
    }
}
