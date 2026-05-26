package com.heronikostudios.metajammer.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.heronikostudios.metajammer.data.FileRepository
import com.heronikostudios.metajammer.data.MetadataRepository
import com.heronikostudios.metajammer.data.SettingsRepository
import com.heronikostudios.metajammer.domain.model.AppSettings
import com.heronikostudios.metajammer.domain.model.MetadataEntry
import com.heronikostudios.metajammer.domain.model.NightModeSetting
import com.heronikostudios.metajammer.domain.model.PostProcessAction
import com.heronikostudios.metajammer.domain.model.ProcessingMode
import com.heronikostudios.metajammer.domain.model.SelectedFile
import com.heronikostudios.metajammer.domain.usecase.ProcessFileUseCase
import com.heronikostudios.metajammer.domain.usecase.SaveFileUseCase
import com.heronikostudios.metajammer.metadata.MetadataReplacementGenerator
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

    private val _metadataPreview = MutableStateFlow<Map<Uri, List<MetadataEntry>>>(emptyMap())
    val metadataPreview: StateFlow<Map<Uri, List<MetadataEntry>>> = _metadataPreview.asStateFlow()

    private val _changePreview = MutableStateFlow<Map<Uri, List<MetadataEntry>>>(emptyMap())
    val changePreview: StateFlow<Map<Uri, List<MetadataEntry>>> = _changePreview.asStateFlow()

    private val _selectedMode = MutableStateFlow<ProcessingMode?>(null)
    val selectedMode: StateFlow<ProcessingMode?> = _selectedMode.asStateFlow()

    private val _processedFiles = MutableStateFlow<List<Pair<SelectedFile, File>>>(emptyList())
    val processedFiles: StateFlow<List<Pair<SelectedFile, File>>> = _processedFiles.asStateFlow()

    private val _processing = MutableStateFlow(false)
    val processing: StateFlow<Boolean> = _processing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _appSettings = MutableStateFlow(AppSettings())
    val appSettings: StateFlow<AppSettings> = _appSettings.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.useRandomFileNamesFlow.collect { value ->
                _appSettings.value = _appSettings.value.copy(useRandomFileNames = value)
            }
        }

        viewModelScope.launch {
            settingsRepository.defaultSavingPathFlow.collect { value ->
                _appSettings.value = _appSettings.value.copy(defaultSavingPath = value)
            }
        }

        viewModelScope.launch {
            settingsRepository.automaticDeletionFlow.collect { value ->
                _appSettings.value = _appSettings.value.copy(automaticDeletion = value)
            }
        }

        viewModelScope.launch {
            settingsRepository.keepImageOrientationFlow.collect { value ->
                _appSettings.value = _appSettings.value.copy(keepImageOrientation = value)
            }
        }

        viewModelScope.launch {
            settingsRepository.shareResultAsDefaultFlow.collect { value ->
                _appSettings.value = _appSettings.value.copy(shareResultAsDefault = value)
            }
        }

        viewModelScope.launch {
            settingsRepository.defaultPrefixFlow.collect { value ->
                _appSettings.value = _appSettings.value.copy(defaultPrefix = value)
            }
        }

        viewModelScope.launch {
            settingsRepository.defaultSuffixFlow.collect { value ->
                _appSettings.value = _appSettings.value.copy(defaultSuffix = value)
            }
        }

        viewModelScope.launch {
            settingsRepository.nightModeFlow.collect { value ->
                _appSettings.value = _appSettings.value.copy(nightMode = value)
            }
        }

        viewModelScope.launch {
            settingsRepository.oledModeFlow.collect { value ->
                _appSettings.value = _appSettings.value.copy(oledMode = value)
            }
        }
    }

    fun setIncomingUris(uris: List<Uri>) {
        val files = uris.map { fileRepository.getSelectedFile(it) }
        _selectedFiles.value = files
        loadMetadataPreview(files)
        _changePreview.value = emptyMap()
        _selectedMode.value = null
        _processedFiles.value = emptyList()
    }

    private fun loadMetadataPreview(files: List<SelectedFile>) {
        viewModelScope.launch {
            try {
                val previewMap = files.associate { file ->
                    file.uri to metadataRepository.readMetadata(file)
                }
                _metadataPreview.value = previewMap
            } catch (e: Exception) {
                _message.value = "Failed to read metadata: ${e.message}"
            }
        }
    }

    fun setProcessingMode(mode: ProcessingMode) {
        _selectedMode.value = mode
        generateChangePreview(mode)
    }

    private fun generateChangePreview(mode: ProcessingMode) {
        val previewMap = _selectedFiles.value.associate { file ->
            val currentMetadata = _metadataPreview.value[file.uri].orEmpty()
            val currentMap = currentMetadata.associate { it.key to it.value }

            val changed = when (mode) {
                ProcessingMode.REMOVE_METADATA -> {
                    if (currentMetadata.isEmpty()) {
                        listOf(MetadataEntry("Info", "No metadata would be removed"))
                    } else {
                        currentMetadata.map {
                            MetadataEntry(
                                key = it.key,
                                value = "${it.value}  →  [REMOVED]"
                            )
                        }
                    }
                }

                ProcessingMode.POISON_METADATA -> {
                    val make = MetadataReplacementGenerator.randomMake()
                    val model = MetadataReplacementGenerator.randomModel(make)
                    val software = MetadataReplacementGenerator.randomSoftware(make)
                    val dateTime = MetadataReplacementGenerator.randomRecentDateTime()
                    val imageDescription = MetadataReplacementGenerator.randomImageDescription()
                    val userComment = MetadataReplacementGenerator.randomUserComment()
                    val photographicSensitivity = MetadataReplacementGenerator.randomPhotographicSensitivity()
                    val exposureTime = MetadataReplacementGenerator.randomExposureTime()
                    val fNumber = MetadataReplacementGenerator.randomFNumber()
                    val focalLength = MetadataReplacementGenerator.randomFocalLength()
                    val whiteBalance = MetadataReplacementGenerator.randomWhiteBalance()
                    val flash = MetadataReplacementGenerator.randomFlash()
                    val (lat, lon) = MetadataReplacementGenerator.randomLatLong()
                    val latRef = if (lat >= 0) "N" else "S"
                    val lonRef = if (lon >= 0) "E" else "W"

                    val targetMap = linkedMapOf(
                        "DateTime" to dateTime,
                        "DateTimeOriginal" to dateTime,
                        "DateTimeDigitized" to dateTime,
                        "Make" to make,
                        "Model" to model,
                        "Software" to software,
                        "ImageDescription" to imageDescription,
                        "UserComment" to userComment,
                        "PhotographicSensitivity" to photographicSensitivity,
                        "ExposureTime" to exposureTime,
                        "FNumber" to fNumber,
                        "FocalLength" to focalLength,
                        "WhiteBalance" to whiteBalance,
                        "Flash" to flash,
                        "GPSLatitude" to lat.toString(),
                        "GPSLatitudeRef" to latRef,
                        "GPSLongitude" to lon.toString(),
                        "GPSLongitudeRef" to lonRef
                    )

                    if (_appSettings.value.keepImageOrientation) {
                        currentMap["Orientation"]?.let { orientation ->
                            targetMap["Orientation"] = orientation
                        }
                    }

                    val orderedKeys = linkedSetOf<String>().apply {
                        addAll(currentMap.keys)
                        addAll(targetMap.keys)
                    }

                    orderedKeys.map { key ->
                        val oldValue = currentMap[key]
                        val newValue = targetMap[key]

                        val previewValue = when {
                            oldValue == null && newValue != null -> "[ADDED] $newValue"
                            oldValue != null && newValue == null -> "[UNCHANGED] $oldValue"
                            oldValue != null && newValue != null && oldValue == newValue -> "[UNCHANGED] $oldValue"
                            oldValue != null && newValue != null -> "[CHANGED] $oldValue  →  $newValue"
                            else -> "[NO DATA]"
                        }

                        MetadataEntry(key = key, value = previewValue)
                    }
                }
            }

            file.uri to changed
        }

        _changePreview.value = previewMap
    }

    fun setUseRandomFileNames(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setUseRandomFileNames(enabled)
        }
    }

    fun persistAndSetDefaultSavingPath(uri: Uri?) {
        if (uri == null) {
            viewModelScope.launch {
                settingsRepository.setDefaultSavingPathString("Pictures/MetaJammer")
                _message.value = "Default saving path reset to Pictures/MetaJammer"
            }
            return
        }

        if (uri.toString().startsWith("content://")) {
            try {
                appContext.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
        }

        viewModelScope.launch {
            settingsRepository.setDefaultSavingPath(uri)
            _message.value = "Default saving path updated"
        }
    }

    fun setAutomaticDeletion(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutomaticDeletion(enabled)
        }
    }

    fun setKeepImageOrientation(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setKeepImageOrientation(enabled)
        }
    }

    fun setShareResultAsDefault(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShareResultAsDefault(enabled)
        }
    }

    fun setDefaultPrefix(value: String) {
        viewModelScope.launch {
            settingsRepository.setDefaultPrefix(value)
        }
    }

    fun setDefaultSuffix(value: String) {
        viewModelScope.launch {
            settingsRepository.setDefaultSuffix(value)
        }
    }

    fun setNightMode(mode: NightModeSetting) {
        viewModelScope.launch {
            settingsRepository.setNightMode(mode)
        }
    }

    fun setOledMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setOledMode(enabled)
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
        val keepOrientation = _appSettings.value.keepImageOrientation

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
                    selectedFile to processFileUseCase(
                        selectedFile = selectedFile,
                        processingMode = mode,
                        keepOrientation = keepOrientation
                    )
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
        val configuredPath = _appSettings.value.defaultSavingPath

        _processedFiles.value.forEach { (selectedFile, processedFile) ->
            val fileName = buildOutputName(selectedFile.displayName)
            val savedUri = saveFileUseCase.saveToDefaultFolder(
                sourceFile = processedFile,
                displayName = fileName,
                mimeType = selectedFile.mimeType,
                configuredPath = configuredPath
            )
            if (savedUri != null) results.add(savedUri)
        }

        cleanupProcessedFilesIfNeeded()

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

        cleanupProcessedFilesIfNeeded()

        return results
    }

    private fun cleanupProcessedFilesIfNeeded() {
        if (_appSettings.value.automaticDeletion) {
            _processedFiles.value.forEach { (_, file) ->
                runCatching { file.delete() }
            }
            _processedFiles.value = emptyList()
        }
    }

    private fun buildOutputName(originalName: String): String {
        val settings = _appSettings.value

        val dotIndex = originalName.lastIndexOf('.')
        val base = if (dotIndex > 0) originalName.substring(0, dotIndex) else originalName
        val ext = if (dotIndex > 0) originalName.substring(dotIndex) else ""

        val finalBase = if (settings.useRandomFileNames) {
            "mj_${System.currentTimeMillis()}"
        } else {
            "${settings.defaultPrefix}$base${settings.defaultSuffix}"
        }

        return "${finalBase}_processed$ext"
    }
}
