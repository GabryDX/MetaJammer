package com.heronikostudios.metajammer.data

import android.content.Context
import androidx.room.Room
import com.heronikostudios.metajammer.data.db.AppDatabase
import com.heronikostudios.metajammer.data.db.ProcessedFileDao
import com.heronikostudios.metajammer.domain.model.HistoryRetentionPolicy
import com.heronikostudios.metajammer.domain.model.ProcessedFileLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class HistoryRepositoryTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var dao: ProcessedFileDao
    private lateinit var repository: HistoryRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.processedFileDao()
        repository = HistoryRepository(processedFileDao = dao, context = null)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testLogAndRetrieveProcessedFiles() = runTest {
        val now = System.currentTimeMillis()
        val log1 = ProcessedFileLog(
            uri = "content://media/external/images/1",
            displayName = "photo1.jpg",
            mimeType = "image/jpeg",
            timestamp = now - 1000,
            sizeBytes = 1024L
        )
        val log2 = ProcessedFileLog(
            uri = "content://media/external/images/2",
            displayName = "photo2.png",
            mimeType = "image/png",
            timestamp = now,
            sizeBytes = 2048L
        )

        repository.logProcessedFile(log1)
        repository.logProcessedFile(log2)

        val history = repository.processedFilesFlow.first()
        assertEquals("Should contain 2 history logs", 2, history.size)
        // Ordered by timestamp DESC
        assertEquals("photo2.png", history[0].displayName)
        assertEquals("photo1.jpg", history[1].displayName)
    }

    @Test
    fun testDeleteByUri() = runTest {
        val log1 = ProcessedFileLog(
            uri = "content://media/1",
            displayName = "file1.jpg",
            mimeType = "image/jpeg"
        )
        val log2 = ProcessedFileLog(
            uri = "content://media/2",
            displayName = "file2.jpg",
            mimeType = "image/jpeg"
        )

        repository.logProcessedFile(log1)
        repository.logProcessedFile(log2)

        val deletedCount = repository.deleteByUri("content://media/1")
        assertEquals(1, deletedCount)

        val remaining = repository.processedFilesFlow.first()
        assertEquals(1, remaining.size)
        assertEquals("content://media/2", remaining[0].uri)
    }

    @Test
    fun testClearHistory() = runTest {
        val log = ProcessedFileLog(
            uri = "content://media/1",
            displayName = "file1.jpg",
            mimeType = "image/jpeg"
        )
        repository.logProcessedFile(log)

        val clearedCount = repository.clearHistory()
        assertEquals(1, clearedCount)

        val remaining = repository.processedFilesFlow.first()
        assertTrue("History should be empty after clearHistory", remaining.isEmpty())
    }

    @Test
    fun testRetentionPolicyClearAfter24Hours() = runTest {
        val now = System.currentTimeMillis()
        val oldTimestamp = now - (48L * 60 * 60 * 1000) // 48 hours ago
        val recentTimestamp = now - (1L * 60 * 60 * 1000) // 1 hour ago

        val oldLog = ProcessedFileLog(
            uri = "content://media/old",
            displayName = "old.jpg",
            mimeType = "image/jpeg",
            timestamp = oldTimestamp
        )
        val recentLog = ProcessedFileLog(
            uri = "content://media/recent",
            displayName = "recent.jpg",
            mimeType = "image/jpeg",
            timestamp = recentTimestamp
        )

        repository.logProcessedFile(oldLog)
        repository.logProcessedFile(recentLog)

        repository.performMaintenance(HistoryRetentionPolicy.CLEAR_AFTER_24_HOURS)

        val remaining = repository.processedFilesFlow.first()
        assertEquals(1, remaining.size)
        assertEquals("recent.jpg", remaining[0].displayName)
    }

    @Test
    fun testRetentionPolicyKeep100ItemsPrunesExcess() = runTest {
        val baseTime = System.currentTimeMillis()
        // Insert 105 items
        for (i in 1..105) {
            val log = ProcessedFileLog(
                uri = "content://media/$i",
                displayName = "file_$i.jpg",
                mimeType = "image/jpeg",
                timestamp = baseTime + i
            )
            repository.logProcessedFile(log, retentionPolicy = HistoryRetentionPolicy.KEEP_100_ITEMS)
        }

        val remaining = repository.processedFilesFlow.first()
        assertEquals("Should be pruned to exactly 100 items", 100, remaining.size)
        // Newest item was 105, oldest surviving should be item 6
        assertEquals("file_105.jpg", remaining.first().displayName)
        assertEquals("file_6.jpg", remaining.last().displayName)
    }

    @Test
    fun testRetentionPolicyClearOnExit() = runTest {
        val log = ProcessedFileLog(
            uri = "content://media/exit_test",
            displayName = "exit.jpg",
            mimeType = "image/jpeg"
        )
        repository.logProcessedFile(log)

        repository.performMaintenance(HistoryRetentionPolicy.CLEAR_ON_EXIT)

        val remaining = repository.processedFilesFlow.first()
        assertTrue("Should be empty under CLEAR_ON_EXIT", remaining.isEmpty())
    }
}
