package com.heronikostudios.metajammer.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.heronikostudios.metajammer.domain.model.ProcessedFileLog

@Entity(
    tableName = "processed_files",
    indices = [Index(value = ["timestamp"])]
)
data class ProcessedFileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uri: String,
    val displayName: String,
    val mimeType: String?,
    val timestamp: Long,
    val sizeBytes: Long? = null
) {
    fun toDomainModel(): ProcessedFileLog = ProcessedFileLog(
        uri = uri,
        displayName = displayName,
        mimeType = mimeType,
        timestamp = timestamp,
        sizeBytes = sizeBytes
    )

    companion object {
        fun fromDomainModel(log: ProcessedFileLog): ProcessedFileEntity = ProcessedFileEntity(
            uri = log.uri,
            displayName = log.displayName,
            mimeType = log.mimeType,
            timestamp = log.timestamp,
            sizeBytes = log.sizeBytes
        )
    }
}
