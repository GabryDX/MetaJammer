package com.heronikostudios.metajammer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.heronikostudios.metajammer.domain.usecase.ShareFileUseCase
import com.heronikostudios.metajammer.ui.MainViewModel
import com.heronikostudios.metajammer.ui.screens.HomeScreen
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
            MetaJammerTheme {
                MetaJammerApp(
                    sharedUris = sharedUris,
                    onExitApp = { finish() }
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

        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                uri?.let { listOf(it) } ?: emptyList()
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    ?: emptyList()
            }

            else -> emptyList()
        }
    }
}

private enum class AppStep {
    HOME,
    PREVIEW,
    PROCESS,
    OUTPUT,
    SETTINGS
}

private fun previousStep(step: AppStep): AppStep? {
    return when (step) {
        AppStep.HOME -> null
        AppStep.PREVIEW -> AppStep.HOME
        AppStep.PROCESS -> AppStep.PREVIEW
        AppStep.OUTPUT -> AppStep.PROCESS
        AppStep.SETTINGS -> AppStep.HOME
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetaJammerApp(
    sharedUris: List<Uri>,
    onExitApp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val metadataPreview by viewModel.metadataPreview.collectAsState()
    val selectedMode by viewModel.selectedMode.collectAsState()
    val selectedPostAction by viewModel.selectedPostAction.collectAsState()
    val processedFiles by viewModel.processedFiles.collectAsState()
    val processing by viewModel.processing.collectAsState()
    val message by viewModel.message.collectAsState()

    val shareFileUseCase = remember { ShareFileUseCase() }

    var currentStep by remember { mutableStateOf(AppStep.HOME) }

    fun navigateBack() {
        val previous = previousStep(currentStep)
        if (previous != null) {
            currentStep = previous
        } else {
            onExitApp()
        }
    }

    BackHandler {
        navigateBack()
    }

    LaunchedEffect(sharedUris) {
        if (sharedUris.isNotEmpty()) {
            viewModel.setIncomingUris(sharedUris)
            currentStep = AppStep.PREVIEW
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
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
                            AppStep.OUTPUT -> "Output Options"
                            AppStep.SETTINGS -> "Settings"
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
                    IconButton(
                        onClick = { currentStep = AppStep.SETTINGS }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { innerPadding ->
        when (currentStep) {
            AppStep.HOME -> {
                HomeScreen(
                    selectedFiles = selectedFiles,
                    onFilesPicked = { uris ->
                        viewModel.setIncomingUris(uris)
                        currentStep = AppStep.PREVIEW
                    },
                    onContinue = {
                        currentStep = AppStep.PREVIEW
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            AppStep.PREVIEW -> {
                MetadataPreviewScreen(
                    selectedFiles = selectedFiles,
                    metadataPreview = metadataPreview,
                    onContinue = {
                        currentStep = AppStep.PROCESS
                    },
                    onBack = {
                        navigateBack()
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            AppStep.PROCESS -> {
                ProcessingScreen(
                    selectedMode = selectedMode,
                    processing = processing,
                    onModeSelected = viewModel::setProcessingMode,
                    onProcess = {
                        viewModel.processFiles()
                    },
                    onNext = {
                        currentStep = AppStep.OUTPUT
                    },
                    hasProcessedFiles = processedFiles.isNotEmpty(),
                    modifier = Modifier.padding(innerPadding)
                )
            }

            AppStep.OUTPUT -> {
                OutputOptionsScreen(
                    selectedAction = selectedPostAction,
                    onActionSelected = viewModel::setPostProcessAction,
                    onSaveDefaultPreference = viewModel::saveDefaultPostAction,
                    onSaveDefault = {
                        viewModel.saveProcessedFilesToDefault()
                    },
                    onSaveCustom = { treeUri ->
                        viewModel.saveProcessedFilesToCustom(treeUri)
                    },
                    onShareOnly = {
                        val firstProcessed = processedFiles.firstOrNull() ?: return@OutputOptionsScreen
                        val savedUris = viewModel.saveProcessedFilesToDefault()
                        savedUris.firstOrNull()?.let { uri ->
                            shareFileUseCase(context, uri, firstProcessed.first.mimeType)
                        }
                    },
                    onSaveAndShare = {
                        val firstProcessed = processedFiles.firstOrNull() ?: return@OutputOptionsScreen
                        val savedUris = viewModel.saveProcessedFilesToDefault()
                        savedUris.firstOrNull()?.let { uri ->
                            shareFileUseCase(context, uri, firstProcessed.first.mimeType)
                        }
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            AppStep.SETTINGS -> {
                SettingsScreen(
                    currentDefaultAction = selectedPostAction,
                    onSetDefaultAction = viewModel::saveDefaultPostAction,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}
