package com.heronikostudios.metajammer.ui.processing

import android.content.Context
import android.graphics.Bitmap
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import androidx.work.Configuration
import androidx.work.WorkManager
import com.heronikostudios.metajammer.data.FileRepository
import com.heronikostudios.metajammer.data.MetadataRepository
import com.heronikostudios.metajammer.data.SettingsRepository
import com.heronikostudios.metajammer.domain.model.*
import com.heronikostudios.metajammer.domain.usecase.ProcessFileUseCase
import com.heronikostudios.metajammer.domain.usecase.SaveFileUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProcessingViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var fileRepository: FileRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var metadataRepository: MetadataRepository
    private lateinit var processFileUseCase: ProcessFileUseCase
    private lateinit var saveFileUseCase: SaveFileUseCase
    private lateinit var workManager: WorkManager
    private lateinit var viewModel: ProcessingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
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

        viewModel = ProcessingViewModel(
            metadataRepository = metadataRepository,
            fileRepository = fileRepository,
            settingsRepository = settingsRepository,
            processFileUseCase = processFileUseCase,
            saveFileUseCase = saveFileUseCase,
            workManager = workManager,
            cacheDir = context.cacheDir
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createTestImage(): File {
        val file = File(context.cacheDir, "test_preview_${System.nanoTime()}.jpg").apply {
            val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            val exif = ExifInterface(absolutePath)
            exif.setAttribute(ExifInterface.TAG_MAKE, "TestCameraMake")
            exif.setLatLong(35.6895, 139.6917)
            exif.saveAttributes()
        }
        return file
    }

    @Test
    fun testGenerateChangePreviewInRemoveModeWithGranularSettings() = runTest {
        val file = createTestImage()
        val selectedFile = SelectedFile(uri = file.toUri(), displayName = file.name, mimeType = "image/jpeg")

        viewModel.loadMetadataPreview(listOf(selectedFile))
        var attempts = 0
        while (viewModel.metadataPreview.value[selectedFile.uri].isNullOrEmpty() && attempts++ < 100) {
            Thread.sleep(20)
        }
        assertFalse("Metadata preview should be loaded", viewModel.metadataPreview.value[selectedFile.uri].isNullOrEmpty())

        // Strip GPS, but keep Device Model
        val settings = AppSettings(stripGps = true, stripDeviceModel = false)
        viewModel.setProcessingMode(ProcessingMode.REMOVE_METADATA, listOf(selectedFile), settings)

        attempts = 0
        while (viewModel.changePreview.value[selectedFile.uri].isNullOrEmpty() && attempts++ < 100) {
            Thread.sleep(20)
        }

        val diffEntries = viewModel.changePreview.value[selectedFile.uri].orEmpty()
        assertFalse("Diff preview should not be empty", diffEntries.isEmpty())

        val gpsEntry = diffEntries.find { it.key == "GPSLatitude" }
        assertNotNull("GPS entry should be present in diff", gpsEntry)
        assertEquals("GPS tag should be REMOVED", MetadataDiffStatus.REMOVED, gpsEntry?.status)

        val makeEntry = diffEntries.find { it.key == "Make" }
        assertNotNull("Make entry should be present in diff", makeEntry)
        assertEquals("Make tag should be KEPT when stripDeviceModel is false", MetadataDiffStatus.KEPT, makeEntry?.status)
    }

    @Test
    fun testGenerateChangePreviewInPoisonModeProducesDiff() = runTest {
        val file = createTestImage()
        val selectedFile = SelectedFile(uri = file.toUri(), displayName = file.name, mimeType = "image/jpeg")

        viewModel.loadMetadataPreview(listOf(selectedFile))
        var attempts = 0
        while (viewModel.metadataPreview.value[selectedFile.uri].isNullOrEmpty() && attempts++ < 100) {
            Thread.sleep(20)
        }

        val settings = AppSettings(poisoningProfile = PoisoningProfile.PRO_MIRRORLESS)
        viewModel.setProcessingMode(ProcessingMode.POISON_METADATA, listOf(selectedFile), settings)

        attempts = 0
        while (viewModel.changePreview.value[selectedFile.uri].isNullOrEmpty() && attempts++ < 100) {
            Thread.sleep(20)
        }

        val diffEntries = viewModel.changePreview.value[selectedFile.uri].orEmpty()
        assertFalse("Diff preview should not be empty in poison mode", diffEntries.isEmpty())

        val poisonedCount = diffEntries.count { it.status == MetadataDiffStatus.POISONED }
        assertTrue("Should have multiple POISONED entries", poisonedCount > 0)

        val makeEntry = diffEntries.find { it.key == "Make" }
        assertNotNull(makeEntry)
        assertEquals(MetadataDiffStatus.POISONED, makeEntry?.status)
    }

    @Test
    fun testClearAllMetadataAndPlansResetsState() {
        val file = createTestImage()
        val selectedFile = SelectedFile(uri = file.toUri(), displayName = "test_preview.jpg", mimeType = "image/jpeg")

        viewModel.loadMetadataPreview(listOf(selectedFile))
        viewModel.setProcessingMode(ProcessingMode.REMOVE_METADATA, listOf(selectedFile), AppSettings())

        viewModel.clearAllMetadataAndPlans()

        assertTrue(viewModel.metadataPreview.value.isEmpty())
        assertTrue(viewModel.changePreview.value.isEmpty())
        assertTrue(viewModel.replacementPlans.value.isEmpty())
        assertNull(viewModel.selectedMode.value)
    }
}
