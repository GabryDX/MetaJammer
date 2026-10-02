package com.heronikostudios.metajammer.ui.quickscrub

import android.content.Context
import android.graphics.Bitmap
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import androidx.work.WorkManager
import com.heronikostudios.metajammer.data.FileRepository
import com.heronikostudios.metajammer.data.MetadataRepository
import com.heronikostudios.metajammer.data.SettingsRepository
import com.heronikostudios.metajammer.domain.model.*
import com.heronikostudios.metajammer.domain.usecase.ProcessFileUseCase
import com.heronikostudios.metajammer.domain.usecase.SaveFileUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class QuickScrubHandlerTest {

    private lateinit var context: Context
    private lateinit var fileRepository: FileRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var metadataRepository: MetadataRepository
    private lateinit var processFileUseCase: ProcessFileUseCase
    private lateinit var saveFileUseCase: SaveFileUseCase
    private lateinit var workManager: WorkManager
    private lateinit var handler: QuickScrubHandler

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        val unsafeField = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null) as sun.misc.Unsafe
        workManager = unsafe.allocateInstance(Class.forName("androidx.work.impl.WorkManagerImpl")) as WorkManager

        fileRepository = FileRepository(context)
        settingsRepository = SettingsRepository(context)
        metadataRepository = MetadataRepository(fileRepository)
        processFileUseCase = ProcessFileUseCase(metadataRepository)
        saveFileUseCase = SaveFileUseCase(fileRepository)

        handler = QuickScrubHandler(
            metadataRepository = metadataRepository,
            processFileUseCase = processFileUseCase,
            saveFileUseCase = saveFileUseCase,
            settingsRepository = settingsRepository,
            fileRepository = fileRepository,
            workManager = workManager,
            cacheDir = context.cacheDir
        )
    }

    private fun createTestImage(): File {
        val file = File(context.cacheDir, "qs_test_${System.nanoTime()}.jpg").apply {
            val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            val exif = ExifInterface(absolutePath)
            exif.setAttribute(ExifInterface.TAG_MAKE, "QuickScrubMake")
            exif.saveAttributes()
        }
        return file
    }

    @Test
    fun testExecuteQuickScrubWithEmptyFilesReportsNoFiles() = runTest {
        var statusMessage: String? = null
        var shareReadyCalled = false

        handler.executeQuickScrub(
            files = emptyList(),
            appSettings = AppSettings(),
            onShareFilesReady = { _, _ -> shareReadyCalled = true },
            onStatusMessage = { statusMessage = it }
        )

        assertEquals("No shared files received", statusMessage)
        assertFalse("onShareFilesReady must not be called when files are empty", shareReadyCalled)
    }

    @Test
    fun testExecuteQuickScrubWithSingleFileCleansAndPreparesShare() = runTest {
        val file = createTestImage()
        val selectedFile = SelectedFile(uri = file.toUri(), displayName = file.name, mimeType = "image/jpeg")

        var readyFiles: List<File>? = null
        var readyMime: String? = null
        var statusMessage: String? = null

        val settings = AppSettings(
            sharedFilesProcessingMode = ProcessingMode.REMOVE_METADATA,
            sharedFilesOutputAction = SharedInputOutputAction.SHARE_TO_ANOTHER_APP
        )

        handler.executeQuickScrub(
            files = listOf(selectedFile),
            appSettings = settings,
            onShareFilesReady = { files, mime ->
                readyFiles = files
                readyMime = mime
            },
            onStatusMessage = { statusMessage = it }
        )

        val files = readyFiles
        assertNotNull("Prepared share files should not be null", files)
        assertEquals(1, files!!.size)
        assertEquals("image/jpeg", readyMime)
        assertTrue(files[0].exists())

        // Verify EXIF metadata was stripped
        val cleanedExif = ExifInterface(files[0].absolutePath)
        assertNull("Camera Make should be stripped in quick scrub", cleanedExif.getAttribute(ExifInterface.TAG_MAKE))
    }

    @Test
    fun testExecuteQuickScrubCleansUpStaleOutgoingDirectories() = runTest {
        // Pre-create a stale outgoing folder with a leftover file
        val sharedDir = File(context.cacheDir, "shared")
        val staleDir = File(sharedDir, "outgoing_stale_12345")
        staleDir.mkdirs()
        val staleFile = File(staleDir, "leftover.jpg")
        staleFile.writeBytes(byteArrayOf(1, 2, 3))
        assertTrue("Stale directory must exist prior to quick scrub", staleDir.exists())

        val file = createTestImage()
        val selectedFile = SelectedFile(uri = file.toUri(), displayName = file.name, mimeType = "image/jpeg")
        val settings = AppSettings(
            sharedFilesProcessingMode = ProcessingMode.REMOVE_METADATA,
            sharedFilesOutputAction = SharedInputOutputAction.SHARE_TO_ANOTHER_APP
        )

        handler.executeQuickScrub(
            files = listOf(selectedFile),
            appSettings = settings,
            onShareFilesReady = { _, _ -> },
            onStatusMessage = { }
        )

        assertFalse("Stale outgoing directory must be cleaned up during preparation", staleDir.exists())
    }

    @Test
    fun testExecuteQuickScrubWithSaveToDefaultFolder() = runTest {
        val file = createTestImage()
        val selectedFile = SelectedFile(uri = file.toUri(), displayName = file.name, mimeType = "image/jpeg")

        var statusMessages = mutableListOf<String>()
        val settings = AppSettings(
            sharedFilesProcessingMode = ProcessingMode.REMOVE_METADATA,
            sharedFilesOutputAction = SharedInputOutputAction.SAVE_TO_DEFAULT_FOLDER
        )

        handler.executeQuickScrub(
            files = listOf(selectedFile),
            appSettings = settings,
            onShareFilesReady = { _, _ -> },
            onStatusMessage = { statusMessages.add(it) }
        )

        assertTrue("Status message should indicate saved to default folder", statusMessages.any { it.contains("Saved 1 file(s) to default folder") })
    }

    @Test
    fun testExecuteQuickScrubWithSaveToSharedFolderWithoutPathFailsGracefully() = runTest {
        val file = createTestImage()
        val selectedFile = SelectedFile(uri = file.toUri(), displayName = file.name, mimeType = "image/jpeg")

        var statusMessages = mutableListOf<String>()
        val settings = AppSettings(
            sharedFilesProcessingMode = ProcessingMode.REMOVE_METADATA,
            sharedFilesOutputAction = SharedInputOutputAction.SAVE_TO_SHARED_FOLDER,
            sharedFilesCustomPath = null
        )

        handler.executeQuickScrub(
            files = listOf(selectedFile),
            appSettings = settings,
            onShareFilesReady = { _, _ -> },
            onStatusMessage = { statusMessages.add(it) }
        )

        assertTrue(
            "Should gracefully report missing folder",
            statusMessages.any { it.contains("No shared-files folder configured") }
        )
    }

    @Test
    fun testExecuteQuickScrubInPoisonModeSpoofsMetadata() = runTest {
        val file = createTestImage()
        val selectedFile = SelectedFile(uri = file.toUri(), displayName = file.name, mimeType = "image/jpeg")

        var readyFiles: List<File>? = null
        val settings = AppSettings(
            sharedFilesProcessingMode = ProcessingMode.POISON_METADATA,
            sharedFilesOutputAction = SharedInputOutputAction.SHARE_TO_ANOTHER_APP,
            poisoningProfile = PoisoningProfile.PRO_MIRRORLESS
        )

        handler.executeQuickScrub(
            files = listOf(selectedFile),
            appSettings = settings,
            onShareFilesReady = { files, _ -> readyFiles = files },
            onStatusMessage = { }
        )

        assertNotNull("Prepared share files should not be null", readyFiles)
        val poisonedExif = ExifInterface(readyFiles!!.first().absolutePath)
        val make = poisonedExif.getAttribute(ExifInterface.TAG_MAKE)
        assertNotNull("Poisoned Make must be present", make)
        assertNotEquals("Real original make must not match poisoned make", "QuickScrubMake", make)
    }
}

