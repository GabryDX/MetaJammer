package com.heronikostudios.metajammer.domain.usecase

import android.net.Uri
import com.heronikostudios.metajammer.data.FileRepository
import java.io.File

class SaveFileUseCase(
    private val fileRepository: FileRepository
) {
    suspend fun saveToDefaultFolder(
        sourceFile: File,
        displayName: String,
        mimeType: String?,
        configuredPath: String?,
        subPath: String? = null
    ): Uri? {
        return fileRepository.saveToDefaultFolder(
            sourceFile = sourceFile,
            displayName = displayName,
            mimeType = mimeType,
            configuredPath = configuredPath,
            subPath = subPath
        )
    }

    suspend fun saveToCustomFolder(
        treeUri: Uri,
        sourceFile: File,
        displayName: String,
        mimeType: String?,
        subPath: String? = null
    ): Uri? {
        return fileRepository.saveToCustomFolder(treeUri, sourceFile, displayName, mimeType, subPath)
    }
}
