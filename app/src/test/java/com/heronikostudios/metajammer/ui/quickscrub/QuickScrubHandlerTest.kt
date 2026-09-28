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

        assertNotNull("Prepared share files should not be null", readyFiles)
        assertEquals(1, readyFiles?.size)
        assertEquals("image/jpeg", readyMime)
        assertTrue(readyFiles!![0].exists())

        // Verify EXIF metadata was stripped
        val cleanedExif = ExifInterface(readyFiles!![0].absolutePath)
        assertNull("Camera Make should be stripped in quick scrub", cleanedExif.getAttribute(ExifInterface.TAG_MAKE))
    }
}
