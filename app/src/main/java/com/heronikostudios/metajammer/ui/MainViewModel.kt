package com.heronikostudios.metajammer.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.heronikostudios.metajammer.data.FileRepository
import com.heronikostudios.metajammer.data.HistoryRepository
import com.heronikostudios.metajammer.data.MetadataRepository
import com.heronikostudios.metajammer.data.SettingsRepository
import com.heronikostudios.metajammer.domain.model.*
import com.heronikostudios.metajammer.domain.usecase.ProcessFileUseCase
import com.heronikostudios.metajammer.domain.usecase.SaveFileUseCase
import com.heronikostudios.metajammer.ui.history.HistoryViewModel
import com.heronikostudios.metajammer.ui.home.HomeViewModel
import com.heronikostudios.metajammer.ui.processing.ProcessingViewModel
import com.heronikostudios.metajammer.ui.quickscrub.QuickScrubHandler
import com.heronikostudios.metajammer.ui.settings.SettingsViewModel
import com.heronikostudios.metajammer.worker.CleanupWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Coordinating ViewModel that composes domain-specific ViewModels:
 * - [HomeViewModel]: File selection and SAF ingestion
 * - [ProcessingViewModel]: Metadata inspection, poisoning plans, diff preview, processing
 * - [SettingsViewModel]: Preferences, persistable folder permissions, language
 * - [HistoryViewModel]: Processed files log observation and re-sharing
 * - [QuickScrubHandler]: Background automated scrub-and-share flow
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = getApplication<Application>().applicationContext
    private val fileRepository = FileRepository(appContext)
    private val metadataRepository = MetadataRepository(fileRepository)
    private val settingsRepository = SettingsRepository(appContext)
    private val historyRepository = HistoryRepository(appContext)
    private val workManager = WorkManager.getInstance(appContext)
    private val processFileUseCase = ProcessFileUseCase(metadataRepository)
    private val saveFileUseCase = SaveFileUseCase(fileRepository)

    val homeViewModel = HomeViewModel(fileRepository)
    val settingsViewModel = SettingsViewModel(settingsRepository, fileRepository, appContext.contentResolver)
    val processingViewModel = ProcessingViewModel(
        metadataRepository = metadataRepository,
        fileRepository = fileRepository,
        settingsRepository = settingsRepository,
        processFileUseCase = processFileUseCase,
        saveFileUseCase = saveFileUseCase,
        workManager = workManager,
        cacheDir = appContext.cacheDir
    )
    val historyViewModel = HistoryViewModel(historyRepository)
    val quickScrubHandler = QuickScrubHandler(
        metadataRepository = metadataRepository,
        processFileUseCase = processFileUseCase,
        saveFileUseCase = saveFileUseCase,
        settingsRepository = settingsRepository,
        fileRepository = fileRepository,
        workManager = workManager,
        cacheDir = appContext.cacheDir
    )

    private var isQuickScrubActive = false

    // Home state
    val selectedFiles: StateFlow<List<SelectedFile>> = homeViewModel.selectedFiles

    // Processing state
    val metadataPreview: StateFlow<Map<Uri, List<MetadataEntry>>> = processingViewModel.metadataPreview
    val changePreview: StateFlow<Map<Uri, List<MetadataEntry>>> = processingViewModel.changePreview
    val replacementPlans: StateFlow<Map<Uri, MetadataReplacementPlan>> = processingViewModel.replacementPlans
    val selectedMode: StateFlow<ProcessingMode?> = processingViewModel.selectedMode
    val processedFiles: StateFlow<List<Pair<SelectedFile, File>>> = processingViewModel.processedFiles
    val processing: StateFlow<Boolean> = processingViewModel.processing
    val workInfo: StateFlow<WorkInfo?> = processingViewModel.workInfo

    // Settings state
    val appSettings: StateFlow<AppSettings> = settingsViewModel.appSettings
    val settingsInitialized: StateFlow<Boolean> = settingsViewModel.settingsInitialized

    // History state
    val processedFilesHistory: StateFlow<List<ProcessedFileLog>> = historyViewModel.processedFilesHistory

    // Unified message state
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            fileRepository.clearCache()
        }

        // Bridge messages from child ViewModels
        viewModelScope.launch {
            processingViewModel.message.collect { msg ->
                if (msg != null) _message.value = msg
            }
        }
        viewModelScope.launch {
            settingsViewModel.message.collect { msg ->
                if (msg != null) _message.value = msg
            }
        }
        viewModelScope.launch {
            historyViewModel.message.collect { msg ->
                if (msg != null) _message.value = msg
            }
        }

        // Listen for setting changes that invalidate preview or cache
        settingsViewModel.observeSettings { oldSettings, newSettings ->
            if (newSettings.keepImageOrientation != oldSettings.keepImageOrientation ||
                newSettings.thumbnailHandling != oldSettings.thumbnailHandling ||
                newSettings.useNearbyScramble != oldSettings.useNearbyScramble
            ) {
                processingViewModel.clearProcessedFiles()
                if (processingViewModel.selectedMode.value != null) {
                    processingViewModel.generateChangePreview(homeViewModel.selectedFiles.value, newSettings)
                }
            }
        }
    }

    // --- Home / File Selection Actions ---

    fun setIncomingUris(uris: List<Uri>) {
        viewModelScope.launch {
            setIncomingUrisSuspend(uris)
        }
    }

    suspend fun setIncomingUrisSuspend(uris: List<Uri>, loadMetadata: Boolean = true) {
        clearTempFiles()
        processingViewModel.clearAllMetadataAndPlans()
        val files = homeViewModel.setIncomingUrisSuspend(uris)
        if (loadMetadata) {
            processingViewModel.loadMetadataPreview(files)
        }
    }

    fun removeFileFromSelection(file: SelectedFile) {
        if (homeViewModel.removeFileFromSelection(file)) {
            processingViewModel.clearMetadataAndPlansForUri(file.uri)
        }
    }

    fun clearSelection() {
        homeViewModel.clearSelection()
        processingViewModel.clearAllMetadataAndPlans()
        clearTempFiles()
    }

    // --- Processing Actions ---

    fun setProcessingMode(mode: ProcessingMode) {
        processingViewModel.setProcessingMode(mode, homeViewModel.selectedFiles.value, settingsViewModel.appSettings.value)
    }

    fun updatePlanLocation(uri: Uri, latitude: Double, longitude: Double) {
        processingViewModel.updatePlanLocation(uri, latitude, longitude, homeViewModel.selectedFiles.value, settingsViewModel.appSettings.value)
    }

    fun regeneratePoisonPlans() {
        processingViewModel.regeneratePoisonPlans(homeViewModel.selectedFiles.value, settingsViewModel.appSettings.value)
    }

    fun processFiles(onSuccess: (() -> Unit)? = null) {
        processingViewModel.processFiles(homeViewModel.selectedFiles.value, settingsViewModel.appSettings.value, onSuccess)
    }

    suspend fun saveProcessedFilesToDefault(): List<Uri> {
        return processingViewModel.saveProcessedFilesToDefault(settingsViewModel.appSettings.value)
    }

    suspend fun saveProcessedFilesToCustom(treeUri: Uri): List<Uri> {
        return processingViewModel.saveProcessedFilesToCustom(treeUri, settingsViewModel.appSettings.value)
    }

    suspend fun getProcessedFilesForSharing(): List<File> {
        return processingViewModel.getProcessedFilesForSharing(settingsViewModel.appSettings.value)
    }

    // --- Quick Scrub Actions ---

    suspend fun autoHandleSharedInput(onShareFilesReady: (List<File>, String?) -> Unit) {
        isQuickScrubActive = true
        quickScrubHandler.executeQuickScrub(
            files = homeViewModel.selectedFiles.value,
            appSettings = settingsViewModel.appSettings.value,
            onShareFilesReady = onShareFilesReady,
            onStatusMessage = { _message.value = it }
        )
    }

    // --- Settings Actions ---

    fun setLanguage(language: AppLanguage) = settingsViewModel.setLanguage(language)
    fun setNightMode(mode: NightModeSetting) = settingsViewModel.setNightMode(mode)
    fun setOledMode(enabled: Boolean) = settingsViewModel.setOledMode(enabled)
    fun setUseDynamicColor(enabled: Boolean) = settingsViewModel.setUseDynamicColor(enabled)
    fun setKeepImageOrientation(enabled: Boolean) = settingsViewModel.setKeepImageOrientation(enabled)
    fun setThumbnailHandling(handling: ThumbnailHandling) = settingsViewModel.setThumbnailHandling(handling)
    fun setUseNearbyScramble(enabled: Boolean) = settingsViewModel.setUseNearbyScramble(enabled)
    fun setAutoHandleSharedFiles(enabled: Boolean) = settingsViewModel.setAutoHandleSharedFiles(enabled)
    fun setSharedFilesProcessingMode(mode: ProcessingMode) = settingsViewModel.setSharedFilesProcessingMode(mode)
    fun setSharedFilesOutputAction(action: SharedInputOutputAction) = settingsViewModel.setSharedFilesOutputAction(action)
    fun setFolderStructure(structure: FolderStructure) = settingsViewModel.setFolderStructure(structure)
    fun setUseSubfoldersInUnified(enabled: Boolean) = settingsViewModel.setUseSubfoldersInUnified(enabled)
    fun setUseRandomFileNames(enabled: Boolean) = settingsViewModel.setUseRandomFileNames(enabled)
    fun setDefaultPrefix(value: String) = settingsViewModel.setDefaultPrefix(value)
    fun setDefaultSuffix(value: String) = settingsViewModel.setDefaultSuffix(value)
    fun setShareResultAsDefault(enabled: Boolean) = settingsViewModel.setShareResultAsDefault(enabled)
    fun setAllowInternetForMap(allowed: Boolean) = settingsViewModel.setAllowInternetForMap(allowed)
    fun setOnboardingCompleted(completed: Boolean) = settingsViewModel.setOnboardingCompleted(completed)
    fun setEnableProcessingHistory(enabled: Boolean) = settingsViewModel.setEnableProcessingHistory(enabled)
    fun setHistoryRetentionPolicy(policy: HistoryRetentionPolicy) = settingsViewModel.setHistoryRetentionPolicy(policy)
    fun setShowHistoryShortcut(show: Boolean) = settingsViewModel.setShowHistoryShortcut(show)

    fun persistAndSetUnifiedSavingPath(uri: Uri?) = settingsViewModel.persistAndSetUnifiedSavingPath(uri)
    fun persistAndSetPicturesSavingPath(uri: Uri?) = settingsViewModel.persistAndSetPicturesSavingPath(uri)
    fun persistAndSetMusicSavingPath(uri: Uri?) = settingsViewModel.persistAndSetMusicSavingPath(uri)
    fun persistAndSetMoviesSavingPath(uri: Uri?) = settingsViewModel.persistAndSetMoviesSavingPath(uri)
    fun persistAndSetDocumentsSavingPath(uri: Uri?) = settingsViewModel.persistAndSetDocumentsSavingPath(uri)
    fun persistAndSetSharedFilesCustomPath(uri: Uri?) = settingsViewModel.persistAndSetSharedFilesCustomPath(uri)

    // --- History Actions ---

    fun clearProcessedFilesHistory() {
        historyViewModel.clearProcessedFilesHistory(appContext.getString(com.heronikostudios.metajammer.R.string.setting_history_cleared))
    }

    fun shareHistoryFile(context: Context, log: ProcessedFileLog) {
        historyViewModel.shareHistoryFile(context, log)
    }

    // --- Cleanup & Messages ---

    fun clearMessage() {
        _message.value = null
        processingViewModel.clearMessage()
        settingsViewModel.clearMessage()
        historyViewModel.clearMessage()
    }

    fun clearTempFiles() {
        processingViewModel.clearProcessedFiles()
        viewModelScope.launch(Dispatchers.IO) {
            fileRepository.clearCache()
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (isQuickScrubActive) {
            val cleanupRequest = OneTimeWorkRequestBuilder<CleanupWorker>()
                .setInitialDelay(5, TimeUnit.MINUTES)
                .build()
            workManager.enqueue(cleanupRequest)
            processingViewModel.clearProcessedFiles()
        } else {
            clearTempFiles()
        }
    }
}
