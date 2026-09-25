package com.heronikostudios.metajammer.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.heronikostudios.metajammer.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

internal val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

open class SettingsRepository(private val context: Context) {

    companion object {
        private val USE_RANDOM_FILE_NAMES = booleanPreferencesKey("use_random_file_names")
        private val FOLDER_STRUCTURE = stringPreferencesKey("folder_structure")
        private val USE_SUBFOLDERS_IN_UNIFIED = booleanPreferencesKey("use_subfolders_in_unified")
        private val UNIFIED_SAVING_PATH = stringPreferencesKey("unified_saving_path")
        private val PICTURES_SAVING_PATH = stringPreferencesKey("pictures_saving_path")
        private val MUSIC_SAVING_PATH = stringPreferencesKey("music_saving_path")
        private val MOVIES_SAVING_PATH = stringPreferencesKey("movies_saving_path")
        private val DOCUMENTS_SAVING_PATH = stringPreferencesKey("documents_saving_path")
        private val DEFAULT_SAVING_PATH = stringPreferencesKey("default_saving_path")
        private val KEEP_IMAGE_ORIENTATION = booleanPreferencesKey("keep_image_orientation")
        private val SHARE_RESULT_AS_DEFAULT = booleanPreferencesKey("share_result_as_default")
        private val DEFAULT_PREFIX = stringPreferencesKey("default_prefix")
        private val DEFAULT_SUFFIX = stringPreferencesKey("default_suffix")
        private val NIGHT_MODE = stringPreferencesKey("night_mode")
        private val OLED_MODE = booleanPreferencesKey("oled_mode")
        private val AUTO_HANDLE_SHARED_FILES = booleanPreferencesKey("auto_handle_shared_files")
        private val SHARED_FILES_PROCESSING_MODE = stringPreferencesKey("shared_files_processing_mode")
        private val SHARED_FILES_OUTPUT_ACTION = stringPreferencesKey("shared_files_output_action")
        private val SHARED_FILES_CUSTOM_PATH = stringPreferencesKey("shared_files_custom_path")
        private val THUMBNAIL_HANDLING = stringPreferencesKey("thumbnail_handling")
        private val ALLOW_INTERNET_FOR_MAP = booleanPreferencesKey("allow_internet_for_map")
        private val USE_NEARBY_SCRAMBLE = booleanPreferencesKey("use_nearby_scramble")
        private val LANGUAGE = stringPreferencesKey("language")
        private val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        private val IS_ONBOARDING_COMPLETED = booleanPreferencesKey("is_onboarding_completed")
        private val ENABLE_PROCESSING_HISTORY = booleanPreferencesKey("enable_processing_history")
        private val HISTORY_RETENTION_POLICY = stringPreferencesKey("history_retention_policy")
        private val SHOW_HISTORY_SHORTCUT = booleanPreferencesKey("show_history_shortcut")
        private val POISONING_PROFILE = stringPreferencesKey("poisoning_profile")
        private val LOCATION_PRESET = stringPreferencesKey("location_preset")
    }

    open suspend fun setUseRandomFileNames(enabled: Boolean) {
        context.dataStore.edit { it[USE_RANDOM_FILE_NAMES] = enabled }
    }

    suspend fun setFolderStructure(structure: FolderStructure) {
        context.dataStore.edit { it[FOLDER_STRUCTURE] = structure.name }
    }

    suspend fun setUseSubfoldersInUnified(enabled: Boolean) {
        context.dataStore.edit { it[USE_SUBFOLDERS_IN_UNIFIED] = enabled }
    }

    suspend fun setUnifiedSavingPath(uri: Uri?) {
        context.dataStore.edit { preferences ->
            if (uri != null) preferences[UNIFIED_SAVING_PATH] = uri.toString()
            else preferences.remove(UNIFIED_SAVING_PATH)
        }
    }

    suspend fun setPicturesSavingPath(uri: Uri?) {
        context.dataStore.edit { preferences ->
            if (uri != null) preferences[PICTURES_SAVING_PATH] = uri.toString()
            else preferences.remove(PICTURES_SAVING_PATH)
        }
    }

    suspend fun setMusicSavingPath(uri: Uri?) {
        context.dataStore.edit { preferences ->
            if (uri != null) preferences[MUSIC_SAVING_PATH] = uri.toString()
            else preferences.remove(MUSIC_SAVING_PATH)
        }
    }

