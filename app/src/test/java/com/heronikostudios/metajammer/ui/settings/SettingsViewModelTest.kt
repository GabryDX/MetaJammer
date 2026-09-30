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
        _settings.update { it.copy(locationPreset = preset, selectedCustomLocationPresetId = null) }
    }
    override suspend fun selectLocationPresetTarget(target: com.heronikostudios.metajammer.domain.model.LocationPresetTarget) {
        _settings.update {
            if (target is com.heronikostudios.metajammer.domain.model.CustomLocationPreset) {
                it.copy(selectedCustomLocationPresetId = target.id)
            } else if (target is com.heronikostudios.metajammer.domain.model.LocationPreset) {
                it.copy(locationPreset = target, selectedCustomLocationPresetId = null)
            } else {
                it
            }
        }
    }
    override suspend fun addCustomLocationPreset(preset: com.heronikostudios.metajammer.domain.model.CustomLocationPreset) {
        _settings.update {
            it.copy(
                customLocationPresets = it.customLocationPresets.filterNot { p -> p.id == preset.id } + preset,
                selectedCustomLocationPresetId = preset.id
            )
        }
    }
    override suspend fun removeCustomLocationPreset(presetId: String) {
        _settings.update {
            it.copy(
                customLocationPresets = it.customLocationPresets.filterNot { p -> p.id == presetId },
                selectedCustomLocationPresetId = if (it.selectedCustomLocationPresetId == presetId) null else it.selectedCustomLocationPresetId
            )
        }
    }
    override suspend fun setStripGps(enabled: Boolean) {
        _settings.update { it.copy(stripGps = enabled) }
    }
    override suspend fun setStripDeviceModel(enabled: Boolean) {
        _settings.update { it.copy(stripDeviceModel = enabled) }
    }
    override suspend fun setStripDateTime(enabled: Boolean) {
        _settings.update { it.copy(stripDateTime = enabled) }
    }
    override suspend fun setStripCameraSettings(enabled: Boolean) {
        _settings.update { it.copy(stripCameraSettings = enabled) }
    }
    override suspend fun setStripComments(enabled: Boolean) {
        _settings.update { it.copy(stripComments = enabled) }
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

        viewModel.setStripGps(false)
        assertFalse(settingsRepository.appSettingsFlow.first().stripGps)

        viewModel.setStripDeviceModel(false)
        assertFalse(settingsRepository.appSettingsFlow.first().stripDeviceModel)

        viewModel.setStripDateTime(false)
        assertFalse(settingsRepository.appSettingsFlow.first().stripDateTime)

        viewModel.setStripCameraSettings(false)
        assertFalse(settingsRepository.appSettingsFlow.first().stripCameraSettings)

        viewModel.setStripComments(false)
        assertFalse(settingsRepository.appSettingsFlow.first().stripComments)
    }

    @Test
    fun testCustomLocationPresetManagement() = runTest {
        val initialSettings = settingsRepository.appSettingsFlow.first()
        assertTrue(initialSettings.customLocationPresets.isEmpty())
        assertEquals(com.heronikostudios.metajammer.domain.model.LocationPreset.RANDOM, initialSettings.activeLocationPreset)

        // Add custom location preset
        viewModel.addCustomLocationPreset("Secret Base", 45.1234, 9.5678)
        val afterAdd = settingsRepository.appSettingsFlow.first()
        assertEquals(1, afterAdd.customLocationPresets.size)
        val customPreset = afterAdd.customLocationPresets.first()
        assertEquals("Secret Base", customPreset.name)
        assertEquals(45.1234, customPreset.latitude, 0.0001)
        assertEquals(9.5678, customPreset.longitude, 0.0001)
        assertEquals(customPreset.id, afterAdd.selectedCustomLocationPresetId)
        assertEquals(customPreset, afterAdd.activeLocationPreset)

        // Switch back to built-in preset
        viewModel.selectLocationPresetTarget(com.heronikostudios.metajammer.domain.model.LocationPreset.PARIS)
        val afterSwitch = settingsRepository.appSettingsFlow.first()
        assertEquals(com.heronikostudios.metajammer.domain.model.LocationPreset.PARIS, afterSwitch.activeLocationPreset)
        assertNull(afterSwitch.selectedCustomLocationPresetId)

        // Switch back to custom preset
        viewModel.selectLocationPresetTarget(customPreset)
        val afterSwitchCustom = settingsRepository.appSettingsFlow.first()
        assertEquals(customPreset.id, afterSwitchCustom.selectedCustomLocationPresetId)
        assertEquals(customPreset, afterSwitchCustom.activeLocationPreset)

        // Delete custom preset
        viewModel.removeCustomLocationPreset(customPreset.id)
        val afterDelete = settingsRepository.appSettingsFlow.first()
        assertTrue(afterDelete.customLocationPresets.isEmpty())
        assertEquals(com.heronikostudios.metajammer.domain.model.LocationPreset.PARIS, afterDelete.activeLocationPreset)
    }
}
