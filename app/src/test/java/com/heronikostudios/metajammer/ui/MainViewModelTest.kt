package com.heronikostudios.metajammer.ui

import android.app.Application
import android.content.Context
import androidx.core.net.toUri
import androidx.work.WorkManager
import com.heronikostudios.metajammer.data.FileRepository
import com.heronikostudios.metajammer.data.HistoryRepository
import com.heronikostudios.metajammer.data.MetadataRepository
import com.heronikostudios.metajammer.data.SettingsRepository
import com.heronikostudios.metajammer.domain.model.SelectedFile
import com.heronikostudios.metajammer.domain.usecase.ProcessFileUseCase
import com.heronikostudios.metajammer.domain.usecase.SaveFileUseCase
import com.heronikostudios.metajammer.ui.history.HistoryViewModel
import com.heronikostudios.metajammer.ui.home.HomeViewModel
import com.heronikostudios.metajammer.ui.processing.ProcessingViewModel
import com.heronikostudios.metajammer.ui.quickscrub.QuickScrubHandler
import com.heronikostudios.metajammer.ui.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MainViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var app: Application
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        app = RuntimeEnvironment.getApplication()

        val unsafeField = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null) as sun.misc.Unsafe
        val workManager = unsafe.allocateInstance(Class.forName("androidx.work.impl.WorkManagerImpl")) as WorkManager

        val fileRepo = FileRepository(app)
        val metaRepo = MetadataRepository(fileRepo)
        val setRepo = SettingsRepository(app)
        val histRepo = HistoryRepository(app)
        val processUseCase = ProcessFileUseCase(metaRepo)
        val saveUseCase = SaveFileUseCase(fileRepo)

        viewModel = MainViewModel(
            application = app,
            fileRepository = fileRepo,
            metadataRepository = metaRepo,
            settingsRepository = setRepo,
            historyRepository = histRepo,
            workManager = workManager,
            processFileUseCase = processUseCase,
            saveFileUseCase = saveUseCase,
            homeViewModel = HomeViewModel(fileRepo),
            settingsViewModel = SettingsViewModel(setRepo, fileRepo, app.contentResolver),
            processingViewModel = ProcessingViewModel(
                metadataRepository = metaRepo,
                fileRepository = fileRepo,
                settingsRepository = setRepo,
                processFileUseCase = processUseCase,
                saveFileUseCase = saveUseCase,
                workManager = workManager,
                cacheDir = app.cacheDir
            ),
            historyViewModel = HistoryViewModel(histRepo),
            quickScrubHandler = QuickScrubHandler(
                metadataRepository = metaRepo,
                processFileUseCase = processUseCase,
                saveFileUseCase = saveUseCase,
                settingsRepository = setRepo,
                fileRepository = fileRepo,
                workManager = workManager,
                cacheDir = app.cacheDir
            )
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testNavigateBackToHomeFromOutput_whenNotSavedNorShared_preservesSelectedFiles() {
        val testFiles = listOf(
            SelectedFile(uri = "content://media/1".toUri(), displayName = "image1.jpg", mimeType = "image/jpeg"),
            SelectedFile(uri = "content://media/2".toUri(), displayName = "image2.jpg", mimeType = "image/jpeg")
        )
        viewModel.homeViewModel.setFiles(testFiles)
        assertEquals(2, viewModel.selectedFiles.value.size)

        // hasSavedOrShared starts false
        assertFalse(viewModel.hasSavedOrShared.value)

        var navigated = false
        viewModel.navigateBackToHomeFromOutput {
            navigated = true
        }

        assertTrue("Navigation callback must be invoked", navigated)
        assertEquals("Selected files must be kept on Home if not saved nor shared", 2, viewModel.selectedFiles.value.size)
        assertNull("Message must be cleared", viewModel.message.value)
    }

    @Test
    fun testNavigateBackToHomeFromOutput_whenSavedOrShared_clearsSelection() {
        val testFiles = listOf(
            SelectedFile(uri = "content://media/1".toUri(), displayName = "image1.jpg", mimeType = "image/jpeg")
        )
        viewModel.homeViewModel.setFiles(testFiles)
        assertEquals(1, viewModel.selectedFiles.value.size)

        // Mark as saved/shared
        viewModel.processingViewModel.setHasSavedOrShared(true)
        assertTrue(viewModel.hasSavedOrShared.value)

        var navigated = false
        viewModel.navigateBackToHomeFromOutput {
            navigated = true
        }

        assertTrue("Navigation callback must be invoked", navigated)
        assertTrue("Selected files must be cleared after being saved or shared", viewModel.selectedFiles.value.isEmpty())
        assertNull("Message must be cleared", viewModel.message.value)
    }
}
