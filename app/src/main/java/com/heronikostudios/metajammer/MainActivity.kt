package com.heronikostudios.metajammer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.work.WorkInfo
import com.heronikostudios.metajammer.domain.usecase.ShareFileUseCase
import com.heronikostudios.metajammer.navigation.Screen
import com.heronikostudios.metajammer.ui.MainViewModel
import com.heronikostudios.metajammer.ui.components.MessageBanner
import com.heronikostudios.metajammer.ui.screens.*
import com.heronikostudios.metajammer.ui.theme.MetaJammerTheme
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

open class MainActivity : AppCompatActivity() {

    private var sharedUris by mutableStateOf<List<Uri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Security: Protect against tapjacking (overlay) attacks
        findViewById<android.view.View>(android.R.id.content)?.filterTouchesWhenObscured = true

        enableEdgeToEdge()
        sharedUris = extractSharedUris(intent)

        setContent {
            val viewModel: MainViewModel = koinViewModel()
            val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()

            MetaJammerTheme(
                nightModeSetting = appSettings.nightMode,
                oledMode = appSettings.oledMode,
                dynamicColor = appSettings.useDynamicColor
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
                val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                uri?.let(::listOf) ?: emptyList()
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: emptyList()
            }

            else -> emptyList()
        }

        return uris.filter { uri ->
            uri.scheme == "content"
        }
    }
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
    val coroutineScope = rememberCoroutineScope()
    val shareFileUseCase = remember { ShareFileUseCase() }

    val selectedFiles by viewModel.selectedFiles.collectAsStateWithLifecycle()
    val metadataPreview by viewModel.metadataPreview.collectAsStateWithLifecycle()
    val changePreview by viewModel.changePreview.collectAsStateWithLifecycle()
    val selectedMode by viewModel.selectedMode.collectAsStateWithLifecycle()
    val selectedProfile by viewModel.selectedProfile.collectAsStateWithLifecycle()
    val selectedLocationPreset by viewModel.selectedLocationPreset.collectAsStateWithLifecycle()
    val processedFiles by viewModel.processedFiles.collectAsStateWithLifecycle()
    val processing by viewModel.processing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    val settingsInitialized by viewModel.settingsInitialized.collectAsStateWithLifecycle()
    val replacementPlans by viewModel.replacementPlans.collectAsStateWithLifecycle()
    val workInfo by viewModel.workInfo.collectAsStateWithLifecycle()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    var showNotificationPermissionExplanation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                showNotificationPermissionExplanation = true
            }
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val isHome = currentDestination?.hasRoute(Screen.Home::class) == true
    val isOnboarding = currentDestination?.hasRoute(Screen.Onboarding::class) == true
    val isQuickScrub = currentDestination?.hasRoute(Screen.QuickScrub::class) == true
    val isSettings = currentDestination?.hasRoute(Screen.Settings::class) == true
    val isHelp = currentDestination?.hasRoute(Screen.Help::class) == true
    val isHistory = currentDestination?.hasRoute(Screen.History::class) == true
    val isPreview = currentDestination?.hasRoute(Screen.Preview::class) == true
    val isProcess = currentDestination?.hasRoute(Screen.Process::class) == true
    val isLocationPicker = currentDestination?.hasRoute(Screen.LocationPicker::class) == true
    val isOutput = currentDestination?.hasRoute(Screen.Output::class) == true

    var initialNavigationDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(settingsInitialized) {
        if (settingsInitialized && !initialNavigationDone) {
            if (!appSettings.isOnboardingCompleted) {
                navController.navigate(Screen.Onboarding) {
                    popUpTo(0) { inclusive = true }
                }
            }
            initialNavigationDone = true
        }
    }

    var handledSharedSignature by rememberSaveable { mutableStateOf<String?>(null) }
    var uriToEditLocation by remember { mutableStateOf<Uri?>(null) }
    var showInternetPermissionExplanation by remember { mutableStateOf(false) }

    // Intercept back on Home to exit app
    BackHandler(enabled = isHome) {
        onExitApp()
    }

    val sharedSignature = remember(sharedUris) {
        if (sharedUris.isEmpty()) null else sharedUris.joinToString(separator = "|") { it.toString() }
    }

    LaunchedEffect(sharedSignature, settingsInitialized, appSettings.autoHandleSharedFiles) {
        if (!settingsInitialized) return@LaunchedEffect
        if (sharedUris.isEmpty()) return@LaunchedEffect
        if (sharedSignature == null) return@LaunchedEffect
        if (handledSharedSignature == sharedSignature) return@LaunchedEffect

        handledSharedSignature = sharedSignature
        viewModel.setIncomingUrisSuspend(sharedUris, loadMetadata = !appSettings.autoHandleSharedFiles)

        if (appSettings.autoHandleSharedFiles) {
            navController.navigate(Screen.QuickScrub)
            viewModel.autoHandleSharedInput { files, mimeType ->
                shareFileUseCase.shareFiles(
                    context = context,
                    files = files,
                    mimeType = mimeType
                )
                onExitApp()
            }
        } else {
            navController.navigate(Screen.Preview)
        }
    }

    LaunchedEffect(workInfo) {
        if (isProcess && workInfo?.state == WorkInfo.State.SUCCEEDED) {
            navController.navigate(Screen.Output) {
                popUpTo<Screen.Process> { inclusive = true }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = if (isQuickScrub) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            if (!isQuickScrub && !isOnboarding) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = when {
                                isHome -> stringResource(R.string.app_name)
                                isPreview -> stringResource(R.string.metadata_preview)
                                isProcess -> stringResource(R.string.process_files_title)
                                isLocationPicker -> stringResource(R.string.pick_location)
                                isOutput -> stringResource(R.string.output_options)
                                isSettings -> stringResource(R.string.settings)
                                isHelp -> stringResource(R.string.help_title)
                                isHistory -> stringResource(R.string.history_title)
                                else -> ""
                            },
                            style = if (isHome) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        if (isHome) {
                            IconButton(onClick = { navController.navigate(Screen.Help) }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_help_outline),
                                    contentDescription = stringResource(R.string.help)
                                )
                            }
                        } else if (!isQuickScrub && !isOnboarding) {
                            IconButton(onClick = {
                                if (!navController.popBackStack()) {
                                    onExitApp()
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.back)
                                )
                            }
                        }
                    },
                    actions = {
                        if (isHome && appSettings.enableProcessingHistory && appSettings.showHistoryShortcut) {
                            IconButton(onClick = { navController.navigate(Screen.History) }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.List,
                                    contentDescription = stringResource(R.string.history_title)
                                )
                            }
                        }
                        if (!isQuickScrub && !isOnboarding && !isSettings) {
                            IconButton(onClick = { navController.navigate(Screen.Settings) }) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = stringResource(R.string.settings)
                                )
                            }
                        }
                    }
                )
            }
        },
        snackbarHost = {
            if (!isQuickScrub) {
                SnackbarHost(snackbarHostState)
            }
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

            NavHost(
                navController = navController,
                startDestination = Screen.Home,
                enterTransition = { fadeIn(tween(200)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(200)) },
                exitTransition = { fadeOut(tween(200)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(200)) },
                popEnterTransition = { fadeIn(tween(200)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(200)) },
                popExitTransition = { fadeOut(tween(200)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(200)) },
                modifier = Modifier.fillMaxSize()
            ) {
                composable<Screen.Onboarding> {
                    OnboardingScreen(
                        onFinish = {
                            viewModel.setOnboardingCompleted(true)
                            navController.navigate(Screen.Home) {
                                popUpTo<Screen.Onboarding> { inclusive = true }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                composable<Screen.Home> {
                    HomeScreen(
                        selectedFiles = selectedFiles,
                        onFilesPicked = {
                            viewModel.setIncomingUris(it)
                            navController.navigate(Screen.Preview)
                        },
                        onFileRemoved = {
                            viewModel.removeFileFromSelection(it)
                        },
                        onContinue = {
                            if (selectedFiles.isNotEmpty()) navController.navigate(Screen.Preview)
                        },
                        onClearSelection = {
                            viewModel.clearSelection()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                composable<Screen.Help> {
                    HelpScreen(
                        onBack = { navController.popBackStack() },
                        onReportIssue = {
                            runCatching {
                                val intent = Intent(Intent.ACTION_VIEW, "https://github.com/GabryDX/MetaJammer/issues".toUri())
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                composable<Screen.Preview> {
                    MetadataPreviewScreen(
                        selectedFiles = selectedFiles,
                        metadataPreview = metadataPreview,
                        onContinue = { navController.navigate(Screen.Process) },
                        onBack = { navController.popBackStack() },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                composable<Screen.Process> {
                    val hasProcessedFiles = processedFiles.isNotEmpty() || workInfo?.state == WorkInfo.State.SUCCEEDED

                    ProcessingScreen(
                        selectedFiles = selectedFiles,
                        selectedMode = selectedMode,
                        changePreview = changePreview,
                        processing = processing,
                        workInfo = workInfo,
                        onModeSelected = viewModel::setProcessingMode,
                        onRegeneratePlans = viewModel::regeneratePoisonPlans,
                        onProcess = {
                            if (hasProcessedFiles) {
                                navController.navigate(Screen.Output)
                            } else {
                                viewModel.processFiles(onSuccess = {
                                    navController.navigate(Screen.Output)
                                })
                            }
                        },
                        onEditLocation = { uri ->
                            uriToEditLocation = uri
                            if (appSettings.allowInternetForMap) {
                                navController.navigate(Screen.LocationPicker(uri.toString()))
                            } else {
                                showInternetPermissionExplanation = true
                            }
                        },
                        hasProcessedFiles = hasProcessedFiles,
                        selectedProfile = selectedProfile,
                        onProfileSelected = viewModel::setProcessingPoisoningProfile,
                        selectedLocationPreset = selectedLocationPreset,
                        onLocationPresetSelected = viewModel::setProcessingLocationPreset,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                composable<Screen.LocationPicker> { backStackEntry ->
                    val route = backStackEntry.toRoute<Screen.LocationPicker>()
                    val uri = route.uriString.toUri()
                    val plan = replacementPlans[uri]

                    LocationPickerScreen(
                        initialLat = plan?.latitude ?: 0.0,
                        initialLon = plan?.longitude ?: 0.0,
                        onLocationPicked = { lat, lon ->
                            viewModel.updatePlanLocation(uri, lat, lon)
                            navController.popBackStack()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                composable<Screen.Output> {
                    OutputOptionsScreen(
                        shareResultAsDefault = appSettings.shareResultAsDefault,
                        onSaveDefault = {
                            coroutineScope.launch {
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
                            }
                        },
                        onSaveCustom = { treeUri ->
                            coroutineScope.launch {
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
                            }
                        },
                        onShareOnly = {
                            coroutineScope.launch {
                                val filesToShare = viewModel.getProcessedFilesForSharing()
                                if (filesToShare.isNotEmpty()) {
                                    val firstProcessedMime = processedFiles.firstOrNull()?.first?.mimeType
                                    val allSameMime = processedFiles.all { it.first.mimeType == firstProcessedMime }

                                    shareFileUseCase.shareFiles(
                                        context = context,
                                        files = filesToShare,
                                        mimeType = if (allSameMime) firstProcessedMime else "*/*"
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                composable<Screen.Settings> {
                    SettingsScreen(
                        settings = appSettings,
                        onUseRandomFileNamesChanged = viewModel::setUseRandomFileNames,
                        onFolderStructureChanged = viewModel::setFolderStructure,
                        onUseSubfoldersInUnifiedChanged = viewModel::setUseSubfoldersInUnified,
                        onUnifiedSavingPathSelected = viewModel::persistAndSetUnifiedSavingPath,
                        onPicturesSavingPathSelected = viewModel::persistAndSetPicturesSavingPath,
                        onMusicSavingPathSelected = viewModel::persistAndSetMusicSavingPath,
                        onMoviesSavingPathSelected = viewModel::persistAndSetMoviesSavingPath,
                        onDocumentsSavingPathSelected = viewModel::persistAndSetDocumentsSavingPath,
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
                        onUseNearbyScrambleChanged = viewModel::setUseNearbyScramble,
                        onLanguageChanged = viewModel::setLanguage,
                        onUseDynamicColorChanged = viewModel::setUseDynamicColor,
                        onEnableProcessingHistoryChanged = viewModel::setEnableProcessingHistory,
                        onHistoryRetentionPolicyChanged = viewModel::setHistoryRetentionPolicy,
                        onShowHistoryShortcutChanged = viewModel::setShowHistoryShortcut,
                        onClearHistory = viewModel::clearProcessedFilesHistory,
                        onViewHistory = { navController.navigate(Screen.History) },
                        onPoisoningProfileChanged = viewModel::setPoisoningProfile,
                        onLocationPresetChanged = viewModel::setLocationPreset,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                composable<Screen.History> {
                    val history by viewModel.processedFilesHistory.collectAsStateWithLifecycle()
                    HistoryScreen(
                        history = history,
                        onClearHistory = viewModel::clearProcessedFilesHistory,
                        onShareFile = { log -> viewModel.shareHistoryFile(context, log) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                composable<Screen.QuickScrub> {
                    QuickScrubScreen(
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        if (showInternetPermissionExplanation) {
            AlertDialog(
                onDismissRequest = { showInternetPermissionExplanation = false },
                title = { Text(stringResource(R.string.map_permission_title)) },
                text = {
                    Text(stringResource(R.string.map_permission_message))
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.setAllowInternetForMap(true)
                            showInternetPermissionExplanation = false
                            val uri = uriToEditLocation
                            if (uri != null) {
                                navController.navigate(Screen.LocationPicker(uri.toString()))
                            }
                        }
                    ) {
                        Text(stringResource(R.string.allow_open_map))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showInternetPermissionExplanation = false
                        }
                    ) {
                        Text(stringResource(R.string.not_now))
                    }
                }
            )
        }

        if (showNotificationPermissionExplanation) {
            AlertDialog(
                onDismissRequest = { showNotificationPermissionExplanation = false },
                title = { Text(stringResource(R.string.notification_permission_title)) },
                text = {
                    Text(stringResource(R.string.notification_permission_message))
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showNotificationPermissionExplanation = false
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    ) {
                        Text(stringResource(R.string.continue_label))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showNotificationPermissionExplanation = false
                        }
                    ) {
                        Text(stringResource(R.string.not_now))
                    }
                }
            )
        }
    }
}
