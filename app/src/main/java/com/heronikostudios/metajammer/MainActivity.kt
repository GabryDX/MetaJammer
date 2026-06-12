package com.heronikostudios.metajammer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.work.WorkInfo
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.heronikostudios.metajammer.domain.usecase.ShareFileUseCase
import com.heronikostudios.metajammer.ui.MainViewModel
import com.heronikostudios.metajammer.ui.components.MessageBanner
import com.heronikostudios.metajammer.ui.screens.HomeScreen
import com.heronikostudios.metajammer.ui.screens.LocationPickerScreen
import com.heronikostudios.metajammer.ui.screens.MetadataPreviewScreen
import com.heronikostudios.metajammer.ui.screens.OutputOptionsScreen
import com.heronikostudios.metajammer.ui.screens.ProcessingScreen
import com.heronikostudios.metajammer.ui.screens.SettingsScreen
import com.heronikostudios.metajammer.ui.theme.MetaJammerTheme

class MainActivity : ComponentActivity() {

    private var sharedUris by mutableStateOf<List<Uri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        sharedUris = extractSharedUris(intent)

        setContent {
            val viewModel: MainViewModel = viewModel()
            val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()

            MetaJammerTheme(
                nightModeSetting = appSettings.nightMode,
                oledMode = appSettings.oledMode
            ) {
                MetaJammerApp(
                    sharedUris = sharedUris,
                    onExitApp = { finish() },
                    viewModel = viewModel
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedUris = extractSharedUris(intent)
    }

    private fun extractSharedUris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()

        val action = intent.action
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) {
            return emptyList()
        }

        val uris = when (action) {
            Intent.ACTION_SEND -> {
                val uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                uri?.let(::listOf) ?: emptyList()
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) ?: emptyList()
            }

            else -> emptyList()
        }

        // Basic validation: ensure they are content URIs
        return uris.filter { uri ->
            uri.scheme == "content"
        }
    }
}

private enum class AppStep {
    HOME,
    PREVIEW,
    PROCESS,
    LOCATION_PICKER,
    OUTPUT,
    SETTINGS
}

