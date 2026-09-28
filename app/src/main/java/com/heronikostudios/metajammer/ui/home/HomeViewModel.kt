package com.heronikostudios.metajammer.ui.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heronikostudios.metajammer.data.FileRepository
import com.heronikostudios.metajammer.domain.model.SelectedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel managing file selection, ingestion from SAF/Intents, and selection removal.
 */
class HomeViewModel(
    private val fileRepository: FileRepository
) : ViewModel() {

    constructor(application: Application) : this(
        FileRepository(application.applicationContext)
    )

    private val _selectedFiles = MutableStateFlow<List<SelectedFile>>(emptyList())
    val selectedFiles: StateFlow<List<SelectedFile>> = _selectedFiles.asStateFlow()

    fun setIncomingUris(uris: List<Uri>, onLoaded: ((List<SelectedFile>) -> Unit)? = null) {
        viewModelScope.launch {
            val files = setIncomingUrisSuspend(uris)
            onLoaded?.invoke(files)
        }
    }

    suspend fun setIncomingUrisSuspend(uris: List<Uri>): List<SelectedFile> {
        val files = withContext(Dispatchers.IO) {
            uris.distinct().map { uri ->
                async { fileRepository.getSelectedFile(uri) }
            }.awaitAll()
        }
        _selectedFiles.value = files
        return files
    }

    fun removeFileFromSelection(file: SelectedFile): Boolean {
        val currentFiles = _selectedFiles.value.toMutableList()
        val removed = currentFiles.remove(file)
        if (removed) {
            _selectedFiles.value = currentFiles
        }
        return removed
    }

    fun clearSelection() {
        _selectedFiles.value = emptyList()
    }

    fun setFiles(files: List<SelectedFile>) {
        _selectedFiles.value = files
    }
}
