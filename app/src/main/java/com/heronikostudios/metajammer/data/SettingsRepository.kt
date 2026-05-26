package com.heronikostudios.metajammer.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.heronikostudios.metajammer.domain.model.PostProcessAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "meta_jammer_settings")

class SettingsRepository(private val context: Context) {

    companion object {
        private val DEFAULT_POST_ACTION = stringPreferencesKey("default_post_action")
    }

    val defaultPostActionFlow: Flow<PostProcessAction> =
        context.dataStore.data.map { preferences ->
            preferences[DEFAULT_POST_ACTION]
                ?.let { saved -> runCatching { PostProcessAction.valueOf(saved) }.getOrNull() }
                ?: PostProcessAction.SAVE_DEFAULT
        }

    suspend fun setDefaultPostAction(action: PostProcessAction) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_POST_ACTION] = action.name
        }
    }
}
