package com.heronikostudios.metajammer.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.heronikostudios.metajammer.domain.model.NightModeSetting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "meta_jammer_settings")

class SettingsRepository(private val context: Context) {

    companion object {
        private val USE_RANDOM_FILE_NAMES = booleanPreferencesKey("use_random_file_names")
        private val DEFAULT_SAVING_PATH = stringPreferencesKey("default_saving_path")
        private val AUTOMATIC_DELETION = booleanPreferencesKey("automatic_deletion")
        private val KEEP_IMAGE_ORIENTATION = booleanPreferencesKey("keep_image_orientation")
        private val SHARE_RESULT_AS_DEFAULT = booleanPreferencesKey("share_result_as_default")
        private val DEFAULT_PREFIX = stringPreferencesKey("default_prefix")
        private val DEFAULT_SUFFIX = stringPreferencesKey("default_suffix")
        private val NIGHT_MODE = stringPreferencesKey("night_mode")
        private val OLED_MODE = booleanPreferencesKey("oled_mode")
    }

    val useRandomFileNamesFlow: Flow<Boolean> =
        context.dataStore.data.map { it[USE_RANDOM_FILE_NAMES] ?: false }

    suspend fun setUseRandomFileNames(enabled: Boolean) {
        context.dataStore.edit { it[USE_RANDOM_FILE_NAMES] = enabled }
    }

    val defaultSavingPathFlow: Flow<String?> =
        context.dataStore.data.map { it[DEFAULT_SAVING_PATH] ?: "Pictures/MetaJammer" }

    suspend fun setDefaultSavingPath(uri: Uri?) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_SAVING_PATH] = uri?.toString() ?: "Pictures/MetaJammer"
        }
    }

    suspend fun setDefaultSavingPathString(path: String) {
        context.dataStore.edit { it[DEFAULT_SAVING_PATH] = path }
    }

    val automaticDeletionFlow: Flow<Boolean> =
        context.dataStore.data.map { it[AUTOMATIC_DELETION] ?: false }

    suspend fun setAutomaticDeletion(enabled: Boolean) {
        context.dataStore.edit { it[AUTOMATIC_DELETION] = enabled }
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
        context.dataStore.data.map { it[DEFAULT_SUFFIX] ?: "" }

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
}
