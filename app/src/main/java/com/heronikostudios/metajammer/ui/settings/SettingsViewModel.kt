package com.heronikostudios.metajammer.ui.settings

import android.app.Application
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heronikostudios.metajammer.data.FileRepository
import com.heronikostudios.metajammer.data.SettingsRepository
import com.heronikostudios.metajammer.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * ViewModel managing user preferences, settings persistence via DataStore,
 * folder tree persistable permissions, and cache maintenance.
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val fileRepository: FileRepository,
    private val contentResolver: ContentResolver? = null
) : ViewModel() {

    constructor(application: Application) : this(
        settingsRepository = SettingsRepository(application.applicationContext),
        fileRepository = FileRepository(application.applicationContext),
        contentResolver = application.applicationContext.contentResolver
    )

    private val _appSettings = MutableStateFlow(AppSettings())
    val appSettings: StateFlow<AppSettings> = _appSettings.asStateFlow()

    private val _settingsInitialized = MutableStateFlow(false)
    val settingsInitialized: StateFlow<Boolean> = _settingsInitialized.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.performMaintenance()
        }
        observeSettings()
    }

    fun observeSettings(onSettingsChanged: ((AppSettings, AppSettings) -> Unit)? = null) {
        viewModelScope.launch {
            var firstEmission = true
            settingsRepository.appSettingsFlow.collect { settings ->
                val oldSettings = _appSettings.value
                _appSettings.value = settings

                if (firstEmission) {
                    _settingsInitialized.value = true
                    firstEmission = false
                }

                if (settings.language != oldSettings.language) {
                    applyLanguage(settings.language)
                }

                onSettingsChanged?.invoke(oldSettings, settings)
            }
        }
    }

    private fun applyLanguage(language: AppLanguage) {
        Timber.d("Applying language: $language (code: ${language.code})")
        val appLocale: LocaleListCompat = if (language == AppLanguage.SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language.code)
        }

        val currentLocales = AppCompatDelegate.getApplicationLocales()
        if (currentLocales != appLocale) {
            Timber.d("Setting application locales to $appLocale")
            AppCompatDelegate.setApplicationLocales(appLocale)
        } else {
            Timber.d("Locales already set to $appLocale")
        }
    }

    fun persistAndSetUnifiedSavingPath(uri: Uri?) {
        if (uri == null) {
            viewModelScope.launch {
                settingsRepository.setUnifiedSavingPath(null)
                _message.value = "Unified saving path reset to Download/MetaJammer"
            }
            return
        }
        takePersistablePermission(uri)
        viewModelScope.launch {
            settingsRepository.setUnifiedSavingPath(uri)
            _message.value = "Unified saving path updated"
        }
    }

    fun persistAndSetPicturesSavingPath(uri: Uri?) {
        if (uri == null) {
            viewModelScope.launch {
                settingsRepository.setPicturesSavingPath(null)
                _message.value = "Pictures saving path reset to Pictures/MetaJammer"
            }
            return
        }
        takePersistablePermission(uri)
        viewModelScope.launch {
            settingsRepository.setPicturesSavingPath(uri)
            _message.value = "Pictures saving path updated"
        }
    }

    fun persistAndSetMusicSavingPath(uri: Uri?) {
        if (uri == null) {
            viewModelScope.launch {
                settingsRepository.setMusicSavingPath(null)
                _message.value = "Music saving path reset to Music/MetaJammer"
            }
            return
        }
        takePersistablePermission(uri)
        viewModelScope.launch {
            settingsRepository.setMusicSavingPath(uri)
            _message.value = "Music saving path updated"
        }
    }

    fun persistAndSetMoviesSavingPath(uri: Uri?) {
        if (uri == null) {
            viewModelScope.launch {
                settingsRepository.setMoviesSavingPath(null)
                _message.value = "Movies saving path reset to Movies/MetaJammer"
            }
            return
        }
        takePersistablePermission(uri)
        viewModelScope.launch {
            settingsRepository.setMoviesSavingPath(uri)
            _message.value = "Movies saving path updated"
        }
    }

    fun persistAndSetDocumentsSavingPath(uri: Uri?) {
        if (uri == null) {
            viewModelScope.launch {
                settingsRepository.setDocumentsSavingPath(null)
                _message.value = "Documents saving path reset to Documents/MetaJammer"
            }
            return
        }
        takePersistablePermission(uri)
        viewModelScope.launch {
            settingsRepository.setDocumentsSavingPath(uri)
            _message.value = "Documents saving path updated"
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
        takePersistablePermission(uri)
        viewModelScope.launch {
            settingsRepository.setSharedFilesCustomPath(uri)
            _message.value = "Shared-files folder updated"
        }
    }

    private fun takePersistablePermission(uri: Uri) {
        if (uri.toString().startsWith("content://")) {
            runCatching {
                contentResolver?.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
    }

    fun setUseRandomFileNames(enabled: Boolean) = launchSettingUpdate {
        settingsRepository.setUseRandomFileNames(enabled)
    }

    fun setFolderStructure(structure: FolderStructure) = launchSettingUpdate {
        settingsRepository.setFolderStructure(structure)
    }

    fun setUseSubfoldersInUnified(enabled: Boolean) = launchSettingUpdate {
        settingsRepository.setUseSubfoldersInUnified(enabled)
    }

    fun setKeepImageOrientation(enabled: Boolean) = launchSettingUpdate {
        settingsRepository.setKeepImageOrientation(enabled)
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

    fun setUseNearbyScramble(enabled: Boolean) = launchSettingUpdate {
        settingsRepository.setUseNearbyScramble(enabled)
    }

    fun setLanguage(language: AppLanguage) = launchSettingUpdate {
        settingsRepository.setLanguage(language)
    }

    fun setUseDynamicColor(enabled: Boolean) = launchSettingUpdate {
        settingsRepository.setUseDynamicColor(enabled)
    }

    fun setOnboardingCompleted(completed: Boolean) = launchSettingUpdate {
        settingsRepository.setIsOnboardingCompleted(completed)
    }

    fun setEnableProcessingHistory(enabled: Boolean) = launchSettingUpdate {
        settingsRepository.setEnableProcessingHistory(enabled)
    }

    fun setHistoryRetentionPolicy(policy: HistoryRetentionPolicy) = launchSettingUpdate {
        settingsRepository.setHistoryRetentionPolicy(policy)
    }

    fun setShowHistoryShortcut(show: Boolean) = launchSettingUpdate {
        settingsRepository.setShowHistoryShortcut(show)
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            fileRepository.clearCache()
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun setMessage(msg: String) {
        _message.value = msg
    }

    private fun launchSettingUpdate(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
