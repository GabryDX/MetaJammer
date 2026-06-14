package com.heronikostudios.metajammer.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.heronikostudios.metajammer.data.FileRepository
import com.heronikostudios.metajammer.data.MetadataRepository
import com.heronikostudios.metajammer.data.SettingsRepository
import com.heronikostudios.metajammer.domain.model.AppSettings
import com.heronikostudios.metajammer.domain.model.MetadataEntry
import com.heronikostudios.metajammer.domain.model.MetadataReplacementPlan
import com.heronikostudios.metajammer.domain.model.NightModeSetting
import com.heronikostudios.metajammer.domain.model.ProcessingMode
import com.heronikostudios.metajammer.domain.model.SelectedFile
import com.heronikostudios.metajammer.domain.model.SharedInputOutputAction
import com.heronikostudios.metajammer.domain.model.ThumbnailHandling
import com.heronikostudios.metajammer.domain.usecase.ProcessFileUseCase
import com.heronikostudios.metajammer.domain.usecase.SaveFileUseCase
import com.heronikostudios.metajammer.metadata.MetadataReplacementGenerator
import com.heronikostudios.metajammer.util.SanitizationUtils
import com.heronikostudios.metajammer.worker.MetadataProcessingWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = getApplication<Application>().applicationContext
    private val fileRepository = FileRepository(appContext)
    private val metadataRepository = MetadataRepository(fileRepository)
    private val settingsRepository = SettingsRepository(appContext)
    private val workManager = WorkManager.getInstance(appContext)
    private val processFileUseCase = ProcessFileUseCase(metadataRepository)
    private val saveFileUseCase = SaveFileUseCase(fileRepository)

    private val _selectedFiles = MutableStateFlow<List<SelectedFile>>(emptyList())
    val selectedFiles: StateFlow<List<SelectedFile>> = _selectedFiles.asStateFlow()

    private val _metadataPreview = MutableStateFlow<Map<Uri, List<MetadataEntry>>>(emptyMap())
    val metadataPreview: StateFlow<Map<Uri, List<MetadataEntry>>> = _metadataPreview.asStateFlow()

    private val _changePreview = MutableStateFlow<Map<Uri, List<MetadataEntry>>>(emptyMap())
    val changePreview: StateFlow<Map<Uri, List<MetadataEntry>>> = _changePreview.asStateFlow()

    private val _replacementPlans = MutableStateFlow<Map<Uri, MetadataReplacementPlan>>(emptyMap())
    val replacementPlans: StateFlow<Map<Uri, MetadataReplacementPlan>> = _replacementPlans.asStateFlow()

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

    private val _settingsInitialized = MutableStateFlow(false)
    val settingsInitialized: StateFlow<Boolean> = _settingsInitialized.asStateFlow()

    private val _workInfo = MutableStateFlow<WorkInfo?>(null)
    val workInfo: StateFlow<WorkInfo?> = _workInfo.asStateFlow()

    init {
        fileRepository.clearCache()
        observeSettings()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.useRandomFileNamesFlow.collect {
                _appSettings.value = _appSettings.value.copy(useRandomFileNames = it)
            }
        }
        viewModelScope.launch {
            settingsRepository.defaultSavingPathFlow.collect {
                _appSettings.value = _appSettings.value.copy(defaultSavingPath = it)
            }
        }
        viewModelScope.launch {
            settingsRepository.keepImageOrientationFlow.collect {
                _appSettings.value = _appSettings.value.copy(keepImageOrientation = it)
            }
        }
        viewModelScope.launch {
            settingsRepository.shareResultAsDefaultFlow.collect {
                _appSettings.value = _appSettings.value.copy(shareResultAsDefault = it)
            }
        }
        viewModelScope.launch {
            settingsRepository.defaultPrefixFlow.collect {
                _appSettings.value = _appSettings.value.copy(defaultPrefix = it)
            }
        }
        viewModelScope.launch {
            settingsRepository.defaultSuffixFlow.collect {
                _appSettings.value = _appSettings.value.copy(defaultSuffix = it)
            }
        }
        viewModelScope.launch {
            settingsRepository.nightModeFlow.collect {
                _appSettings.value = _appSettings.value.copy(nightMode = it)
            }
        }
        viewModelScope.launch {
            settingsRepository.oledModeFlow.collect {
                _appSettings.value = _appSettings.value.copy(oledMode = it)
            }
        }
        viewModelScope.launch {
            var firstEmission = true
            settingsRepository.autoHandleSharedFilesFlow.collect {
                _appSettings.value = _appSettings.value.copy(autoHandleSharedFiles = it)
                if (firstEmission) {
                    _settingsInitialized.value = true
                    firstEmission = false
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.sharedFilesProcessingModeFlow.collect {
                _appSettings.value = _appSettings.value.copy(sharedFilesProcessingMode = it)
            }
        }
        viewModelScope.launch {
            settingsRepository.sharedFilesOutputActionFlow.collect {
                _appSettings.value = _appSettings.value.copy(sharedFilesOutputAction = it)
            }
        }
        viewModelScope.launch {
            settingsRepository.sharedFilesCustomPathFlow.collect {
                _appSettings.value = _appSettings.value.copy(sharedFilesCustomPath = it)
            }
        }
        viewModelScope.launch {
            settingsRepository.thumbnailHandlingFlow.collect {
                _appSettings.value = _appSettings.value.copy(thumbnailHandling = it)
            }
        }
        viewModelScope.launch {
            settingsRepository.allowInternetForMapFlow.collect {
                _appSettings.value = _appSettings.value.copy(allowInternetForMap = it)
            }
        }
    }

    fun setIncomingUris(uris: List<Uri>) {
        viewModelScope.launch {
            val files = withContext(Dispatchers.IO) {
                uris.distinct().map(fileRepository::getSelectedFile)
            }
            clearTempFiles()
            _selectedFiles.value = files
            _metadataPreview.value = emptyMap()
            _changePreview.value = emptyMap()
            _replacementPlans.value = emptyMap()
            _selectedMode.value = null
            loadMetadataPreview(files)
        }
    }

    fun clearSelection() {
        _selectedFiles.value = emptyList()
        _metadataPreview.value = emptyMap()
        _changePreview.value = emptyMap()
        _replacementPlans.value = emptyMap()
        _selectedMode.value = null
        clearTempFiles()
    }

    private fun loadMetadataPreview(files: List<SelectedFile>) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    files.associate { file ->
                        file.uri to metadataRepository.readMetadata(file)
                    }
                }
            }.onSuccess {
                _metadataPreview.value = it
            }.onFailure {
                Timber.e(it, "Failed to read metadata for files")
                _message.value = "Failed to read metadata: ${it.message}"
            }
        }
    }

    fun setProcessingMode(mode: ProcessingMode) {
        _selectedMode.value = mode
        if (mode == ProcessingMode.POISON_METADATA && _replacementPlans.value.isEmpty()) {
            _replacementPlans.value = _selectedFiles.value.associate { it.uri to MetadataReplacementGenerator.generatePlan() }
        } else if (mode != ProcessingMode.POISON_METADATA) {
            _replacementPlans.value = emptyMap()
        }
        generateChangePreview()
    }

    fun updatePlanLocation(uri: Uri, latitude: Double, longitude: Double) {
        val currentPlans = _replacementPlans.value.toMutableMap()
        val plan = currentPlans[uri] ?: MetadataReplacementGenerator.generatePlan()

        val latitudeRef = if (latitude >= 0) "N" else "S"
        val longitudeRef = if (longitude >= 0) "E" else "W"

        currentPlans[uri] = plan.copy(
            latitude = latitude,
            longitude = longitude,
            latitudeRef = latitudeRef,
            longitudeRef = longitudeRef
        )
        _replacementPlans.value = currentPlans
        generateChangePreview()
    }

    fun regeneratePoisonPlans() {
        if (_selectedMode.value != ProcessingMode.POISON_METADATA) return
        _replacementPlans.value = _selectedFiles.value.associate {
            it.uri to MetadataReplacementGenerator.generatePlan()
        }
        generateChangePreview()
    }

    private fun generateChangePreview() {
        val mode = _selectedMode.value ?: run {
            _changePreview.value = emptyMap()
            return
        }

        val keepOrientation = _appSettings.value.keepImageOrientation

        _changePreview.value = _selectedFiles.value.associate { file ->
            val currentMetadata = _metadataPreview.value[file.uri].orEmpty()
            val currentMap = currentMetadata.associate { it.key to it.value }

            val entries = when (mode) {
                ProcessingMode.REMOVE_METADATA -> {
                    if (currentMetadata.isEmpty()) {
                        listOf(MetadataEntry("Info", "No metadata would be removed"))
                    } else {
                        currentMetadata.map { MetadataEntry(it.key, "${it.value}  →  [REMOVED]") }
                    }
                }

                ProcessingMode.POISON_METADATA -> {
                    val plan = _replacementPlans.value[file.uri]
                    if (plan == null) {
                        listOf(MetadataEntry("Info", "No replacement plan available"))
                    } else {
                        val targetMap = linkedMapOf(
                            "DateTime" to plan.dateTime,
                            "DateTimeOriginal" to plan.dateTime,
                            "DateTimeDigitized" to plan.dateTime,
                            "Make" to plan.make,
                            "Model" to plan.model,
                            "Software" to plan.software,
                            "ImageDescription" to plan.imageDescription,
                            "UserComment" to plan.userComment,
                            "PhotographicSensitivity" to plan.photographicSensitivity,
                            "ExposureTime" to plan.exposureTime,
                            "FNumber" to plan.fNumber,
                            "FocalLength" to plan.focalLength,
                            "WhiteBalance" to plan.whiteBalance,
                            "Flash" to plan.flash,
                            "GPSLatitude" to plan.latitude.toString(),
                            "GPSLatitudeRef" to plan.latitudeRef,
                            "GPSLongitude" to plan.longitude.toString(),
                            "GPSLongitudeRef" to plan.longitudeRef
                        )

                        if (keepOrientation) {
                            currentMap["Orientation"]?.let { targetMap["Orientation"] = it }
                        }

                        linkedSetOf<String>().apply {
                            addAll(currentMap.keys)
                            addAll(targetMap.keys)
                        }.map { key ->
                            val oldValue = currentMap[key]
                            val newValue = targetMap[key]
                            val value = when {
                                oldValue == null && newValue != null -> "[ADDED] $newValue"
                                oldValue != null && newValue == null -> "[UNCHANGED] $oldValue"
                                oldValue == newValue -> "[UNCHANGED] ${oldValue ?: ""}"
                                else -> "[CHANGED] $oldValue  →  $newValue"
                            }
                            MetadataEntry(key, value)
                        }
                    }
                }
            }

            file.uri to entries
        }
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

        if (files.size > 5) {
            enqueueBackgroundProcessing(files, mode)
            return
        }

        viewModelScope.launch {
            _processing.value = true
            runCatching {
                withContext(Dispatchers.IO) {
                    files.map { selectedFile ->
                        val plan = _replacementPlans.value[selectedFile.uri]
                        selectedFile to processFileUseCase(
                            selectedFile = selectedFile,
                            processingMode = mode,
                            keepOrientation = _appSettings.value.keepImageOrientation,
                            thumbnailHandling = _appSettings.value.thumbnailHandling,
                            replacementPlan = plan
                        )
                    }
                }
            }.onSuccess {
                _processedFiles.value = it
                _message.value = "Processing complete"
            }.onFailure {
                Timber.e(it, "Manual processing failed")
                _message.value = "Processing failed: ${it.message}"
            }
            _processing.value = false
        }
    }

    private fun enqueueBackgroundProcessing(files: List<SelectedFile>, mode: ProcessingMode) {
        val plans = _replacementPlans.value.mapKeys { it.key.toString() }
        val plansFile = File(appContext.cacheDir, "processing_plans_${System.currentTimeMillis()}.json")
        plansFile.writeText(Json.encodeToString(plans))

        val inputData = Data.Builder()
            .putStringArray(MetadataProcessingWorker.KEY_INPUT_URIS, files.map { it.uri.toString() }.toTypedArray())
            .putString(MetadataProcessingWorker.KEY_MODE, mode.name)
            .putBoolean(MetadataProcessingWorker.KEY_KEEP_ORIENTATION, _appSettings.value.keepImageOrientation)
            .putString(MetadataProcessingWorker.KEY_THUMBNAIL_HANDLING, _appSettings.value.thumbnailHandling.name)
            .putString(MetadataProcessingWorker.KEY_PLANS_FILE_PATH, plansFile.absolutePath)
            .putString(MetadataProcessingWorker.KEY_SAVING_PATH, _appSettings.value.defaultSavingPath)
            .putString(MetadataProcessingWorker.KEY_DEFAULT_PREFIX, _appSettings.value.defaultPrefix)
            .putString(MetadataProcessingWorker.KEY_DEFAULT_SUFFIX, _appSettings.value.defaultSuffix)
            .putBoolean(MetadataProcessingWorker.KEY_USE_RANDOM_NAMES, _appSettings.value.useRandomFileNames)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<MetadataProcessingWorker>()
            .setInputData(inputData)
            .build()

        workManager.enqueue(workRequest)

        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(workRequest.id).collect { info ->
                _workInfo.value = info
                if (info != null && info.state == WorkInfo.State.SUCCEEDED) {
                    val savedUris = info.outputData.getStringArray("saved_uris")
                    _message.value = "Background processing complete. ${savedUris?.size ?: 0} files saved."
                } else if (info != null && info.state == WorkInfo.State.FAILED) {
                    _message.value = "Background processing failed."
                }
            }
        }
    }

    fun autoHandleSharedInput(
        onShareFilesReady: (List<File>, String?) -> Unit
    ) {
        val files = _selectedFiles.value
        if (files.isEmpty()) {
            _message.value = "No shared files received"
            return
        }

        val mode = _appSettings.value.sharedFilesProcessingMode
        val keepOrientation = _appSettings.value.keepImageOrientation

        if (mode == ProcessingMode.POISON_METADATA) {
            _replacementPlans.value = files.associate {
                it.uri to MetadataReplacementGenerator.generatePlan()
            }
        } else {
            _replacementPlans.value = emptyMap()
        }

        if (files.size > 5) {
            enqueueBackgroundProcessing(files, mode)
            return
        }

        viewModelScope.launch {
            _processing.value = true
            runCatching {
                val results = withContext(Dispatchers.IO) {
                    files.map { selectedFile ->
                        val plan = _replacementPlans.value[selectedFile.uri]
                        selectedFile to processFileUseCase(
                            selectedFile = selectedFile,
                            processingMode = mode,
                            keepOrientation = keepOrientation,
                            thumbnailHandling = _appSettings.value.thumbnailHandling,
                            replacementPlan = plan
                        )
                    }
                }
                _processedFiles.value = results

                when (_appSettings.value.sharedFilesOutputAction) {
                    SharedInputOutputAction.SAVE_TO_DEFAULT_FOLDER -> {
                        saveProcessedFilesToDefault()
                    }

                    SharedInputOutputAction.SAVE_TO_SHARED_FOLDER -> {
                        val path = _appSettings.value.sharedFilesCustomPath
                        if (path.isNullOrBlank()) {
                            throw IllegalStateException("No shared-files folder configured")
                        }
                        saveProcessedFilesToCustom(Uri.parse(path))
                    }

                    SharedInputOutputAction.SHARE_TO_ANOTHER_APP -> {
                        if (_processedFiles.value.isEmpty()) {
                            throw IllegalStateException("No processed files available for sharing")
                        }
                        val processedFilesList = _processedFiles.value.map { it.second }
                        val firstMime = _processedFiles.value.first().first.mimeType
                        val allSameMime = _processedFiles.value.all { it.first.mimeType == firstMime }
                        onShareFilesReady(processedFilesList, if (allSameMime) firstMime else "*/*")
                    }
                }
            }.onSuccess {
                _message.value = "Shared files handled automatically"
            }.onFailure {
                Timber.e(it, "Automatic shared file handling failed")
                _message.value = "Automatic handling failed: ${it.message}"
            }

            _processing.value = false
        }
    }

    fun saveProcessedFilesToDefault(): List<Uri> {
        // Since this involves I/O, it's safer on Dispatchers.IO, but currently 
        // it's called from Main and returns a value. 
        // We'll leave the call as is for now as it's relatively light MediaStore I/O, 
        // but ideally use a Flow or deferred result.
        val results = _processedFiles.value.mapNotNull { (selectedFile, processedFile) ->
            saveFileUseCase.saveToDefaultFolder(
                sourceFile = processedFile,
                displayName = buildOutputName(selectedFile.displayName),
                mimeType = selectedFile.mimeType,
                configuredPath = _appSettings.value.defaultSavingPath
            )
        }
        if (results.isNotEmpty()) {
            _message.value = "Saved ${results.size} file(s) to default folder"
        } else if (_processedFiles.value.isNotEmpty()) {
            _message.value = "Failed to save files"
        }
        return results
    }

    fun saveProcessedFilesToCustom(treeUri: Uri): List<Uri> {
        val results = _processedFiles.value.mapNotNull { (selectedFile, processedFile) ->
            saveFileUseCase.saveToCustomFolder(
                treeUri = treeUri,
                sourceFile = processedFile,
                displayName = buildOutputName(selectedFile.displayName),
                mimeType = selectedFile.mimeType
            )
        }
        if (results.isNotEmpty()) {
            _message.value = "Saved ${results.size} file(s) to custom folder"
        } else if (_processedFiles.value.isNotEmpty()) {
            _message.value = "Failed to save files"
        }
        return results
    }

    fun getFirstProcessedFileForSharing(): Pair<SelectedFile, File>? =
        _processedFiles.value.firstOrNull()

    fun persistAndSetDefaultSavingPath(uri: Uri?) {
        if (uri == null) {
            viewModelScope.launch {
                settingsRepository.setDefaultSavingPathString("Pictures/MetaJammer")
                _message.value = "Default saving path reset to Pictures/MetaJammer"
            }
            return
        }

        if (uri.toString().startsWith("content://")) {
            runCatching {
                appContext.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }

        viewModelScope.launch {
            settingsRepository.setDefaultSavingPath(uri)
            _message.value = "Default saving path updated"
        }
    }

    fun persistAndSetSharedFilesCustomPath(uri: Uri?) {
        if (uri == null) {
            viewModelScope.launch {
                settingsRepository.setSharedFilesCustomPath(null)
                _message.value = "Shared-files folder cleared"
            }
            return
        }

        if (uri.toString().startsWith("content://")) {
            runCatching {
                appContext.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }

        viewModelScope.launch {
            settingsRepository.setSharedFilesCustomPath(uri)
            _message.value = "Shared-files folder updated"
        }
    }

    fun setUseRandomFileNames(enabled: Boolean) = launchSettingUpdate {
        settingsRepository.setUseRandomFileNames(enabled)
    }

    fun setKeepImageOrientation(enabled: Boolean) = launchSettingUpdate {
        settingsRepository.setKeepImageOrientation(enabled)
        if (_selectedMode.value != null) generateChangePreview()
    }

    fun setShareResultAsDefault(enabled: Boolean) = launchSettingUpdate {
        settingsRepository.setShareResultAsDefault(enabled)
    }

    fun setDefaultPrefix(value: String) = launchSettingUpdate {
        settingsRepository.setDefaultPrefix(value)
    }

    fun setDefaultSuffix(value: String) = launchSettingUpdate {
        settingsRepository.setDefaultSuffix(value)
    }

    fun setNightMode(mode: NightModeSetting) = launchSettingUpdate {
        settingsRepository.setNightMode(mode)
    }

    fun setOledMode(enabled: Boolean) = launchSettingUpdate {
        settingsRepository.setOledMode(enabled)
    }

    fun setAutoHandleSharedFiles(enabled: Boolean) = launchSettingUpdate {
        settingsRepository.setAutoHandleSharedFiles(enabled)
    }

    fun setSharedFilesProcessingMode(mode: ProcessingMode) = launchSettingUpdate {
        settingsRepository.setSharedFilesProcessingMode(mode)
    }

    fun setSharedFilesOutputAction(action: SharedInputOutputAction) = launchSettingUpdate {
        settingsRepository.setSharedFilesOutputAction(action)
    }

    fun setThumbnailHandling(handling: ThumbnailHandling) = launchSettingUpdate {
        settingsRepository.setThumbnailHandling(handling)
    }

    fun setAllowInternetForMap(allowed: Boolean) = launchSettingUpdate {
        settingsRepository.setAllowInternetForMap(allowed)
    }

    private fun launchSettingUpdate(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun clearTempFiles() {
        _processedFiles.value.forEach { (_, file) -> runCatching { file.delete() } }
        _processedFiles.value = emptyList()
        fileRepository.clearCache()
    }

    override fun onCleared() {
        super.onCleared()
        clearTempFiles()
    }

    private fun buildOutputName(originalName: String): String {
        val settings = _appSettings.value

        val dotIndex = originalName.lastIndexOf('.')
        val base = if (dotIndex > 0) originalName.substring(0, dotIndex) else originalName
        val ext = if (dotIndex > 0) originalName.substring(dotIndex) else ""

        val finalBase = if (settings.useRandomFileNames) {
            "mj_${System.currentTimeMillis()}"
        } else {
            val prefix = SanitizationUtils.sanitizeSimple(settings.defaultPrefix)
            val suffix = SanitizationUtils.sanitizeSimple(settings.defaultSuffix)
            val sanitizedBase = SanitizationUtils.sanitizeSimple(base)
            "$prefix$sanitizedBase$suffix"
        }

        val extension = SanitizationUtils.sanitizeSimple(ext)
        return "${finalBase}_processed$extension"
    }
}