private fun previousStep(step: AppStep): AppStep? = when (step) {
    AppStep.HOME -> null
    AppStep.PREVIEW -> AppStep.HOME
    AppStep.PROCESS -> AppStep.PREVIEW
    AppStep.LOCATION_PICKER -> AppStep.PROCESS
    AppStep.OUTPUT -> AppStep.PROCESS
    AppStep.SETTINGS -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetaJammerApp(
    sharedUris: List<Uri>,
    onExitApp: () -> Unit,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val shareFileUseCase = remember { ShareFileUseCase() }

    val selectedFiles by viewModel.selectedFiles.collectAsStateWithLifecycle()
    val metadataPreview by viewModel.metadataPreview.collectAsStateWithLifecycle()
    val changePreview by viewModel.changePreview.collectAsStateWithLifecycle()
    val selectedMode by viewModel.selectedMode.collectAsStateWithLifecycle()
    val processedFiles by viewModel.processedFiles.collectAsStateWithLifecycle()
    val processing by viewModel.processing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    val settingsInitialized by viewModel.settingsInitialized.collectAsStateWithLifecycle()
    val replacementPlans by viewModel.replacementPlans.collectAsStateWithLifecycle()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Handle result if needed
    }

    var showNotificationPermissionExplanation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            showNotificationPermissionExplanation = true
        }
    }

    var currentStep by rememberSaveable { mutableStateOf(AppStep.HOME) }
    var previousNonSettingsStep by rememberSaveable { mutableStateOf(AppStep.HOME) }
    var handledSharedSignature by rememberSaveable { mutableStateOf<String?>(null) }
    var uriToEditLocation by remember { mutableStateOf<Uri?>(null) }
    var showInternetPermissionExplanation by remember { mutableStateOf(false) }

    fun navigateTo(step: AppStep) {
        viewModel.clearMessage()
        if (step != AppStep.SETTINGS) {
            previousNonSettingsStep = step
        }
        currentStep = step
    }

    fun navigateBack() {
        when (currentStep) {
            AppStep.SETTINGS -> currentStep = previousNonSettingsStep
            else -> {
                val previous = previousStep(currentStep)
                if (previous != null) currentStep = previous else onExitApp()
            }
        }
    }

    BackHandler {
        navigateBack()
    }

    val sharedSignature = remember(sharedUris) {
        if (sharedUris.isEmpty()) {
            null
        } else {
            sharedUris.joinToString(separator = "|") { it.toString() }
        }
    }

    LaunchedEffect(sharedSignature, settingsInitialized, appSettings.autoHandleSharedFiles) {
        if (!settingsInitialized) return@LaunchedEffect
        if (sharedUris.isEmpty()) return@LaunchedEffect
        if (sharedSignature == null) return@LaunchedEffect
        if (handledSharedSignature == sharedSignature) return@LaunchedEffect

        handledSharedSignature = sharedSignature
        viewModel.setIncomingUris(sharedUris)

        if (appSettings.autoHandleSharedFiles) {
            viewModel.autoHandleSharedInput { file, mimeType ->
                shareFileUseCase.shareFile(
                    context = context,
                    file = file,
                    mimeType = mimeType
                )
            }
        } else {
            navigateTo(AppStep.PREVIEW)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = when (currentStep) {
                            AppStep.HOME -> "MetaJammer"
                            AppStep.PREVIEW -> "Metadata Preview"
                            AppStep.PROCESS -> "Process Files"
                            AppStep.LOCATION_PICKER -> "Pick Location"
                            AppStep.OUTPUT -> "Output Options"
                            AppStep.SETTINGS -> "Settings"
                        },
                        style = when (currentStep) {
                            AppStep.HOME -> MaterialTheme.typography.headlineMedium
                            else -> MaterialTheme.typography.titleLarge
                        }
                    )
                },
                navigationIcon = {
                    if (currentStep != AppStep.HOME) {
                        IconButton(onClick = { navigateBack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { currentStep = AppStep.SETTINGS }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState)
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding)
        ) {
            message?.let {
                MessageBanner(
                    message = it,
                    onDismiss = { viewModel.clearMessage() }
                )
            }

            when (currentStep) {
                AppStep.HOME -> {
                    HomeScreen(
                        selectedFiles = selectedFiles,
                        onFilesPicked = {
                            viewModel.setIncomingUris(it)
                            navigateTo(AppStep.PREVIEW)
                        },
                        onContinue = {
                            if (selectedFiles.isNotEmpty()) navigateTo(AppStep.PREVIEW)
                        },
                        onClearSelection = {
                            viewModel.clearSelection()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                AppStep.PREVIEW -> {
                    MetadataPreviewScreen(
                        selectedFiles = selectedFiles,
                        metadataPreview = metadataPreview,
                        onContinue = { navigateTo(AppStep.PROCESS) },
                        onBack = { navigateTo(AppStep.HOME) },
                        modifier = Modifier.weight(1f)
                    )
                }

                AppStep.PROCESS -> {
                    ProcessingScreen(
                        selectedFiles = selectedFiles,
                        selectedMode = selectedMode,
                        changePreview = changePreview,
                        processing = processing,
                        workInfo = viewModel.workInfo.collectAsStateWithLifecycle().value,
                        onModeSelected = viewModel::setProcessingMode,
                        onProcess = viewModel::processFiles,
                        onNext = {
                            if (processedFiles.isNotEmpty()) navigateTo(AppStep.OUTPUT)
                        },
                        onEditLocation = { uri ->
                            uriToEditLocation = uri
                            if (appSettings.allowInternetForMap) {
                                navigateTo(AppStep.LOCATION_PICKER)
                            } else {
                                showInternetPermissionExplanation = true
                            }
                        },
                        hasProcessedFiles = processedFiles.isNotEmpty() || viewModel.workInfo.collectAsStateWithLifecycle().value?.state == WorkInfo.State.SUCCEEDED,
                        modifier = Modifier.weight(1f)
                    )
                }

                AppStep.LOCATION_PICKER -> {
                    val uri = uriToEditLocation
                    if (uri != null) {
                        val plan = replacementPlans[uri]
                        
                        LocationPickerScreen(
                            initialLat = plan?.latitude ?: 0.0,
                            initialLon = plan?.longitude ?: 0.0,
                            onLocationPicked = { lat, lon ->
                                viewModel.updatePlanLocation(uri, lat, lon)
                                navigateBack()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                AppStep.OUTPUT -> {
                    OutputOptionsScreen(
                        shareResultAsDefault = appSettings.shareResultAsDefault,
                        onSaveDefault = {
                            val firstProcessed = processedFiles.firstOrNull()
                            val savedUris = viewModel.saveProcessedFilesToDefault()

                            if (appSettings.shareResultAsDefault) {
                                val firstSavedUri = savedUris.firstOrNull()
                                if (firstSavedUri != null && firstProcessed != null) {
                                    shareFileUseCase.shareUri(
                                        context = context,
                                        uri = firstSavedUri,
                                        mimeType = firstProcessed.first.mimeType
                                    )
                                }
                            }
                        },
                        onSaveCustom = { treeUri ->
                            val firstProcessed = processedFiles.firstOrNull()
                            val savedUris = viewModel.saveProcessedFilesToCustom(treeUri)

                            if (appSettings.shareResultAsDefault) {
                                val firstSavedUri = savedUris.firstOrNull()
                                if (firstSavedUri != null && firstProcessed != null) {
                                    shareFileUseCase.shareUri(
                                        context = context,
                                        uri = firstSavedUri,
                                        mimeType = firstProcessed.first.mimeType
                                    )
                                }
                            }
                        },
                        onShareOnly = {
                            viewModel.getFirstProcessedFileForSharing()?.let { (selectedFile, file) ->
                                shareFileUseCase.shareFile(
                                    context = context,
                                    file = file,
                                    mimeType = selectedFile.mimeType
                                )
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                AppStep.SETTINGS -> {
                    SettingsScreen(
                        settings = appSettings,
                        onUseRandomFileNamesChanged = viewModel::setUseRandomFileNames,
                        onDefaultSavingPathSelected = viewModel::persistAndSetDefaultSavingPath,
                        onKeepImageOrientationChanged = viewModel::setKeepImageOrientation,
                        onShareResultAsDefaultChanged = viewModel::setShareResultAsDefault,
                        onDefaultPrefixChanged = viewModel::setDefaultPrefix,
                        onDefaultSuffixChanged = viewModel::setDefaultSuffix,
                        onNightModeChanged = viewModel::setNightMode,
                        onOledModeChanged = viewModel::setOledMode,
                        onAutoHandleSharedFilesChanged = viewModel::setAutoHandleSharedFiles,
                        onSharedFilesProcessingModeChanged = viewModel::setSharedFilesProcessingMode,
                        onSharedFilesOutputActionChanged = viewModel::setSharedFilesOutputAction,
                        onSharedFilesCustomPathSelected = viewModel::persistAndSetSharedFilesCustomPath,
                        onThumbnailHandlingChanged = viewModel::setThumbnailHandling,
                        onAllowInternetForMapChanged = viewModel::setAllowInternetForMap,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (showInternetPermissionExplanation) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showInternetPermissionExplanation = false },
                title = { Text("Map Picker & Internet Access") },
                text = {
                    Text(
                        "To show a map for location picking, MetaJammer needs to connect to the internet to load map tiles from OpenStreetMap. \n\n" +
                                "If you decline, the app will continue to work perfectly for all metadata stripping and poisoning features, but you won't be able to pick locations visually on a map."
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            viewModel.setAllowInternetForMap(true)
                            showInternetPermissionExplanation = false
                            navigateTo(AppStep.LOCATION_PICKER)
                        }
                    ) {
                        Text("Allow & Open Map")
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            showInternetPermissionExplanation = false
                        }
                    ) {
                        Text("Not Now")
                    }
                }
            )
        }

        if (showNotificationPermissionExplanation) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showNotificationPermissionExplanation = false },
                title = { Text("Stay Informed") },
                text = {
                    Text(
                        "MetaJammer can show notifications to keep you updated on the progress of large file batches while they process in the background. \n\n" +
                                "This is especially useful if you want to switch to another app while MetaJammer finishes its work."
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            showNotificationPermissionExplanation = false
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    ) {
                        Text("Continue")
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            showNotificationPermissionExplanation = false
                        }
                    ) {
                        Text("Not Now")
                    }
                }
            )
        }
    }
}
