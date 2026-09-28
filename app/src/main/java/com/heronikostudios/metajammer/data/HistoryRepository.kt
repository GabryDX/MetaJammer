package com.heronikostudios.metajammer.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.heronikostudios.metajammer.data.db.AppDatabase
import com.heronikostudios.metajammer.data.db.ProcessedFileDao
import com.heronikostudios.metajammer.data.db.ProcessedFileEntity
import com.heronikostudios.metajammer.domain.model.HistoryRetentionPolicy
import com.heronikostudios.metajammer.domain.model.ProcessedFileLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber

open class HistoryRepository(
    private val processedFileDao: ProcessedFileDao,
    private val context: Context? = null
) {

    constructor(context: Context) : this(
        processedFileDao = AppDatabase.getInstance(context).processedFileDao(),
        context = context
    )

    val processedFilesFlow: Flow<List<ProcessedFileLog>> = processedFileDao.getAllFlow().map { entities ->
        entities.map { it.toDomainModel() }
    }

    suspend fun logProcessedFile(
        log: ProcessedFileLog,
        retentionPolicy: HistoryRetentionPolicy = HistoryRetentionPolicy.KEEP_100_ITEMS
    ) = withContext(Dispatchers.IO) {
        val entity = ProcessedFileEntity.fromDomainModel(log)
        processedFileDao.insert(entity)
        applyRetentionPolicy(retentionPolicy)
    }

    suspend fun clearHistory(): Int = withContext(Dispatchers.IO) {
        processedFileDao.clearAll()
    }

    suspend fun deleteByUri(uri: String): Int = withContext(Dispatchers.IO) {
        processedFileDao.deleteByUri(uri)
    }

    suspend fun performMaintenance(policy: HistoryRetentionPolicy) = withContext(Dispatchers.IO) {
        migrateLegacyDataStoreIfNeeded()
        applyRetentionPolicy(policy)
    }

    private suspend fun applyRetentionPolicy(policy: HistoryRetentionPolicy) {
        when (policy) {
            HistoryRetentionPolicy.CLEAR_ON_EXIT -> {
                processedFileDao.clearAll()
            }
            HistoryRetentionPolicy.CLEAR_AFTER_24_HOURS -> {
                val cutoff = System.currentTimeMillis() - (24L * 60 * 60 * 1000)
                processedFileDao.pruneOlderThan(cutoff)
            }
            HistoryRetentionPolicy.KEEP_100_ITEMS -> {
                processedFileDao.pruneToMaxItems(100)
            }
        }
    }

    /**
     * One-time migration: checks if legacy DataStore preferences contain JSON logs,
     * imports them into SQLite Room, and clears the legacy preference key.
     */
    private suspend fun migrateLegacyDataStoreIfNeeded() {
        val ctx = context ?: return
        runCatching {
            val legacyKey = stringSetPreferencesKey("processed_files_log")
            ctx.dataStore.edit { preferences ->
                val legacySet = preferences[legacyKey]
                if (!legacySet.isNullOrEmpty()) {
                    Timber.d("Migrating %d legacy history entries from DataStore to Room", legacySet.size)
                    val entities = legacySet.mapNotNull { json ->
                        runCatching {
                            val log = Json.decodeFromString<ProcessedFileLog>(json)
                            ProcessedFileEntity.fromDomainModel(log)
                        }.getOrNull()
                    }
                    if (entities.isNotEmpty()) {
                        processedFileDao.insertAll(entities)
                    }
                    preferences.remove(legacyKey)
                }
            }
        }.onFailure {
            Timber.w(it, "Failed to migrate legacy history from DataStore")
        }
    }
}
