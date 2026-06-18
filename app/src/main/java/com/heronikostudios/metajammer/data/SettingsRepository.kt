package com.heronikostudios.metajammer.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.heronikostudios.metajammer.domain.model.AppLanguage
import com.heronikostudios.metajammer.domain.model.FolderStructure
import com.heronikostudios.metajammer.domain.model.NightModeSetting
import com.heronikostudios.metajammer.domain.model.ProcessingMode
import com.heronikostudios.metajammer.domain.model.SharedInputOutputAction
import com.heronikostudios.metajammer.domain.model.ThumbnailHandling
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "meta_jammer_settings")

class SettingsRepository(private val context: Context) {

    companion object {
        private val USE_RANDOM_FILE_NAMES = booleanPreferencesKey("use_random_file_names")
        private val FOLDER_STRUCTURE = stringPreferencesKey("folder_structure")
        private val USE_SUBFOLDERS_IN_UNIFIED = booleanPreferencesKey("use_subfolders_in_unified")
        private val UNIFIED_SAVING_PATH = stringPreferencesKey("unified_saving_path")
        private val PICTURES_SAVING_PATH = stringPreferencesKey("pictures_saving_path")
        private val MUSIC_SAVING_PATH = stringPreferencesKey("music_saving_path")
        private val MOVIES_SAVING_PATH = stringPreferencesKey("movies_saving_path")
        private val DOCUMENTS_SAVING_PATH = stringPreferencesKey("documents_saving_path")
        private val DEFAULT_SAVING_PATH = stringPreferencesKey("default_saving_path") // Deprecated, kept for migration
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
    }

    val useRandomFileNamesFlow: Flow<Boolean> =
        context.dataStore.data.map { it[USE_RANDOM_FILE_NAMES] ?: false }

    suspend fun setUseRandomFileNames(enabled: Boolean) {
        context.dataStore.edit { it[USE_RANDOM_FILE_NAMES] = enabled }
    }

    val folderStructureFlow: Flow<FolderStructure> =
        context.dataStore.data.map { preferences ->
            preferences[FOLDER_STRUCTURE]?.let { runCatching { FolderStructure.valueOf(it) }.getOrNull() }
                ?: FolderStructure.SPLIT
        }

    suspend fun setFolderStructure(structure: FolderStructure) {
        context.dataStore.edit { it[FOLDER_STRUCTURE] = structure.name }
    }

    val useSubfoldersInUnifiedFlow: Flow<Boolean> =
        context.dataStore.data.map { it[USE_SUBFOLDERS_IN_UNIFIED] ?: true }

    suspend fun setUseSubfoldersInUnified(enabled: Boolean) {
        context.dataStore.edit { it[USE_SUBFOLDERS_IN_UNIFIED] = enabled }
    }

    val unifiedSavingPathFlow: Flow<String?> =
        context.dataStore.data.map { it[UNIFIED_SAVING_PATH] ?: it[DEFAULT_SAVING_PATH] ?: "Download/MetaJammer" }

    suspend fun setUnifiedSavingPath(uri: Uri?) {
        context.dataStore.edit { preferences ->
            preferences[UNIFIED_SAVING_PATH] = uri?.toString() ?: "Download/MetaJammer"
        }
    }

    val picturesSavingPathFlow: Flow<String?> =
        context.dataStore.data.map { it[PICTURES_SAVING_PATH] ?: it[DEFAULT_SAVING_PATH] ?: "Pictures/MetaJammer" }

    suspend fun setPicturesSavingPath(uri: Uri?) {
        context.dataStore.edit { preferences ->
            preferences[PICTURES_SAVING_PATH] = uri?.toString() ?: "Pictures/MetaJammer"
        }
    }

    val musicSavingPathFlow: Flow<String?> =
        context.dataStore.data.map { it[MUSIC_SAVING_PATH] ?: "Music/MetaJammer" }

    suspend fun setMusicSavingPath(uri: Uri?) {
        context.dataStore.edit { preferences ->
            preferences[MUSIC_SAVING_PATH] = uri?.toString() ?: "Music/MetaJammer"
        }
    }

    val moviesSavingPathFlow: Flow<String?> =
        context.dataStore.data.map { it[MOVIES_SAVING_PATH] ?: "Movies/MetaJammer" }

    suspend fun setMoviesSavingPath(uri: Uri?) {
        context.dataStore.edit { preferences ->
            preferences[MOVIES_SAVING_PATH] = uri?.toString() ?: "Movies/MetaJammer"
        }
    }

    val documentsSavingPathFlow: Flow<String?> =
        context.dataStore.data.map { it[DOCUMENTS_SAVING_PATH] ?: "Documents/MetaJammer" }

    suspend fun setDocumentsSavingPath(uri: Uri?) {
        context.dataStore.edit { preferences ->
            preferences[DOCUMENTS_SAVING_PATH] = uri?.toString() ?: "Documents/MetaJammer"
        }
    }

    val keepImageOrientationFlow: Flow<Boolean> =
        context.dataStore.data.map { it[KEEP_IMAGE_ORIENTATION] ?: true }

