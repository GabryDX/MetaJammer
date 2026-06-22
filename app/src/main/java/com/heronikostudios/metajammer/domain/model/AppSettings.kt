package com.heronikostudios.metajammer.domain.model

data class AppSettings(
    val useRandomFileNames: Boolean = false,
    val folderStructure: FolderStructure = FolderStructure.SPLIT,
    val useSubfoldersInUnified: Boolean = true,
    val unifiedSavingPath: String? = "Download/MetaJammer",
    val picturesSavingPath: String? = "Pictures/MetaJammer",
    val musicSavingPath: String? = "Music/MetaJammer",
    val moviesSavingPath: String? = "Movies/MetaJammer",
    val documentsSavingPath: String? = "Documents/MetaJammer",
    val keepImageOrientation: Boolean = true,
    val shareResultAsDefault: Boolean = false,
    val defaultPrefix: String = "",
    val defaultSuffix: String = "_processed",
    val nightMode: NightModeSetting = NightModeSetting.AUTOMATIC,
    val oledMode: Boolean = false,
    val autoHandleSharedFiles: Boolean = false,
    val sharedFilesProcessingMode: ProcessingMode = ProcessingMode.REMOVE_METADATA,
    val sharedFilesOutputAction: SharedInputOutputAction = SharedInputOutputAction.SHARE_TO_ANOTHER_APP,
    val sharedFilesCustomPath: String? = null,
    val thumbnailHandling: ThumbnailHandling = ThumbnailHandling.REMOVE,
    val allowInternetForMap: Boolean = false,
    val useNearbyScramble: Boolean = false,
    val language: AppLanguage = AppLanguage.SYSTEM
)
