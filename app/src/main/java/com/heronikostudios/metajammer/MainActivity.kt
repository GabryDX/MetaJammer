package com.heronikostudios.metajammer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.heronikostudios.metajammer.domain.usecase.ShareFileUseCase
import com.heronikostudios.metajammer.ui.MainViewModel
import com.heronikostudios.metajammer.ui.screens.HomeScreen
import com.heronikostudios.metajammer.ui.screens.OutputOptionsScreen
import com.heronikostudios.metajammer.ui.screens.ProcessingScreen
import com.heronikostudios.metajammer.ui.theme.MetaJammerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var sharedUris by mutableStateOf<List<Uri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        sharedUris = extractSharedUris(intent)

        setContent {
            MetaJammerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MetaJammerApp(
                        sharedUris = sharedUris,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
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
    PROCESS,
    OUTPUT
}

@androidx.compose.runtime.Composable
fun MetaJammerApp(
    sharedUris: List<Uri>,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val selectedMode by viewModel.selectedMode.collectAsState()
    val selectedPostAction by viewModel.selectedPostAction.collectAsState()
    val processedFiles by viewModel.processedFiles.collectAsState()
    val processing by viewModel.processing.collectAsState()
    val message by viewModel.message.collectAsState()

    val shareFileUseCase = remember { ShareFileUseCase() }

    var currentStep by remember { mutableStateOf(AppStep.HOME) }

    LaunchedEffect(sharedUris) {
        if (sharedUris.isNotEmpty()) {
            viewModel.setIncomingUris(sharedUris)
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
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.Top
        ) {
            when (currentStep) {
                AppStep.HOME -> {
                    HomeScreen(
                        selectedFiles = selectedFiles,
                        onFilesPicked = { uris ->
                            viewModel.setIncomingUris(uris)
                        },
                        onContinue = {
                            currentStep = AppStep.PROCESS
                        },
                        modifier = Modifier.weight(1f)
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
                        modifier = Modifier.weight(1f)
                    )
                }

                AppStep.OUTPUT -> {
                    OutputOptionsScreen(
                        selectedAction = selectedPostAction,
                        onActionSelected = viewModel::setPostProcessAction,
                        onSaveDefaultPreference = viewModel::saveDefaultPostAction,
                        onSaveDefault = {
                            val savedUris = viewModel.saveProcessedFilesToDefault()
                            coroutineScope.launch {
                                if (savedUris.isNotEmpty()) {
                                    snackbarHostState.showSnackbar("Saved ${savedUris.size} file(s) to default folder")
                                } else {
                                    snackbarHostState.showSnackbar("No files were saved")
                                }
                            }
                        },
                        onSaveCustom = { treeUri ->
                            val savedUris = viewModel.saveProcessedFilesToCustom(treeUri)
                            coroutineScope.launch {
                                if (savedUris.isNotEmpty()) {
                                    snackbarHostState.showSnackbar("Saved ${savedUris.size} file(s) to custom folder")
                                } else {
                                    snackbarHostState.showSnackbar("No files were saved")
                                }
                            }
                        },
                        onShareOnly = {
                            val firstProcessed = processedFiles.firstOrNull()
                            if (firstProcessed == null) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("No processed files available to share")
                                }
                                return@OutputOptionsScreen
                            }

                            val savedUris = viewModel.saveProcessedFilesToDefault()
                            val firstSavedUri = savedUris.firstOrNull()

                            if (firstSavedUri != null) {
                                shareFileUseCase(
                                    context = context,
                                    uri = firstSavedUri,
                                    mimeType = firstProcessed.first.mimeType
                                )
                            } else {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Unable to prepare file for sharing")
                                }
                            }
                        },
                        onSaveAndShare = {
                            val firstProcessed = processedFiles.firstOrNull()
                            if (firstProcessed == null) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("No processed files available")
                                }
                                return@OutputOptionsScreen
                            }

                            val savedUris = viewModel.saveProcessedFilesToDefault()
                            val firstSavedUri = savedUris.firstOrNull()

                            if (firstSavedUri != null) {
                                shareFileUseCase(
                                    context = context,
                                    uri = firstSavedUri,
                                    mimeType = firstProcessed.first.mimeType
                                )
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Saved and shared file")
                                }
                            } else {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Failed to save file before sharing")
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Button(
                onClick = {
                    currentStep = when (currentStep) {
                        AppStep.HOME -> AppStep.HOME
                        AppStep.PROCESS -> AppStep.HOME
                        AppStep.OUTPUT -> AppStep.PROCESS
                    }
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = when (currentStep) {
                        AppStep.HOME -> "Home"
                        AppStep.PROCESS -> "Back"
                        AppStep.OUTPUT -> "Back"
                    }
                )
            }
        }
    }
}
