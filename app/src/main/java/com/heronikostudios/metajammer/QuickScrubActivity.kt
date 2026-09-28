package com.heronikostudios.metajammer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.heronikostudios.metajammer.data.FileRepository
import com.heronikostudios.metajammer.data.SettingsRepository
import com.heronikostudios.metajammer.domain.model.AppSettings
import com.heronikostudios.metajammer.domain.usecase.ShareFileUseCase
import com.heronikostudios.metajammer.ui.quickscrub.QuickScrubHandler
import com.heronikostudios.metajammer.ui.screens.QuickScrubScreen
import com.heronikostudios.metajammer.ui.theme.MetaJammerTheme
import com.heronikostudios.metajammer.util.IntentUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

/**
 * Dedicated, ultra-fast transient activity for incoming shared media.
 * Bypasses the entire navigation graph and main ViewModels, rendering
 * the QuickScrub HUD immediately on frame 1.
 */
class QuickScrubActivity : AppCompatActivity() {

    private val settingsRepository: SettingsRepository by inject()
    private val quickScrubHandler: QuickScrubHandler by inject()
    private val fileRepository: FileRepository by inject()
    private val shareFileUseCase: ShareFileUseCase by inject()

    private var scrubJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Protect against tapjacking (overlay) attacks
        findViewById<android.view.View>(android.R.id.content)?.filterTouchesWhenObscured = true
        enableEdgeToEdge()

        val sharedUris = IntentUtils.extractSharedUris(intent)
        if (sharedUris.isEmpty()) {
            finish()
            return
        }

        var appSettings by mutableStateOf(AppSettings())
        var statusMessage by mutableStateOf<String?>(null)
        var fileCount by mutableIntStateOf(sharedUris.size)

        setContent {
            MetaJammerTheme(
                nightModeSetting = appSettings.nightMode,
                oledMode = appSettings.oledMode,
                dynamicColor = appSettings.useDynamicColor
            ) {
                QuickScrubScreen(
                    fileCount = fileCount,
                    processingMode = appSettings.sharedFilesProcessingMode,
                    statusText = statusMessage,
                    onCancel = {
                        scrubJob?.cancel()
                        finish()
                    }
                )
            }
        }

        scrubJob = lifecycleScope.launch {
            val settings = runCatching { settingsRepository.appSettingsFlow.first() }
                .getOrDefault(AppSettings())
            appSettings = settings

            // If user disabled automatic handling, forward to MainActivity for manual review
            if (!settings.autoHandleSharedFiles) {
                val forwardIntent = Intent(this@QuickScrubActivity, MainActivity::class.java).apply {
                    action = intent.action
                    type = intent.type
                    putExtras(intent)
                    addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
                }
                startActivity(forwardIntent)
                finish()
                return@launch
            }

            // Ingest files in parallel on Dispatchers.IO
            val selectedFiles = withContext(Dispatchers.IO) {
                sharedUris.distinct().map { uri ->
                    async { fileRepository.getSelectedFile(uri) }
                }.awaitAll()
            }
            fileCount = selectedFiles.size

            quickScrubHandler.executeQuickScrub(
                files = selectedFiles,
                appSettings = settings,
                onShareFilesReady = { files, mimeType ->
                    shareFileUseCase.shareFiles(
                        context = this@QuickScrubActivity,
                        files = files,
                        mimeType = mimeType
                    )
                    finish()
                },
                onStatusMessage = { msg ->
                    statusMessage = msg
                }
            )
        }
    }

    override fun onDestroy() {
        scrubJob?.cancel()
        super.onDestroy()
    }
}