    suspend fun setMoviesSavingPath(uri: Uri?) {
        context.dataStore.edit { preferences ->
            if (uri != null) preferences[MOVIES_SAVING_PATH] = uri.toString()
            else preferences.remove(MOVIES_SAVING_PATH)
        }
    }

    suspend fun setDocumentsSavingPath(uri: Uri?) {
        context.dataStore.edit { preferences ->
            if (uri != null) preferences[DOCUMENTS_SAVING_PATH] = uri.toString()
            else preferences.remove(DOCUMENTS_SAVING_PATH)
        }
    }

    open suspend fun setKeepImageOrientation(enabled: Boolean) {
        context.dataStore.edit { it[KEEP_IMAGE_ORIENTATION] = enabled }
    }

    suspend fun setShareResultAsDefault(enabled: Boolean) {
        context.dataStore.edit { it[SHARE_RESULT_AS_DEFAULT] = enabled }
    }

    open suspend fun setDefaultPrefix(prefix: String) {
        context.dataStore.edit { it[DEFAULT_PREFIX] = prefix }
    }

    open suspend fun setDefaultSuffix(suffix: String) {
        context.dataStore.edit { it[DEFAULT_SUFFIX] = suffix }
    }

    suspend fun setNightMode(mode: NightModeSetting) {
        context.dataStore.edit { it[NIGHT_MODE] = mode.name }
    }

    suspend fun setOledMode(enabled: Boolean) {
        context.dataStore.edit { it[OLED_MODE] = enabled }
    }

