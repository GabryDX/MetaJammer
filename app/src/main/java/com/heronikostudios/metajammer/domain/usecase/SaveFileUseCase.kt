package com.heronikostudios.metajammer.domain.usecase

import android.net.Uri
import com.heronikostudios.metajammer.data.FileRepository
import java.io.File

class SaveFileUseCase(
    private val fileRepository: FileRepository
) {
    fun saveToDefaultFolder(
        sourceFile: File,
        displayName: String,
        mimeType: String?
    ): Uri? {
        return fileRepository.saveToDefaultFolder(sourceFile, displayName, mimeType)
    }

    fun saveToCustomFolder(
        treeUri: Uri,
        sourceFile: File,
        displayName: String,
        mimeType: String?
    ): Uri? {
        return fileRepository.saveToCustomFolder(treeUri, sourceFile, displayName, mimeType)
    }
}