    suspend fun setKeepImageOrientation(enabled: Boolean) {
        context.dataStore.edit { it[KEEP_IMAGE_ORIENTATION] = enabled }
    }

    val shareResultAsDefaultFlow: Flow<Boolean> =
        context.dataStore.data.map { it[SHARE_RESULT_AS_DEFAULT] ?: false }

    suspend fun setShareResultAsDefault(enabled: Boolean) {
        context.dataStore.edit { it[SHARE_RESULT_AS_DEFAULT] = enabled }
    }

    val defaultPrefixFlow: Flow<String> =
        context.dataStore.data.map { it[DEFAULT_PREFIX] ?: "" }

    suspend fun setDefaultPrefix(value: String) {
        context.dataStore.edit { it[DEFAULT_PREFIX] = value }
    }

    val defaultSuffixFlow: Flow<String> =
        context.dataStore.data.map { it[DEFAULT_SUFFIX] ?: "_processed" }

    suspend fun setDefaultSuffix(value: String) {
        context.dataStore.edit { it[DEFAULT_SUFFIX] = value }
    }

    val nightModeFlow: Flow<NightModeSetting> =
        context.dataStore.data.map { preferences ->
            preferences[NIGHT_MODE]
                ?.let { runCatching { NightModeSetting.valueOf(it) }.getOrNull() }
                ?: NightModeSetting.AUTOMATIC
        }

    suspend fun setNightMode(mode: NightModeSetting) {
        context.dataStore.edit { it[NIGHT_MODE] = mode.name }
    }

    val oledModeFlow: Flow<Boolean> =
        context.dataStore.data.map { it[OLED_MODE] ?: false }

    suspend fun setOledMode(enabled: Boolean) {
        context.dataStore.edit { it[OLED_MODE] = enabled }
    }

    val autoHandleSharedFilesFlow: Flow<Boolean> =
        context.dataStore.data.map { it[AUTO_HANDLE_SHARED_FILES] ?: false }

    suspend fun setAutoHandleSharedFiles(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_HANDLE_SHARED_FILES] = enabled }
    }

    val sharedFilesProcessingModeFlow: Flow<ProcessingMode> =
        context.dataStore.data.map { preferences ->
            preferences[SHARED_FILES_PROCESSING_MODE]
                ?.let { runCatching { ProcessingMode.valueOf(it) }.getOrNull() }
                ?: ProcessingMode.REMOVE_METADATA
        }

    suspend fun setSharedFilesProcessingMode(mode: ProcessingMode) {
        context.dataStore.edit { it[SHARED_FILES_PROCESSING_MODE] = mode.name }
    }

    val sharedFilesOutputActionFlow: Flow<SharedInputOutputAction> =
        context.dataStore.data.map { preferences ->
            preferences[SHARED_FILES_OUTPUT_ACTION]
                ?.let { runCatching { SharedInputOutputAction.valueOf(it) }.getOrNull() }
                ?: SharedInputOutputAction.SAVE_TO_DEFAULT_FOLDER
        }

    suspend fun setSharedFilesOutputAction(action: SharedInputOutputAction) {
        context.dataStore.edit { it[SHARED_FILES_OUTPUT_ACTION] = action.name }
    }

    val sharedFilesCustomPathFlow: Flow<String?> =
        context.dataStore.data.map { it[SHARED_FILES_CUSTOM_PATH] }

    suspend fun setSharedFilesCustomPath(uri: Uri?) {
        context.dataStore.edit { preferences ->
            if (uri == null) {
                preferences.remove(SHARED_FILES_CUSTOM_PATH)
            } else {
                preferences[SHARED_FILES_CUSTOM_PATH] = uri.toString()
            }
        }
    }

    val thumbnailHandlingFlow: Flow<ThumbnailHandling> =
        context.dataStore.data.map { preferences ->
            preferences[THUMBNAIL_HANDLING]
                ?.let { runCatching { ThumbnailHandling.valueOf(it) }.getOrNull() }
                ?: ThumbnailHandling.REMOVE
        }

    suspend fun setThumbnailHandling(handling: ThumbnailHandling) {
        context.dataStore.edit { it[THUMBNAIL_HANDLING] = handling.name }
    }

    val allowInternetForMapFlow: Flow<Boolean> =
        context.dataStore.data.map { it[ALLOW_INTERNET_FOR_MAP] ?: false }

    suspend fun setAllowInternetForMap(allowed: Boolean) {
        context.dataStore.edit { it[ALLOW_INTERNET_FOR_MAP] = allowed }
    }

    val useNearbyScrambleFlow: Flow<Boolean> =
        context.dataStore.data.map { it[USE_NEARBY_SCRAMBLE] ?: false }

    suspend fun setUseNearbyScramble(enabled: Boolean) {
        context.dataStore.edit { it[USE_NEARBY_SCRAMBLE] = enabled }
    }

    val languageFlow: Flow<AppLanguage> =
        context.dataStore.data.map { preferences ->
            preferences[LANGUAGE]
                ?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                ?: AppLanguage.SYSTEM
        }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[LANGUAGE] = language.name }
    }
}
