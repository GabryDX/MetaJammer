package com.heronikostudios.metajammer.ui.history

import android.content.Context
import androidx.room.Room
import com.heronikostudios.metajammer.data.HistoryRepository
import com.heronikostudios.metajammer.data.db.AppDatabase
import com.heronikostudios.metajammer.domain.model.ProcessedFileLog
import com.heronikostudios.metajammer.domain.usecase.ShareFileUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HistoryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: HistoryRepository
    private lateinit var viewModel: HistoryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = HistoryRepository(processedFileDao = database.processedFileDao(), context = null)
        viewModel = HistoryViewModel(historyRepository = repository, shareFileUseCase = ShareFileUseCase())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun testClearProcessedFilesHistoryClearsRepositoryAndSetsNotice() = runTest {
        repository.logProcessedFile(
            ProcessedFileLog(
                uri = "content://file1",
                displayName = "photo.jpg",
                mimeType = "image/jpeg"
            )
        )
        assertFalse(repository.processedFilesFlow.first().isEmpty())

        viewModel.clearProcessedFilesHistory("History Cleared Successfully")

        // Flush Robolectric main thread looper
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        val remaining = repository.processedFilesFlow.first()
        assertTrue("History in database must be empty", remaining.isEmpty())
        assertEquals("History Cleared Successfully", viewModel.message.value)

        viewModel.clearMessage()
        assertNull("Message must be cleared after clearMessage", viewModel.message.value)
    }
}
