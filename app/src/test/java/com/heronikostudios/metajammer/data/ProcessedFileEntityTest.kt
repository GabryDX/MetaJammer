package com.heronikostudios.metajammer.data

import com.heronikostudios.metajammer.data.db.ProcessedFileEntity
import com.heronikostudios.metajammer.domain.model.ProcessedFileLog
import org.junit.Assert.assertEquals
import org.junit.Test

class ProcessedFileEntityTest {

    @Test
    fun `conversion between domain model and entity preserves all fields`() {
        val domainLog = ProcessedFileLog(
            uri = "content://media/external/images/media/42",
            displayName = "sanitized_photo.jpg",
            mimeType = "image/jpeg",
            timestamp = 1716739200000L,
            sizeBytes = 204800L
        )

        val entity = ProcessedFileEntity.fromDomainModel(domainLog)

        assertEquals(domainLog.uri, entity.uri)
        assertEquals(domainLog.displayName, entity.displayName)
        assertEquals(domainLog.mimeType, entity.mimeType)
        assertEquals(domainLog.timestamp, entity.timestamp)
        assertEquals(domainLog.sizeBytes, entity.sizeBytes)

        val convertedBack = entity.toDomainModel()

        assertEquals(domainLog, convertedBack)
    }
}
