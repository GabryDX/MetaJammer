package com.heronikostudios.metajammer.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProcessedFileDao {

    @Query("SELECT * FROM processed_files ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<ProcessedFileEntity>>

    @Query("SELECT * FROM processed_files ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentFlow(limit: Int): Flow<List<ProcessedFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entity: ProcessedFileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(entities: List<ProcessedFileEntity>): List<Long>

    @Query("DELETE FROM processed_files WHERE uri = :uri")
    fun deleteByUri(uri: String): Int

    @Query("DELETE FROM processed_files")
    fun clearAll(): Int

    @Query("DELETE FROM processed_files WHERE timestamp < :cutoffTimestamp")
    fun pruneOlderThan(cutoffTimestamp: Long): Int

    @Query("DELETE FROM processed_files WHERE id NOT IN (SELECT id FROM processed_files ORDER BY timestamp DESC LIMIT :maxItems)")
    fun pruneToMaxItems(maxItems: Int): Int

    @Query("SELECT COUNT(*) FROM processed_files")
    fun getCount(): Int
}
