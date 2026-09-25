package com.heronikostudios.metajammer.ui.processing

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.heronikostudios.metajammer.data.FileRepository
import com.heronikostudios.metajammer.data.MetadataRepository
import com.heronikostudios.metajammer.data.SettingsRepository
import com.heronikostudios.metajammer.domain.model.*
import com.heronikostudios.metajammer.domain.usecase.ProcessFileUseCase
import com.heronikostudios.metajammer.domain.usecase.SaveFileUseCase
import com.heronikostudios.metajammer.metadata.MetadataReplacementGenerator
import com.heronikostudios.metajammer.util.SanitizationUtils
import com.heronikostudios.metajammer.worker.MetadataProcessingWorker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File

/**
 * ViewModel managing metadata extraction, poisoning plan generation, diff previews,
 * file processing (foreground & WorkManager background), saving, and sharing.
 */
class ProcessingViewModel(
    private val metadataRepository: MetadataRepository,
    private val fileRepository: FileRepository,
    private val settingsRepository: SettingsRepository,
    private val processFileUseCase: ProcessFileUseCase,
    private val saveFileUseCase: SaveFileUseCase,
    private val workManager: WorkManager,
    private val cacheDir: File
) : ViewModel() {

    constructor(application: Application) : this(
        metadataRepository = MetadataRepository(FileRepository(application.applicationContext)),
        fileRepository = FileRepository(application.applicationContext),
        settingsRepository = SettingsRepository(application.applicationContext),
        processFileUseCase = ProcessFileUseCase(MetadataRepository(FileRepository(application.applicationContext))),
        saveFileUseCase = SaveFileUseCase(FileRepository(application.applicationContext)),
        workManager = WorkManager.getInstance(application.applicationContext),
        cacheDir = application.applicationContext.cacheDir
    )

    private val _metadataPreview = MutableStateFlow<Map<Uri, List<MetadataEntry>>>(emptyMap())
    val metadataPreview: StateFlow<Map<Uri, List<MetadataEntry>>> = _metadataPreview.asStateFlow()

    private val _changePreview = MutableStateFlow<Map<Uri, List<MetadataEntry>>>(emptyMap())
    val changePreview: StateFlow<Map<Uri, List<MetadataEntry>>> = _changePreview.asStateFlow()

    private val _replacementPlans = MutableStateFlow<Map<Uri, MetadataReplacementPlan>>(emptyMap())
    val replacementPlans: StateFlow<Map<Uri, MetadataReplacementPlan>> = _replacementPlans.asStateFlow()

    private val _selectedMode = MutableStateFlow<ProcessingMode?>(null)
    val selectedMode: StateFlow<ProcessingMode?> = _selectedMode.asStateFlow()

    private val _selectedProfile = MutableStateFlow(PoisoningProfile.RANDOM)
    val selectedProfile: StateFlow<PoisoningProfile> = _selectedProfile.asStateFlow()

    private val _selectedLocationPreset = MutableStateFlow(LocationPreset.RANDOM)
    val selectedLocationPreset: StateFlow<LocationPreset> = _selectedLocationPreset.asStateFlow()

    private val _processedFiles = MutableStateFlow<List<Pair<SelectedFile, File>>>(emptyList())
    val processedFiles: StateFlow<List<Pair<SelectedFile, File>>> = _processedFiles.asStateFlow()

    private val _processing = MutableStateFlow(false)
    val processing: StateFlow<Boolean> = _processing.asStateFlow()

    private val _workInfo = MutableStateFlow<WorkInfo?>(null)
    val workInfo: StateFlow<WorkInfo?> = _workInfo.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun loadMetadataPreview(files: List<SelectedFile>) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    coroutineScope {
                        files.map { file ->
                            async {
                                file.uri to metadataRepository.readMetadata(file)
                            }
                        }.awaitAll().toMap()
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

    fun setProcessingMode(
        mode: ProcessingMode,
        selectedFiles: List<SelectedFile>,
        appSettings: AppSettings
    ) {
        if (_selectedMode.value != mode) {
            clearProcessedFiles()
        }
        _selectedMode.value = mode
        if (mode == ProcessingMode.POISON_METADATA && _replacementPlans.value.isEmpty()) {
            _selectedProfile.value = appSettings.poisoningProfile
            _selectedLocationPreset.value = appSettings.locationPreset
            regeneratePoisonPlansInternal(selectedFiles, appSettings)
        } else if (mode != ProcessingMode.POISON_METADATA) {
            _replacementPlans.value = emptyMap()
        }
        generateChangePreview(selectedFiles, appSettings)
    }

    fun updatePlanLocation(
        uri: Uri,
        latitude: Double,
        longitude: Double,
        selectedFiles: List<SelectedFile>,
        appSettings: AppSettings
    ) {
        val currentPlans = _replacementPlans.value.toMutableMap()
        val file = selectedFiles.find { it.uri == uri }
        val plan = currentPlans[uri] ?: MetadataReplacementGenerator.generatePlan(
            mimeType = file?.mimeType,
            profile = _selectedProfile.value,
            locationPreset = _selectedLocationPreset.value
        )

        val latitudeRef = if (latitude >= 0) "N" else "S"
        val longitudeRef = if (longitude >= 0) "E" else "W"

        currentPlans[uri] = plan.copy(
            latitude = latitude,
            longitude = longitude,
            latitudeRef = latitudeRef,
            longitudeRef = longitudeRef
        )
        _replacementPlans.value = currentPlans
        clearProcessedFiles()
        generateChangePreview(selectedFiles, appSettings)
    }

    fun regeneratePoisonPlans(selectedFiles: List<SelectedFile>, appSettings: AppSettings) {
        if (_selectedMode.value != ProcessingMode.POISON_METADATA) return
        regeneratePoisonPlansInternal(selectedFiles, appSettings)
        clearProcessedFiles()
        generateChangePreview(selectedFiles, appSettings)
    }

    private fun regeneratePoisonPlansInternal(selectedFiles: List<SelectedFile>, appSettings: AppSettings) {
        val currentProfile = _selectedProfile.value
        val currentPreset = _selectedLocationPreset.value
        val useScramble = appSettings.useNearbyScramble

        _replacementPlans.value = selectedFiles.associate { selectedFile ->
            val metadata = _metadataPreview.value[selectedFile.uri].orEmpty()
            val lat = metadata.find { it.key == "GPSLatitude" }?.value?.toDoubleOrNull()
            val lon = metadata.find { it.key == "GPSLongitude" }?.value?.toDoubleOrNull()

            selectedFile.uri to if (useScramble && lat != null && lon != null && currentPreset == LocationPreset.RANDOM) {
                MetadataReplacementGenerator.generatePlan(
                    mimeType = selectedFile.mimeType,
                    existingLat = lat,
                    existingLon = lon,
                    profile = currentProfile,
                    locationPreset = currentPreset
                )
            } else {
                MetadataReplacementGenerator.generatePlan(
                    mimeType = selectedFile.mimeType,
                    profile = currentProfile,
                    locationPreset = currentPreset
                )
            }
        }
    }

    fun setPoisoningProfile(
        profile: PoisoningProfile,
        selectedFiles: List<SelectedFile>,
        appSettings: AppSettings
    ) {
        _selectedProfile.value = profile
        if (_selectedMode.value == ProcessingMode.POISON_METADATA) {
            regeneratePoisonPlans(selectedFiles, appSettings)
        }
    }

    fun setLocationPreset(
        preset: LocationPreset,
        selectedFiles: List<SelectedFile>,
        appSettings: AppSettings
    ) {
        _selectedLocationPreset.value = preset
        if (_selectedMode.value == ProcessingMode.POISON_METADATA) {
            regeneratePoisonPlans(selectedFiles, appSettings)
        }
    }

    fun generateChangePreview(selectedFiles: List<SelectedFile>, appSettings: AppSettings) {
        val mode = _selectedMode.value ?: run {
            _changePreview.value = emptyMap()
            return
        }

        val keepOrientation = appSettings.keepImageOrientation

        viewModelScope.launch(Dispatchers.Default) {
            val preview = selectedFiles.associate { file ->
                val currentMetadata = _metadataPreview.value[file.uri].orEmpty()
                val currentMap = currentMetadata.associate { it.key to it.value }

                val entries = when (mode) {
                    ProcessingMode.REMOVE_METADATA -> {
                        if (currentMetadata.isEmpty()) {
                            listOf(MetadataEntry("Info", "No metadata would be removed"))
                        } else {
                            currentMetadata.map { entry ->
                                val willStrip = com.heronikostudios.metajammer.metadata.ImageMetadataProcessor.shouldStripTag(
                                    entry.key,
                                    appSettings.stripGps,
                                    appSettings.stripDeviceModel,
                                    appSettings.stripDateTime,
                                    appSettings.stripCameraSettings,
                                    appSettings.stripComments
                                )
                                if (willStrip) {
                                    MetadataEntry(entry.key, "${entry.value}  →  [REMOVED]")
                                } else {
                                    MetadataEntry(entry.key, "${entry.value}  (KEPT)")
                                }
                            }
                        }
                    }

                    ProcessingMode.POISON_METADATA -> {
                        val plan = _replacementPlans.value[file.uri]
                        if (plan == null) {
                            listOf(MetadataEntry("Info", "No replacement plan available"))
                        } else {
                            val targetMap = linkedMapOf<String, String>()
                            val mime = file.mimeType ?: ""

                            when {
                                mime.startsWith("image/") -> {
                                    if (appSettings.stripDateTime) {
                                        targetMap["DateTime"] = plan.dateTime
                                        targetMap["DateTimeOriginal"] = plan.dateTime
                                        targetMap["DateTimeDigitized"] = plan.dateTime
                                    } else {
                                        currentMap["DateTime"]?.let { targetMap["DateTime"] = it }
                                        currentMap["DateTimeOriginal"]?.let { targetMap["DateTimeOriginal"] = it }
                                        currentMap["DateTimeDigitized"]?.let { targetMap["DateTimeDigitized"] = it }
                                    }

                                    if (appSettings.stripDeviceModel) {
                                        targetMap["Make"] = plan.make
                                        targetMap["Model"] = plan.model
                                        targetMap["Software"] = plan.software
                                        plan.lensMake?.let { targetMap["LensMake"] = it }
                                        plan.lensModel?.let { targetMap["LensModel"] = it }
                                    } else {
                                        currentMap["Make"]?.let { targetMap["Make"] = it }
                                        currentMap["Model"]?.let { targetMap["Model"] = it }
                                        currentMap["Software"]?.let { targetMap["Software"] = it }
                                        currentMap["LensMake"]?.let { targetMap["LensMake"] = it }
                                        currentMap["LensModel"]?.let { targetMap["LensModel"] = it }
                                    }

                                    currentMap["ImageWidth"]?.let { targetMap["ImageWidth"] = it }
                                    currentMap["ImageLength"]?.let { targetMap["ImageLength"] = it }

                                    if (appSettings.stripComments) {
                                        targetMap["ImageDescription"] = plan.imageDescription
                                        targetMap["UserComment"] = plan.userComment
                                    } else {
                                        currentMap["ImageDescription"]?.let { targetMap["ImageDescription"] = it }
                                        currentMap["UserComment"]?.let { targetMap["UserComment"] = it }
                                    }

                                    if (appSettings.stripCameraSettings) {
                                        targetMap["PhotographicSensitivity"] = plan.photographicSensitivity
                                        targetMap["ExposureTime"] = plan.exposureTime
                                        targetMap["FNumber"] = plan.fNumber
                                        targetMap["FocalLength"] = plan.focalLength
                                        targetMap["WhiteBalance"] = plan.whiteBalance
                                        targetMap["Flash"] = plan.flash
                                    } else {
                                        currentMap["PhotographicSensitivity"]?.let { targetMap["PhotographicSensitivity"] = it }
                                        currentMap["ExposureTime"]?.let { targetMap["ExposureTime"] = it }
                                        currentMap["FNumber"]?.let { targetMap["FNumber"] = it }
                                        currentMap["FocalLength"]?.let { targetMap["FocalLength"] = it }
                                        currentMap["WhiteBalance"]?.let { targetMap["WhiteBalance"] = it }
                                        currentMap["Flash"]?.let { targetMap["Flash"] = it }
                                    }

                                    if (appSettings.stripGps) {
                                        targetMap["GPSLatitude"] = plan.latitude.toString()
                                        targetMap["GPSLatitudeRef"] = plan.latitudeRef
                                        targetMap["GPSLongitude"] = plan.longitude.toString()
                                        targetMap["GPSLongitudeRef"] = plan.longitudeRef
                                    } else {
                                        currentMap["GPSLatitude"]?.let { targetMap["GPSLatitude"] = it }
                                        currentMap["GPSLatitudeRef"]?.let { targetMap["GPSLatitudeRef"] = it }
                                        currentMap["GPSLongitude"]?.let { targetMap["GPSLongitude"] = it }
                                        currentMap["GPSLongitudeRef"]?.let { targetMap["GPSLongitudeRef"] = it }
                                    }
                                }
                                mime.startsWith("video/") || mime.startsWith("audio/") -> {
                                    targetMap["Location"] = "${plan.latitude}, ${plan.longitude}"
                                }
                                mime == "application/pdf" -> {
                                    plan.pdfTitle?.let { targetMap["Title"] = it }
                                    plan.author?.let { targetMap["Author"] = it }
                                    plan.creator?.let { targetMap["Creator"] = it }
                                    plan.producer?.let { targetMap["Producer"] = it }
                                }
                            }

                            if (keepOrientation && mime.startsWith("image/")) {
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
                                    oldValue != null && newValue == null -> "[REMOVED] $oldValue"
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
            _changePreview.value = preview
        }
    }

    fun processFiles(
        selectedFiles: List<SelectedFile>,
        appSettings: AppSettings,
        onSuccess: (() -> Unit)? = null
    ) {
        val files = selectedFiles
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
            enqueueBackgroundProcessing(files, mode, appSettings)
            return
        }

        viewModelScope.launch {
            _processing.value = true
            runCatching {
                withContext(Dispatchers.IO) {
                    coroutineScope {
                        files.map { selectedFile ->
                            async {
                                val plan = _replacementPlans.value[selectedFile.uri]
                                selectedFile to processFileUseCase(
                                    selectedFile = selectedFile,
                                    processingMode = mode,
                                    keepOrientation = appSettings.keepImageOrientation,
                                    thumbnailHandling = appSettings.thumbnailHandling,
                                    replacementPlan = plan,
                                    stripGps = appSettings.stripGps,
                                    stripDeviceModel = appSettings.stripDeviceModel,
                                    stripDateTime = appSettings.stripDateTime,
                                    stripCameraSettings = appSettings.stripCameraSettings,
                                    stripComments = appSettings.stripComments
                                )
                            }
                        }.awaitAll()
                    }
                }
            }.onSuccess {
                _processedFiles.value = it
                _message.value = "Processing complete"
                onSuccess?.invoke()
            }.onFailure {
                Timber.e(it, "Manual processing failed")
                _message.value = "Processing failed: ${it.message}"
            }
            _processing.value = false
        }
    }

    fun enqueueBackgroundProcessing(
        files: List<SelectedFile>,
        mode: ProcessingMode,
        appSettings: AppSettings
    ) {
        val plans = _replacementPlans.value.mapKeys { it.key.toString() }
        val plansFile = File(cacheDir, "processing_plans_${System.currentTimeMillis()}.json")
        plansFile.writeText(Json.encodeToString(plans))

        val settings = appSettings
        val inputData = Data.Builder()
            .putStringArray(MetadataProcessingWorker.KEY_INPUT_URIS, files.map { it.uri.toString() }.toTypedArray())
            .putString(MetadataProcessingWorker.KEY_MODE, mode.name)
            .putBoolean(MetadataProcessingWorker.KEY_KEEP_ORIENTATION, settings.keepImageOrientation)
            .putString(MetadataProcessingWorker.KEY_THUMBNAIL_HANDLING, settings.thumbnailHandling.name)
            .putString(MetadataProcessingWorker.KEY_PLANS_FILE_PATH, plansFile.absolutePath)
            .putString(MetadataProcessingWorker.KEY_FOLDER_STRUCTURE, settings.folderStructure.name)
            .putBoolean(MetadataProcessingWorker.KEY_USE_SUBFOLDERS_IN_UNIFIED, settings.useSubfoldersInUnified)
            .putString(MetadataProcessingWorker.KEY_UNIFIED_SAVING_PATH, settings.unifiedSavingPath)
            .putString(MetadataProcessingWorker.KEY_PICTURES_SAVING_PATH, settings.picturesSavingPath)
            .putString(MetadataProcessingWorker.KEY_MUSIC_SAVING_PATH, settings.musicSavingPath)
            .putString(MetadataProcessingWorker.KEY_MOVIES_SAVING_PATH, settings.moviesSavingPath)
            .putString(MetadataProcessingWorker.KEY_DOCUMENTS_SAVING_PATH, settings.documentsSavingPath)
            .putString(MetadataProcessingWorker.KEY_DEFAULT_PREFIX, settings.defaultPrefix)
            .putString(MetadataProcessingWorker.KEY_DEFAULT_SUFFIX, settings.defaultSuffix)
            .putBoolean(MetadataProcessingWorker.KEY_USE_RANDOM_NAMES, settings.useRandomFileNames)
            .putBoolean(MetadataProcessingWorker.KEY_STRIP_GPS, settings.stripGps)
            .putBoolean(MetadataProcessingWorker.KEY_STRIP_DEVICE_MODEL, settings.stripDeviceModel)
            .putBoolean(MetadataProcessingWorker.KEY_STRIP_DATE_TIME, settings.stripDateTime)
            .putBoolean(MetadataProcessingWorker.KEY_STRIP_CAMERA_SETTINGS, settings.stripCameraSettings)
            .putBoolean(MetadataProcessingWorker.KEY_STRIP_COMMENTS, settings.stripComments)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<MetadataProcessingWorker>()
            .setInputData(inputData)
            .build()

        workManager.enqueue(workRequest)

        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(workRequest.id).collect { info ->
                _workInfo.value = info
                if (info != null && info.state == WorkInfo.State.SUCCEEDED) {
                    val savedUris = info.outputData.getNullableStringArray("saved_uris")
                    _message.value = "Background processing complete. ${savedUris?.size ?: 0} files saved."
                } else if (info != null && info.state == WorkInfo.State.FAILED) {
                    _message.value = "Background processing failed."
                }
            }
        }
    }

    suspend fun saveProcessedFilesToDefault(appSettings: AppSettings): List<Uri> = withContext(Dispatchers.IO) {
        val results = coroutineScope {
            _processedFiles.value.map { (selectedFile, processedFile) ->
                async {
                    val (configuredPath, subPath) = resolveSavingPath(selectedFile.mimeType, appSettings)
                    val savedUri = saveFileUseCase.saveToDefaultFolder(
                        sourceFile = processedFile,
                        displayName = buildOutputName(selectedFile.displayName, appSettings),
                        mimeType = selectedFile.mimeType,
                        configuredPath = configuredPath,
                        subPath = subPath
                    )

                    if (savedUri != null) {
                        settingsRepository.logProcessedFile(
                            ProcessedFileLog(
                                uri = savedUri.toString(),
                                displayName = buildOutputName(selectedFile.displayName, appSettings),
                                mimeType = selectedFile.mimeType,
                                timestamp = System.currentTimeMillis(),
                                sizeBytes = processedFile.length()
                            )
                        )
                    }
                    savedUri
                }
            }.awaitAll().filterNotNull()
        }
        if (results.isNotEmpty()) {
            _message.value = "Saved ${results.size} file(s)"
        } else if (_processedFiles.value.isNotEmpty()) {
            _message.value = "Failed to save files"
        }
        results
    }

    suspend fun saveProcessedFilesToCustom(treeUri: Uri, appSettings: AppSettings): List<Uri> = withContext(Dispatchers.IO) {
        val results = coroutineScope {
            _processedFiles.value.map { (selectedFile, processedFile) ->
                async {
                    val savedUri = saveFileUseCase.saveToCustomFolder(
                        treeUri = treeUri,
                        sourceFile = processedFile,
                        displayName = buildOutputName(selectedFile.displayName, appSettings),
                        mimeType = selectedFile.mimeType
                    )

                    if (savedUri != null) {
                        settingsRepository.logProcessedFile(
                            ProcessedFileLog(
                                uri = savedUri.toString(),
                                displayName = buildOutputName(selectedFile.displayName, appSettings),
                                mimeType = selectedFile.mimeType,
                                timestamp = System.currentTimeMillis(),
                                sizeBytes = processedFile.length()
                            )
                        )
                    }
                    savedUri
                }
            }.awaitAll().filterNotNull()
        }
        if (results.isNotEmpty()) {
            _message.value = "Saved ${results.size} file(s) to custom folder"
        } else if (_processedFiles.value.isNotEmpty()) {
            _message.value = "Failed to save files"
        }
        results
    }

    suspend fun getProcessedFilesForSharing(appSettings: AppSettings): List<File> = withContext(Dispatchers.IO) {
        val pairs = _processedFiles.value
        if (pairs.isEmpty()) return@withContext emptyList()

        val processedFilesList = pairs.map { it.second }
        val selectedFilesList = pairs.map { it.first }

        prepareFilesForSharing(processedFilesList, selectedFilesList, appSettings)
    }

    suspend fun prepareFilesForSharing(
        processedFiles: List<File>,
        selectedFiles: List<SelectedFile>,
        appSettings: AppSettings
    ): List<File> = withContext(Dispatchers.IO) {
        val sharedDir = File(cacheDir, "shared/outgoing_${System.currentTimeMillis()}")
        if (!sharedDir.exists()) {
            sharedDir.mkdirs()
        }

        val results = coroutineScope {
            processedFiles.mapIndexed { index, file ->
                async {
                    val selectedFile = selectedFiles[index]
                    val niceName = buildOutputName(selectedFile.displayName, appSettings)
                    val sharedFile = File(sharedDir, niceName)

                    val success = runCatching {
                        if (!file.renameTo(sharedFile)) {
                            file.copyTo(sharedFile, overwrite = true)
                            file.delete()
                        }
                        true
                    }.onFailure {
                        Timber.e(it, "Failed to move/copy file for sharing: %s", niceName)
                    }.getOrDefault(false)

                    if (success) sharedFile else null
                }
            }.awaitAll().filterNotNull()
        }

        _processedFiles.update { current ->
            current.mapIndexed { index, pair ->
                if (index < results.size) {
                    pair.copy(second = results[index])
                } else pair
            }
        }

        results
    }

    private fun resolveSavingPath(mimeType: String?, settings: AppSettings): Pair<String?, String?> {
        return if (settings.folderStructure == FolderStructure.UNIFIED) {
            val subPath = if (settings.useSubfoldersInUnified) {
                when {
                    mimeType?.startsWith("image/") == true -> "Pictures"
                    mimeType?.startsWith("video/") == true -> "Movies"
                    mimeType?.startsWith("audio/") == true -> "Music"
                    else -> "Documents"
                }
            } else null
            settings.unifiedSavingPath to subPath
        } else {
            val path = when {
                mimeType?.startsWith("image/") == true -> settings.picturesSavingPath
                mimeType?.startsWith("video/") == true -> settings.moviesSavingPath
                mimeType?.startsWith("audio/") == true -> settings.musicSavingPath
                else -> settings.documentsSavingPath
            }
            path to null
        }
    }

    fun buildOutputName(originalName: String, settings: AppSettings): String {
        return SanitizationUtils.generateOutputName(
            originalName = originalName,
            useRandomFileNames = settings.useRandomFileNames,
            prefix = settings.defaultPrefix,
            suffix = settings.defaultSuffix
        )
    }

    fun clearProcessedFiles() {
        val files = _processedFiles.value
        _processedFiles.value = emptyList()
        _workInfo.value = null

        viewModelScope.launch(Dispatchers.IO) {
            files.forEach { (_, file) -> runCatching { file.delete() } }
        }
    }

    fun clearMetadataAndPlansForUri(uri: Uri) {
        val currentMeta = _metadataPreview.value.toMutableMap()
        currentMeta.remove(uri)
        _metadataPreview.value = currentMeta

        val currentPlans = _replacementPlans.value.toMutableMap()
        currentPlans.remove(uri)
        _replacementPlans.value = currentPlans

        val currentDiff = _changePreview.value.toMutableMap()
        currentDiff.remove(uri)
        _changePreview.value = currentDiff
    }

    fun clearAllMetadataAndPlans() {
        _metadataPreview.value = emptyMap()
        _changePreview.value = emptyMap()
        _replacementPlans.value = emptyMap()
        _selectedMode.value = null
    }

    fun clearMessage() {
        _message.value = null
    }

    fun setMessage(msg: String) {
        _message.value = msg
    }

    fun setProcessedFilesDirectly(files: List<Pair<SelectedFile, File>>) {
        _processedFiles.value = files
    }

    fun setReplacementPlansDirectly(plans: Map<Uri, MetadataReplacementPlan>) {
        _replacementPlans.value = plans
    }
}
