package com.heronikostudios.metajammer.ui.settings

import android.content.Context
import com.heronikostudios.metajammer.data.FileRepository
import com.heronikostudios.metajammer.data.SettingsRepository
import com.heronikostudios.metajammer.domain.model.AppSettings
import com.heronikostudios.metajammer.domain.model.ThumbnailHandling
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

private class FakeSettingsRepository(context: Context) : SettingsRepository(context) {
    private val _settings = MutableStateFlow(AppSettings())
    override val appSettingsFlow: Flow<AppSettings> = _settings.asStateFlow()

    override suspend fun setUseRandomFileNames(enabled: Boolean) {
        _settings.update { it.copy(useRandomFileNames = enabled) }
    }
    override suspend fun setDefaultPrefix(prefix: String) {
        _settings.update { it.copy(defaultPrefix = prefix) }
    }
    override suspend fun setDefaultSuffix(suffix: String) {
        _settings.update { it.copy(defaultSuffix = suffix) }
    }
    override suspend fun setKeepImageOrientation(enabled: Boolean) {
        _settings.update { it.copy(keepImageOrientation = enabled) }
    }
    override suspend fun setThumbnailHandling(handling: ThumbnailHandling) {
        _settings.update { it.copy(thumbnailHandling = handling) }
    }
    override suspend fun setPoisoningProfile(profile: com.heronikostudios.metajammer.domain.model.PoisoningProfile) {
        _settings.update { it.copy(poisoningProfile = profile) }
    }
    override suspend fun setLocationPreset(preset: com.heronikostudios.metajammer.domain.model.LocationPreset) {
        _settings.update { it.copy(locationPreset = preset) }
    }
    override suspend fun performMaintenance() {}
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var settingsRepository: FakeSettingsRepository
    private lateinit var fileRepository: FileRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = RuntimeEnvironment.getApplication()
        settingsRepository = FakeSettingsRepository(context)
        fileRepository = FileRepository(context)
        viewModel = SettingsViewModel(
            settingsRepository = settingsRepository,
            fileRepository = fileRepository,
            contentResolver = context.contentResolver
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testSettingsTogglesUpdateRepositoryAndState() = runTest {
        // Toggle random file names
        viewModel.setUseRandomFileNames(true)
        assertTrue(settingsRepository.appSettingsFlow.first().useRandomFileNames)

        // Set output prefix and suffix
        viewModel.setDefaultPrefix("CLEAN_")
        assertEquals("CLEAN_", settingsRepository.appSettingsFlow.first().defaultPrefix)

        viewModel.setDefaultSuffix("_SAFE")
        assertEquals("_SAFE", settingsRepository.appSettingsFlow.first().defaultSuffix)

        // Toggle orientation and thumbnails
        viewModel.setKeepImageOrientation(false)
        assertFalse(settingsRepository.appSettingsFlow.first().keepImageOrientation)

        viewModel.setThumbnailHandling(ThumbnailHandling.KEEP_ORIGINAL)
        assertEquals(ThumbnailHandling.KEEP_ORIGINAL, settingsRepository.appSettingsFlow.first().thumbnailHandling)

        viewModel.setPoisoningProfile(com.heronikostudios.metajammer.domain.model.PoisoningProfile.PRO_MIRRORLESS)
        assertEquals(com.heronikostudios.metajammer.domain.model.PoisoningProfile.PRO_MIRRORLESS, settingsRepository.appSettingsFlow.first().poisoningProfile)

        viewModel.setLocationPreset(com.heronikostudios.metajammer.domain.model.LocationPreset.TOKYO)
        assertEquals(com.heronikostudios.metajammer.domain.model.LocationPreset.TOKYO, settingsRepository.appSettingsFlow.first().locationPreset)
    }

    @Test
    fun testClearMessageResetsMessageState() {
        viewModel.clearMessage()
        assertNull(viewModel.message.value)
    }
}
