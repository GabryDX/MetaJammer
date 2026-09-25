package com.heronikostudios.metajammer.ui.quickscrub

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.heronikostudios.metajammer.data.FileRepository
import com.heronikostudios.metajammer.data.MetadataRepository
import com.heronikostudios.metajammer.data.SettingsRepository
import com.heronikostudios.metajammer.domain.model.*
import com.heronikostudios.metajammer.domain.usecase.ProcessFileUseCase
import com.heronikostudios.metajammer.domain.usecase.SaveFileUseCase
import com.heronikostudios.metajammer.metadata.MetadataReplacementGenerator
import com.heronikostudios.metajammer.util.SanitizationUtils
import com.heronikostudios.metajammer.worker.MetadataProcessingWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File

/**
 * Encapsulates the intent-driven quick scrub workflow: automatically stripping
 * or poisoning incoming shared files and saving or re-sharing them with minimal user interaction.
 */
class QuickScrubHandler(
    private val metadataRepository: MetadataRepository,
    private val processFileUseCase: ProcessFileUseCase,
    private val saveFileUseCase: SaveFileUseCase,
    private val settingsRepository: SettingsRepository,
    private val fileRepository: FileRepository,
    private val workManager: WorkManager,
    private val cacheDir: File
) {

    constructor(context: Context) : this(
        metadataRepository = MetadataRepository(FileRepository(context)),
        processFileUseCase = ProcessFileUseCase(MetadataRepository(FileRepository(context))),
        saveFileUseCase = SaveFileUseCase(FileRepository(context)),
        settingsRepository = SettingsRepository(context),
        fileRepository = FileRepository(context),
        workManager = WorkManager.getInstance(context),
        cacheDir = context.cacheDir
    )

    suspend fun executeQuickScrub(
        files: List<SelectedFile>,
        appSettings: AppSettings,
        onShareFilesReady: (List<File>, String?) -> Unit,
        onStatusMessage: (String) -> Unit
    ) {
        if (files.isEmpty()) {
            onStatusMessage("No shared files received")
            return
        }

        val mode = appSettings.sharedFilesProcessingMode
        val keepOrientation = appSettings.keepImageOrientation

        val replacementPlans = if (mode == ProcessingMode.POISON_METADATA) {
            val newPlans = mutableMapOf<Uri, MetadataReplacementPlan>()
            withContext(Dispatchers.IO) {
                val useScramble = appSettings.useNearbyScramble
                for (selectedFile in files) {
                    val plan = if (useScramble) {
                        val metadata = metadataRepository.readMetadata(selectedFile)
                        val lat = metadata.find { it.key == "GPSLatitude" }?.value?.toDoubleOrNull()
                        val lon = metadata.find { it.key == "GPSLongitude" }?.value?.toDoubleOrNull()

                        if (lat != null && lon != null) {
                            MetadataReplacementGenerator.generatePlan(selectedFile.mimeType, lat, lon)
                        } else {
                            MetadataReplacementGenerator.generatePlan(selectedFile.mimeType)
                        }
                    } else {
                        MetadataReplacementGenerator.generatePlan(selectedFile.mimeType)
                    }
                    newPlans[selectedFile.uri] = plan
                }
            }
            newPlans
        } else {
            emptyMap()
        }

        if (files.size > 5) {
            enqueueBackgroundProcessing(files, mode, replacementPlans, appSettings)
            onStatusMessage("Processing ${files.size} files in background...")
            return
        }

        runCatching {
            val processedResults = withContext(Dispatchers.IO) {
                coroutineScope {
                    files.map { selectedFile ->
                        async {
                            val plan = replacementPlans[selectedFile.uri]
                            selectedFile to processFileUseCase(
                                selectedFile = selectedFile,
                                processingMode = mode,
                                keepOrientation = keepOrientation,
                                thumbnailHandling = appSettings.thumbnailHandling,
                                replacementPlan = plan
                            )
                        }
                    }.awaitAll()
                }
            }

            when (appSettings.sharedFilesOutputAction) {
                SharedInputOutputAction.SAVE_TO_DEFAULT_FOLDER -> {
                    saveProcessedFilesToDefault(processedResults, appSettings)
                    onStatusMessage("Saved ${processedResults.size} file(s) to default folder")
                }

                SharedInputOutputAction.SAVE_TO_SHARED_FOLDER -> {
                    val path = appSettings.sharedFilesCustomPath
                    if (path.isNullOrBlank()) {
                        throw IllegalStateException("No shared-files folder configured")
                    }
                    saveProcessedFilesToCustom(processedResults, path.toUri(), appSettings)
                    onStatusMessage("Saved ${processedResults.size} file(s) to shared folder")
                }

                SharedInputOutputAction.SHARE_TO_ANOTHER_APP -> {
                    if (processedResults.isEmpty()) {
                        throw IllegalStateException("No processed files available for sharing")
                    }
                    val processedFilesList = processedResults.map { it.second }
                    val selectedFilesList = processedResults.map { it.first }

                    val nicelyNamedFiles = withContext(Dispatchers.IO) {
                        prepareFilesForSharing(processedFilesList, selectedFilesList, appSettings)
                    }

                    val firstMime = selectedFilesList.first().mimeType
                    val allSameMime = selectedFilesList.all { it.mimeType == firstMime }
                    onShareFilesReady(nicelyNamedFiles, if (allSameMime) firstMime else "*/*")
                }
            }
        }.onSuccess {
            onStatusMessage("Shared files handled automatically")
        }.onFailure {
            Timber.e(it, "Automatic shared file handling failed")
            onStatusMessage("Automatic handling failed: ${it.message}")
        }
    }

    private suspend fun saveProcessedFilesToDefault(
        processed: List<Pair<SelectedFile, File>>,
        appSettings: AppSettings
    ) = withContext(Dispatchers.IO) {
        coroutineScope {
            processed.map { (selectedFile, processedFile) ->
                async {
                    val (configuredPath, subPath) = resolveSavingPath(selectedFile.mimeType, appSettings)
                    val outputName = buildOutputName(selectedFile.displayName, appSettings)
                    val savedUri = saveFileUseCase.saveToDefaultFolder(
                        sourceFile = processedFile,
                        displayName = outputName,
                        mimeType = selectedFile.mimeType,
                        configuredPath = configuredPath,
                        subPath = subPath
                    )

                    if (savedUri != null) {
                        settingsRepository.logProcessedFile(
                            ProcessedFileLog(
                                uri = savedUri.toString(),
                                displayName = outputName,
                                mimeType = selectedFile.mimeType,
                                timestamp = System.currentTimeMillis(),
                                sizeBytes = processedFile.length()
                            )
                        )
                    }
                    savedUri
                }
            }.awaitAll()
        }
    }

    private suspend fun saveProcessedFilesToCustom(
        processed: List<Pair<SelectedFile, File>>,
        treeUri: Uri,
        appSettings: AppSettings
    ) = withContext(Dispatchers.IO) {
        coroutineScope {
            processed.map { (selectedFile, processedFile) ->
                async {
                    val outputName = buildOutputName(selectedFile.displayName, appSettings)
                    val savedUri = saveFileUseCase.saveToCustomFolder(
                        treeUri = treeUri,
                        sourceFile = processedFile,
                        displayName = outputName,
                        mimeType = selectedFile.mimeType
                    )

                    if (savedUri != null) {
                        settingsRepository.logProcessedFile(
                            ProcessedFileLog(
                                uri = savedUri.toString(),
                                displayName = outputName,
                                mimeType = selectedFile.mimeType,
                                timestamp = System.currentTimeMillis(),
                                sizeBytes = processedFile.length()
                            )
                        )
                    }
                    savedUri
                }
            }.awaitAll()
        }
    }

    private suspend fun prepareFilesForSharing(
        processedFiles: List<File>,
        selectedFiles: List<SelectedFile>,
        appSettings: AppSettings
    ): List<File> = withContext(Dispatchers.IO) {
        val sharedDir = File(cacheDir, "shared/outgoing_${System.currentTimeMillis()}")
        if (!sharedDir.exists()) sharedDir.mkdirs()

        coroutineScope {
            processedFiles.mapIndexed { index, file ->
                async {
                    val selectedFile = selectedFiles[index]
                    val niceName = buildOutputName(selectedFile.displayName, appSettings)
                    val sharedFile = File(sharedDir, niceName)

                    val success = runCatching {
                        if (!file.renameTo(sharedFile)) {
                            file.copyTo(sharedFile, overwrite = true)
                            file.delete()
                        }
                        true
                    }.getOrDefault(false)

                    if (success) sharedFile else null
                }
            }.awaitAll().filterNotNull()
        }
    }

    private fun enqueueBackgroundProcessing(
        files: List<SelectedFile>,
        mode: ProcessingMode,
        plans: Map<Uri, MetadataReplacementPlan>,
        settings: AppSettings
    ) {
        val plansFile = File(cacheDir, "processing_plans_${System.currentTimeMillis()}.json")
        plansFile.writeText(Json.encodeToString(plans.mapKeys { it.key.toString() }))

        val inputData = Data.Builder()
            .putStringArray(MetadataProcessingWorker.KEY_INPUT_URIS, files.map { it.uri.toString() }.toTypedArray())
            .putString(MetadataProcessingWorker.KEY_MODE, mode.name)
            .putBoolean(MetadataProcessingWorker.KEY_KEEP_ORIENTATION, settings.keepImageOrientation)
            .putString(MetadataProcessingWorker.KEY_THUMBNAIL_HANDLING, settings.thumbnailHandling.name)
            .putString(MetadataProcessingWorker.KEY_PLANS_FILE_PATH, plansFile.absolutePath)
            .putString(MetadataProcessingWorker.KEY_FOLDER_STRUCTURE, settings.folderStructure.name)
            .putBoolean(MetadataProcessingWorker.KEY_USE_SUBFOLDERS_IN_UNIFIED, settings.useSubfoldersInUnified)
            .putString(MetadataProcessingWorker.KEY_UNIFIED_SAVING_PATH, settings.unifiedSavingPath)
            .putString(MetadataProcessingWorker.KEY_PICTURES_SAVING_PATH, settings.picturesSavingPath)
            .putString(MetadataProcessingWorker.KEY_MUSIC_SAVING_PATH, settings.musicSavingPath)
            .putString(MetadataProcessingWorker.KEY_MOVIES_SAVING_PATH, settings.moviesSavingPath)
            .putString(MetadataProcessingWorker.KEY_DOCUMENTS_SAVING_PATH, settings.documentsSavingPath)
            .putString(MetadataProcessingWorker.KEY_DEFAULT_PREFIX, settings.defaultPrefix)
            .putString(MetadataProcessingWorker.KEY_DEFAULT_SUFFIX, settings.defaultSuffix)
            .putBoolean(MetadataProcessingWorker.KEY_USE_RANDOM_NAMES, settings.useRandomFileNames)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<MetadataProcessingWorker>()
            .setInputData(inputData)
            .build()

        workManager.enqueue(workRequest)
    }

    private fun resolveSavingPath(mimeType: String?, settings: AppSettings): Pair<String?, String?> {
        return if (settings.folderStructure == FolderStructure.UNIFIED) {
            val subPath = if (settings.useSubfoldersInUnified) {
                when {
                    mimeType?.startsWith("image/") == true -> "Pictures"
                    mimeType?.startsWith("video/") == true -> "Movies"
                    mimeType?.startsWith("audio/") == true -> "Music"
                    else -> "Documents"
                }
            } else null
            settings.unifiedSavingPath to subPath
        } else {
            val path = when {
                mimeType?.startsWith("image/") == true -> settings.picturesSavingPath
                mimeType?.startsWith("video/") == true -> settings.moviesSavingPath
                mimeType?.startsWith("audio/") == true -> settings.musicSavingPath
                else -> settings.documentsSavingPath
            }
            path to null
        }
    }

    private fun buildOutputName(originalName: String, settings: AppSettings): String {
        return SanitizationUtils.generateOutputName(
            originalName = originalName,
            useRandomFileNames = settings.useRandomFileNames,
            prefix = settings.defaultPrefix,
            suffix = settings.defaultSuffix
        )
    }
}
