package com.heronikostudios.metajammer.di

import androidx.work.WorkManager
import com.heronikostudios.metajammer.data.FileRepository
import com.heronikostudios.metajammer.data.HistoryRepository
import com.heronikostudios.metajammer.data.MetadataRepository
import com.heronikostudios.metajammer.data.SettingsRepository
import com.heronikostudios.metajammer.data.db.AppDatabase
import com.heronikostudios.metajammer.domain.usecase.ProcessFileUseCase
import com.heronikostudios.metajammer.domain.usecase.SaveFileUseCase
import com.heronikostudios.metajammer.domain.usecase.ShareFileUseCase
import com.heronikostudios.metajammer.ui.MainViewModel
import com.heronikostudios.metajammer.ui.history.HistoryViewModel
import com.heronikostudios.metajammer.ui.home.HomeViewModel
import com.heronikostudios.metajammer.ui.processing.ProcessingViewModel
import com.heronikostudios.metajammer.ui.quickscrub.QuickScrubHandler
import com.heronikostudios.metajammer.ui.settings.SettingsViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Database & DAOs
    single { AppDatabase.getInstance(androidContext()) }
    single { get<AppDatabase>().processedFileDao() }

    // Repositories
    single { SettingsRepository(androidContext()) }
    single { FileRepository(androidContext()) }
    single { MetadataRepository(get()) }
    single { HistoryRepository(get(), androidContext()) }

    // System Services & Use Cases
    single { WorkManager.getInstance(androidContext()) }
    single { ProcessFileUseCase(get()) }
    single { SaveFileUseCase(get()) }
    single { ShareFileUseCase() }

    // Handlers
    factory {
        QuickScrubHandler(
            metadataRepository = get(),
            processFileUseCase = get(),
            saveFileUseCase = get(),
            settingsRepository = get(),
            fileRepository = get(),
            workManager = get(),
            cacheDir = androidContext().cacheDir
        )
    }

    // ViewModels
    viewModel { HomeViewModel(fileRepository = get()) }
    viewModel {
        SettingsViewModel(
            settingsRepository = get(),
            fileRepository = get(),
            contentResolver = androidContext().contentResolver
        )
    }
    viewModel {
        ProcessingViewModel(
            metadataRepository = get(),
            fileRepository = get(),
            settingsRepository = get(),
            processFileUseCase = get(),
            saveFileUseCase = get(),
            workManager = get(),
            cacheDir = androidContext().cacheDir
        )
    }
    viewModel { HistoryViewModel(historyRepository = get(), shareFileUseCase = get()) }
    viewModel {
        MainViewModel(
            application = androidApplication(),
            fileRepository = get(),
            metadataRepository = get(),
            settingsRepository = get(),
            historyRepository = get(),
            workManager = get(),
            processFileUseCase = get(),
            saveFileUseCase = get(),
            homeViewModel = get(),
            settingsViewModel = get(),
            processingViewModel = get(),
            historyViewModel = get(),
            quickScrubHandler = get()
        )
    }
}