    suspend fun setAutoHandleSharedFiles(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_HANDLE_SHARED_FILES] = enabled }
    }

    suspend fun setSharedFilesProcessingMode(mode: ProcessingMode) {
        context.dataStore.edit { it[SHARED_FILES_PROCESSING_MODE] = mode.name }
    }

    suspend fun setSharedFilesOutputAction(action: SharedInputOutputAction) {
        context.dataStore.edit { it[SHARED_FILES_OUTPUT_ACTION] = action.name }
    }

    suspend fun setSharedFilesCustomPath(uri: Uri?) {
        context.dataStore.edit { preferences ->
            if (uri != null) {
                preferences[SHARED_FILES_CUSTOM_PATH] = uri.toString()
            } else {
                preferences.remove(SHARED_FILES_CUSTOM_PATH)
            }
        }
    }

    open suspend fun setThumbnailHandling(handling: ThumbnailHandling) {
        context.dataStore.edit { it[THUMBNAIL_HANDLING] = handling.name }
    }

    suspend fun setAllowInternetForMap(allowed: Boolean) {
        context.dataStore.edit { it[ALLOW_INTERNET_FOR_MAP] = allowed }
    }

    suspend fun setUseNearbyScramble(enabled: Boolean) {
        context.dataStore.edit { it[USE_NEARBY_SCRAMBLE] = enabled }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[LANGUAGE] = language.name }
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[USE_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setIsOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[IS_ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setEnableProcessingHistory(enabled: Boolean) {
        context.dataStore.edit { it[ENABLE_PROCESSING_HISTORY] = enabled }
    }

    suspend fun setHistoryRetentionPolicy(policy: HistoryRetentionPolicy) {
        context.dataStore.edit { it[HISTORY_RETENTION_POLICY] = policy.name }
    }

    suspend fun setShowHistoryShortcut(show: Boolean) {
        context.dataStore.edit { it[SHOW_HISTORY_SHORTCUT] = show }
    }

    open suspend fun setPoisoningProfile(profile: PoisoningProfile) {
        context.dataStore.edit { it[POISONING_PROFILE] = profile.name }
    }

    open suspend fun setLocationPreset(preset: LocationPreset) {
        context.dataStore.edit { it[LOCATION_PRESET] = preset.name }
    }

    private val historyRepository by lazy { HistoryRepository(context) }

    suspend fun logProcessedFile(log: ProcessedFileLog) {
        val currentSettings = appSettingsFlow.first()
        if (!currentSettings.enableProcessingHistory) return
        historyRepository.logProcessedFile(log, currentSettings.historyRetentionPolicy)
    }

    suspend fun clearProcessedFilesLog() {
        historyRepository.clearHistory()
    }

    open suspend fun performMaintenance() {
        val currentSettings = appSettingsFlow.first()
        historyRepository.performMaintenance(currentSettings.historyRetentionPolicy)
    }

    open val appSettingsFlow: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        AppSettings(
            useRandomFileNames = preferences[USE_RANDOM_FILE_NAMES] ?: false,
            folderStructure = preferences[FOLDER_STRUCTURE]?.let { runCatching { FolderStructure.valueOf(it) }.getOrNull() } ?: FolderStructure.SPLIT,
            useSubfoldersInUnified = preferences[USE_SUBFOLDERS_IN_UNIFIED] ?: true,
            unifiedSavingPath = preferences[UNIFIED_SAVING_PATH] ?: preferences[DEFAULT_SAVING_PATH] ?: "Download/MetaJammer",
            picturesSavingPath = preferences[PICTURES_SAVING_PATH] ?: preferences[DEFAULT_SAVING_PATH] ?: "Pictures/MetaJammer",
            musicSavingPath = preferences[MUSIC_SAVING_PATH] ?: "Music/MetaJammer",
            moviesSavingPath = preferences[MOVIES_SAVING_PATH] ?: "Movies/MetaJammer",
            documentsSavingPath = preferences[DOCUMENTS_SAVING_PATH] ?: "Documents/MetaJammer",
            keepImageOrientation = preferences[KEEP_IMAGE_ORIENTATION] ?: true,
            shareResultAsDefault = preferences[SHARE_RESULT_AS_DEFAULT] ?: false,
            defaultPrefix = preferences[DEFAULT_PREFIX] ?: "",
            defaultSuffix = preferences[DEFAULT_SUFFIX] ?: "_processed",
            nightMode = preferences[NIGHT_MODE]?.let { runCatching { NightModeSetting.valueOf(it) }.getOrNull() } ?: NightModeSetting.AUTOMATIC,
            oledMode = preferences[OLED_MODE] ?: false,
            autoHandleSharedFiles = preferences[AUTO_HANDLE_SHARED_FILES] ?: false,
            sharedFilesProcessingMode = preferences[SHARED_FILES_PROCESSING_MODE]?.let { runCatching { ProcessingMode.valueOf(it) }.getOrNull() } ?: ProcessingMode.REMOVE_METADATA,
            sharedFilesOutputAction = preferences[SHARED_FILES_OUTPUT_ACTION]?.let { runCatching { SharedInputOutputAction.valueOf(it) }.getOrNull() } ?: SharedInputOutputAction.SHARE_TO_ANOTHER_APP,
            sharedFilesCustomPath = preferences[SHARED_FILES_CUSTOM_PATH],
            thumbnailHandling = preferences[THUMBNAIL_HANDLING]?.let { runCatching { ThumbnailHandling.valueOf(it) }.getOrNull() } ?: ThumbnailHandling.REMOVE,
            allowInternetForMap = preferences[ALLOW_INTERNET_FOR_MAP] ?: false,
            useNearbyScramble = preferences[USE_NEARBY_SCRAMBLE] ?: false,
            language = preferences[LANGUAGE]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() } ?: AppLanguage.SYSTEM,
            useDynamicColor = preferences[USE_DYNAMIC_COLOR] ?: true,
            isOnboardingCompleted = preferences[IS_ONBOARDING_COMPLETED] ?: false,
            enableProcessingHistory = preferences[ENABLE_PROCESSING_HISTORY] ?: false,
            historyRetentionPolicy = preferences[HISTORY_RETENTION_POLICY]?.let { runCatching { HistoryRetentionPolicy.valueOf(it) }.getOrNull() } ?: HistoryRetentionPolicy.KEEP_100_ITEMS,
            showHistoryShortcut = preferences[SHOW_HISTORY_SHORTCUT] ?: false,
            poisoningProfile = preferences[POISONING_PROFILE]?.let { runCatching { PoisoningProfile.valueOf(it) }.getOrNull() } ?: PoisoningProfile.RANDOM,
            locationPreset = preferences[LOCATION_PRESET]?.let { runCatching { LocationPreset.valueOf(it) }.getOrNull() } ?: LocationPreset.RANDOM
        )
    }

    val processedFilesLog: Flow<List<ProcessedFileLog>> = historyRepository.processedFilesFlow
}
