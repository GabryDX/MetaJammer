package com.heronikostudios.metajammer.domain.usecase

import com.heronikostudios.metajammer.data.MetadataRepository
import com.heronikostudios.metajammer.domain.model.ProcessingMode
import com.heronikostudios.metajammer.domain.model.SelectedFile
import java.io.File

class ProcessFileUseCase(
    private val metadataRepository: MetadataRepository
) {
    operator fun invoke(
        selectedFile: SelectedFile,
        processingMode: ProcessingMode
    ): File {
        return metadataRepository.processFile(selectedFile, processingMode)
    }
}
