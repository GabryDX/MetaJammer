package com.heronikostudios.metajammer.domain.usecase

import com.heronikostudios.metajammer.data.MetadataRepository
import com.heronikostudios.metajammer.domain.model.MetadataReplacementPlan
import com.heronikostudios.metajammer.domain.model.ProcessingMode
import com.heronikostudios.metajammer.domain.model.SelectedFile
import java.io.File

class ProcessFileUseCase(
    private val metadataRepository: MetadataRepository
) {
    suspend operator fun invoke(
        selectedFile: SelectedFile,
        processingMode: ProcessingMode,
        keepOrientation: Boolean,
        thumbnailHandling: com.heronikostudios.metajammer.domain.model.ThumbnailHandling = com.heronikostudios.metajammer.domain.model.ThumbnailHandling.REMOVE,
        replacementPlan: MetadataReplacementPlan? = null,
        stripGps: Boolean = true,
        stripDeviceModel: Boolean = true,
        stripDateTime: Boolean = true,
        stripCameraSettings: Boolean = true,
        stripComments: Boolean = true
    ): File {
        return metadataRepository.processFile(
            selectedFile = selectedFile,
            mode = processingMode,
            keepOrientation = keepOrientation,
            thumbnailHandling = thumbnailHandling,
            replacementPlan = replacementPlan,
            stripGps = stripGps,
            stripDeviceModel = stripDeviceModel,
            stripDateTime = stripDateTime,
            stripCameraSettings = stripCameraSettings,
            stripComments = stripComments
        )
    }
}
