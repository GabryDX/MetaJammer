package com.heronikostudios.metajammer.domain.model

data class AppSettings(
    val useRandomFileNames: Boolean = false,
    val defaultSavingPath: String? = "Pictures/MetaJammer",
    val automaticDeletion: Boolean = false,
    val keepImageOrientation: Boolean = true,
    val shareResultAsDefault: Boolean = false,
    val defaultPrefix: String = "",
    val defaultSuffix: String = "",
    val nightMode: NightModeSetting = NightModeSetting.AUTOMATIC,
    val oledMode: Boolean = false,

    val autoHandleSharedFiles: Boolean = false,
    val sharedFilesProcessingMode: ProcessingMode = ProcessingMode.REMOVE_METADATA,
    val sharedFilesOutputAction: SharedInputOutputAction = SharedInputOutputAction.SAVE_TO_DEFAULT_FOLDER,
    val sharedFilesCustomPath: String? = null
)
