package com.heronikostudios.metajammer.ui.home

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.heronikostudios.metajammer.data.FileRepository
import com.heronikostudios.metajammer.domain.model.SelectedFile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {

    private lateinit var context: Context
    private lateinit var fileRepository: FileRepository
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        fileRepository = FileRepository(context)
        viewModel = HomeViewModel(fileRepository)
    }

    @Test
    fun testInitialSelectedFilesIsEmpty() {
        assertTrue("Initially selected files must be empty", viewModel.selectedFiles.value.isEmpty())
    }

    @Test
    fun testSetFilesUpdatesSelection() {
        val files = listOf(
            SelectedFile(uri = "content://file1".toUri(), displayName = "photo1.jpg", mimeType = "image/jpeg"),
            SelectedFile(uri = "content://file2".toUri(), displayName = "photo2.png", mimeType = "image/png")
        )
        viewModel.setFiles(files)

        assertEquals(2, viewModel.selectedFiles.value.size)
        assertEquals("photo1.jpg", viewModel.selectedFiles.value[0].displayName)
        assertEquals("photo2.png", viewModel.selectedFiles.value[1].displayName)
    }

    @Test
    fun testRemoveFileFromSelection() {
        val file1 = SelectedFile(uri = "content://file1".toUri(), displayName = "photo1.jpg", mimeType = "image/jpeg")
        val file2 = SelectedFile(uri = "content://file2".toUri(), displayName = "photo2.png", mimeType = "image/png")
        viewModel.setFiles(listOf(file1, file2))

        val removed = viewModel.removeFileFromSelection(file1)
        assertTrue(removed)
        assertEquals(1, viewModel.selectedFiles.value.size)
        assertEquals(file2, viewModel.selectedFiles.value[0])
    }

    @Test
    fun testClearSelection() {
        val file1 = SelectedFile(uri = "content://file1".toUri(), displayName = "photo1.jpg", mimeType = "image/jpeg")
        viewModel.setFiles(listOf(file1))
        assertFalse(viewModel.selectedFiles.value.isEmpty())

        viewModel.clearSelection()
        assertTrue("Selection should be empty after clearSelection", viewModel.selectedFiles.value.isEmpty())
    }

    @Test
    fun testSetIncomingUrisSuspendDeduplicatesUris() = runTest {
        // Create dummy test file
        val file = java.io.File(context.cacheDir, "test_dedup.jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val uri = file.toUri()

        // Pass same URI twice
        val result = viewModel.setIncomingUrisSuspend(listOf(uri, uri))

        assertEquals("Duplicates should be removed", 1, result.size)
        assertEquals(1, viewModel.selectedFiles.value.size)
    }
}
