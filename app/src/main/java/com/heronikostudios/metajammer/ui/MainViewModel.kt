package com.heronikostudios.metajammer.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.heronikostudios.metajammer.data.FileRepository
import com.heronikostudios.metajammer.data.MetadataRepository
import com.heronikostudios.metajammer.data.SettingsRepository
import com.heronikostudios.metajammer.domain.model.PostProcessAction
import com.heronikostudios.metajammer.domain.model.ProcessingMode
import com.heronikostudios.metajammer.domain.model.SelectedFile
import com.heronikostudios.metajammer.domain.usecase.ProcessFileUseCase
import com.heronikostudios.metajammer.domain.usecase.SaveFileUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = getApplication<Application>().applicationContext

    private val fileRepository = FileRepository(appContext)
    private val metadataRepository = MetadataRepository(appContext, fileRepository)
    private val settingsRepository = SettingsRepository(appContext)
    private val processFileUseCase = ProcessFileUseCase(metadataRepository)
    private val saveFileUseCase = SaveFileUseCase(fileRepository)

    private val _selectedFiles = MutableStateFlow<List<SelectedFile>>(emptyList())
    val selectedFiles: StateFlow<List<SelectedFile>> = _selectedFiles.asStateFlow()

    private val _selectedMode = MutableStateFlow<ProcessingMode?>(null)
    val selectedMode: StateFlow<ProcessingMode?> = _selectedMode.asStateFlow()

    private val _selectedPostAction = MutableStateFlow(PostProcessAction.SAVE_DEFAULT)
    val selectedPostAction: StateFlow<PostProcessAction> = _selectedPostAction.asStateFlow()

    private val _processedFiles = MutableStateFlow<List<Pair<SelectedFile, File>>>(emptyList())
    val processedFiles: StateFlow<List<Pair<SelectedFile, File>>> = _processedFiles.asStateFlow()

    private val _processing = MutableStateFlow(false)
    val processing: StateFlow<Boolean> = _processing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.defaultPostActionFlow.collect {
                _selectedPostAction.value = it
            }
        }
    }

    fun setIncomingUris(uris: List<Uri>) {
        val files = uris.map { fileRepository.getSelectedFile(it) }
        _selectedFiles.value = files
    }

    fun setProcessingMode(mode: ProcessingMode) {
        _selectedMode.value = mode
    }

    fun setPostProcessAction(action: PostProcessAction) {
        _selectedPostAction.value = action
    }

    fun saveDefaultPostAction(action: PostProcessAction) {
        viewModelScope.launch {
            settingsRepository.setDefaultPostAction(action)
            _message.value = "Default action saved: $action"
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun clearProcessedFiles() {
        _processedFiles.value = emptyList()
    }

    fun processFiles() {
        val files = _selectedFiles.value
        val mode = _selectedMode.value

        if (files.isEmpty()) {
            _message.value = "No files selected"
            return
        }

        if (mode == null) {
            _message.value = "Please choose a processing mode"
            return
        }

        viewModelScope.launch {
            _processing.value = true
            try {
                val results = files.map { selectedFile ->
                    selectedFile to processFileUseCase(selectedFile, mode)
                }
                _processedFiles.value = results
                _message.value = "Processing complete"
            } catch (e: Exception) {
                _message.value = "Processing failed: ${e.message}"
            } finally {
                _processing.value = false
            }
        }
    }

    fun saveProcessedFilesToDefault(): List<Uri> {
        val results = mutableListOf<Uri>()

        _processedFiles.value.forEach { (selectedFile, processedFile) ->
            val fileName = buildOutputName(selectedFile.displayName)
            val savedUri = saveFileUseCase.saveToDefaultFolder(
                sourceFile = processedFile,
                displayName = fileName,
                mimeType = selectedFile.mimeType
            )
            if (savedUri != null) results.add(savedUri)
        }

        return results
    }

    fun saveProcessedFilesToCustom(treeUri: Uri): List<Uri> {
        val results = mutableListOf<Uri>()

        _processedFiles.value.forEach { (selectedFile, processedFile) ->
            val fileName = buildOutputName(selectedFile.displayName)
            val savedUri = saveFileUseCase.saveToCustomFolder(
                treeUri = treeUri,
                sourceFile = processedFile,
                displayName = fileName,
                mimeType = selectedFile.mimeType
            )
            if (savedUri != null) results.add(savedUri)
        }

        return results
    }

    private fun buildOutputName(originalName: String): String {
        val dotIndex = originalName.lastIndexOf('.')
        return if (dotIndex > 0) {
            val base = originalName.substring(0, dotIndex)
            val ext = originalName.substring(dotIndex)
            "${base}_processed$ext"
        } else {
            "${originalName}_processed"
        }
    }
}
