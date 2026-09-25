package com.heronikostudios.metajammer.ui.history

import android.app.Application
import android.content.Context
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heronikostudios.metajammer.data.SettingsRepository
import com.heronikostudios.metajammer.domain.model.ProcessedFileLog
import com.heronikostudios.metajammer.domain.usecase.ShareFileUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel managing processed file history display, deletion, and sharing.
 */
class HistoryViewModel(
    private val settingsRepository: SettingsRepository,
    private val shareFileUseCase: ShareFileUseCase = ShareFileUseCase()
) : ViewModel() {

    constructor(application: Application) : this(
        settingsRepository = SettingsRepository(application.applicationContext)
    )

    val processedFilesHistory: StateFlow<List<ProcessedFileLog>> = settingsRepository.processedFilesLog
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearProcessedFilesHistory(clearedNotice: String? = null) {
        viewModelScope.launch {
            settingsRepository.clearProcessedFilesLog()
            clearedNotice?.let { _message.value = it }
        }
    }

    fun shareHistoryFile(context: Context, log: ProcessedFileLog) {
        val uri = log.uri.toUri()
        shareFileUseCase.shareUri(
            context = context,
            uri = uri,
            mimeType = log.mimeType ?: "*/*"
        )
    }

    fun clearMessage() {
        _message.value = null
    }
}
