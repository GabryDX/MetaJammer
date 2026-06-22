package com.heronikostudios.metajammer.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.heronikostudios.metajammer.R
import com.heronikostudios.metajammer.domain.model.*

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onUseRandomFileNamesChanged: (Boolean) -> Unit,
    onFolderStructureChanged: (FolderStructure) -> Unit,
    onUseSubfoldersInUnifiedChanged: (Boolean) -> Unit,
    onUnifiedSavingPathSelected: (Uri?) -> Unit,
    onPicturesSavingPathSelected: (Uri?) -> Unit,
    onMusicSavingPathSelected: (Uri?) -> Unit,
    onMoviesSavingPathSelected: (Uri?) -> Unit,
    onDocumentsSavingPathSelected: (Uri?) -> Unit,
    onKeepImageOrientationChanged: (Boolean) -> Unit,
    onShareResultAsDefaultChanged: (Boolean) -> Unit,
    onDefaultPrefixChanged: (String) -> Unit,
    onDefaultSuffixChanged: (String) -> Unit,
    onNightModeChanged: (NightModeSetting) -> Unit,
    onOledModeChanged: (Boolean) -> Unit,
    onAutoHandleSharedFilesChanged: (Boolean) -> Unit,
    onSharedFilesProcessingModeChanged: (ProcessingMode) -> Unit,
    onSharedFilesOutputActionChanged: (SharedInputOutputAction) -> Unit,
    onSharedFilesCustomPathSelected: (Uri?) -> Unit,
    onThumbnailHandlingChanged: (ThumbnailHandling) -> Unit,
    onAllowInternetForMapChanged: (Boolean) -> Unit,
    onUseNearbyScrambleChanged: (Boolean) -> Unit,
    onLanguageChanged: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    val unifiedFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { 
        onUnifiedSavingPathSelected(it) 
    }
    val picturesFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { 
        onPicturesSavingPathSelected(it) 
    }
    val musicFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { 
        onMusicSavingPathSelected(it) 
    }
    val moviesFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { 
        onMoviesSavingPathSelected(it) 
    }
    val documentsFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { 
        onDocumentsSavingPathSelected(it) 
    }
    val sharedFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { 
        onSharedFilesCustomPathSelected(it) 
    }

    // Single state for active dialog to reduce memory footprint and avoid overlaps
    var activeDialog by remember { mutableStateOf<SettingsDialog?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsCategory(title = stringResource(R.string.category_folder_management)) {
            DialogSettingRow(
                title = stringResource(R.string.setting_folder_structure_title),
                value = settings.folderStructure.toReadableLabel(),
                onClick = { activeDialog = SettingsDialog.FolderStructureDialog(settings.folderStructure) }
            )

            if (settings.folderStructure == FolderStructure.UNIFIED) {
                SettingSwitchRow(
                    title = stringResource(R.string.setting_use_subfolders_title),
                    subtitle = stringResource(R.string.setting_use_subfolders_sub),
                    checked = settings.useSubfoldersInUnified,
                    onCheckedChange = onUseSubfoldersInUnifiedChanged
                )

                SettingFolderRow(
                    title = stringResource(R.string.setting_unified_path_title),
                    currentPath = settings.unifiedSavingPath ?: "Download/MetaJammer",
                    onSelect = { unifiedFolderPicker.launch(null) },
                    onReset = { onUnifiedSavingPathSelected(null) },
                    resetLabel = stringResource(R.string.setting_reset_path_download)
                )
            } else {
                DialogSettingRow(
                    title = stringResource(R.string.setting_configure_split_folders),
                    value = stringResource(R.string.setting_split_folders_desc),
                    onClick = { activeDialog = SettingsDialog.SplitFoldersConfig }
                )
            }
        }

        SettingsCategory(title = stringResource(R.string.category_file_management)) {
            SettingSwitchRow(
                title = stringResource(R.string.setting_random_names_title),
                subtitle = stringResource(R.string.setting_random_names_sub),
                checked = settings.useRandomFileNames,
                onCheckedChange = onUseRandomFileNamesChanged
            )
        }

        SettingsCategory(title = stringResource(R.string.category_image_file)) {
            SettingSwitchRow(
                title = stringResource(R.string.setting_keep_orientation_title),
                subtitle = stringResource(R.string.setting_keep_orientation_sub),
                checked = settings.keepImageOrientation,
                onCheckedChange = onKeepImageOrientationChanged
            )

            SettingSwitchRow(
                title = stringResource(R.string.setting_share_default_title),
                subtitle = stringResource(R.string.setting_share_default_sub),
                checked = settings.shareResultAsDefault,
                onCheckedChange = onShareResultAsDefaultChanged
            )

            HorizontalDivider()

            DialogSettingRow(
                title = stringResource(R.string.setting_prefix_title),
                value = settings.defaultPrefix.ifBlank { stringResource(R.string.setting_not_set) },
                onClick = { activeDialog = SettingsDialog.Prefix(settings.defaultPrefix) }
            )

            DialogSettingRow(
                title = stringResource(R.string.setting_suffix_title),
                value = settings.defaultSuffix.ifBlank { stringResource(R.string.setting_not_set) },
                onClick = { activeDialog = SettingsDialog.Suffix(settings.defaultSuffix) }
            )

            SettingSwitchRow(
                title = stringResource(R.string.setting_keep_thumbnails_title),
                subtitle = stringResource(R.string.setting_keep_thumbnails_sub),
                checked = settings.thumbnailHandling == ThumbnailHandling.KEEP_ORIGINAL,
                onCheckedChange = { isChecked ->
                    onThumbnailHandlingChanged(if (isChecked) ThumbnailHandling.KEEP_ORIGINAL else ThumbnailHandling.REMOVE)
                }
            )

            if (settings.thumbnailHandling == ThumbnailHandling.KEEP_ORIGINAL) {
                SettingWarningCard(
                    message = stringResource(R.string.setting_thumbnails_warning)
                )
            }

            SettingSwitchRow(
                title = stringResource(R.string.setting_enable_map_title),
                subtitle = stringResource(R.string.setting_enable_map_sub),
                checked = settings.allowInternetForMap,
                onCheckedChange = onAllowInternetForMapChanged
            )

            SettingSwitchRow(
                title = stringResource(R.string.setting_nearby_scramble_title),
                subtitle = stringResource(R.string.setting_nearby_scramble_sub),
                checked = settings.useNearbyScramble,
                onCheckedChange = onUseNearbyScrambleChanged
            )
        }

        SettingsCategory(title = stringResource(R.string.category_shared_files)) {
            SettingSwitchRow(
                title = stringResource(R.string.setting_quick_scrub_title),
                subtitle = stringResource(R.string.setting_quick_scrub_sub),
                checked = settings.autoHandleSharedFiles,
                onCheckedChange = onAutoHandleSharedFilesChanged
            )

            DialogSettingRow(
                title = stringResource(R.string.setting_metadata_action_title),
                value = settings.sharedFilesProcessingMode.toReadableLabel(),
                onClick = { activeDialog = SettingsDialog.SharedProcessingMode(settings.sharedFilesProcessingMode) }
            )

            DialogSettingRow(
                title = stringResource(R.string.setting_output_action_title),
                value = settings.sharedFilesOutputAction.toReadableLabel(),
                onClick = { activeDialog = SettingsDialog.SharedOutputAction(settings.sharedFilesOutputAction) }
            )

            if (settings.sharedFilesOutputAction == SharedInputOutputAction.SAVE_TO_SHARED_FOLDER) {
                SettingFolderRow(
                    title = stringResource(R.string.setting_dedicated_folder_title),
                    currentPath = settings.sharedFilesCustomPath ?: stringResource(R.string.setting_no_folder_selected),
                    onSelect = { sharedFolderPicker.launch(null) },
                    onReset = { onSharedFilesCustomPathSelected(null) },
                    resetLabel = stringResource(R.string.setting_clear_folder)
                )
            }
        }

        SettingsCategory(title = stringResource(R.string.category_ui)) {
            DialogSettingRow(
                title = stringResource(R.string.setting_night_mode_title),
                value = settings.nightMode.toReadableLabel(),
                onClick = { activeDialog = SettingsDialog.NightMode(settings.nightMode) }
            )

            DialogSettingRow(
                title = stringResource(R.string.setting_language_title),
                value = settings.language.toReadableLabel(),
                onClick = { activeDialog = SettingsDialog.Language(settings.language) }
            )

            SettingSwitchRow(
                title = stringResource(R.string.setting_oled_mode_title),
                subtitle = stringResource(R.string.setting_oled_mode_sub),
                checked = settings.oledMode,
                onCheckedChange = onOledModeChanged
            )
        }
    }

    // Centralized Dialog Controller
    val currentDialog = activeDialog
    if (currentDialog != null) {
        HandleSettingsDialog(
            dialog = currentDialog,
            settings = settings,
            onDismiss = { if (activeDialog != null) activeDialog = null },
            onFolderStructureChanged = onFolderStructureChanged,
            onPicturesSavingPathSelected = { picturesFolderPicker.launch(null) },
            onMusicSavingPathSelected = { musicFolderPicker.launch(null) },
            onMoviesSavingPathSelected = { moviesFolderPicker.launch(null) },
            onDocumentsSavingPathSelected = { documentsFolderPicker.launch(null) },
            onPicturesReset = { onPicturesSavingPathSelected(null) },
            onMusicReset = { onMusicSavingPathSelected(null) },
            onMoviesReset = { onMoviesSavingPathSelected(null) },
            onDocumentsReset = { onDocumentsSavingPathSelected(null) },
            onNightModeChanged = onNightModeChanged,
            onPrefixChanged = onDefaultPrefixChanged,
            onSuffixChanged = onDefaultSuffixChanged,
            onProcessingModeChanged = onSharedFilesProcessingModeChanged,
            onOutputActionChanged = onSharedFilesOutputActionChanged,
            onLanguageChanged = onLanguageChanged
        )
    }
}

@Composable
private fun SettingsCategory(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingWarningCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = stringResource(R.string.warning),
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SettingFolderRow(
    title: String,
    currentPath: String,
    onSelect: () -> Unit,
    onReset: () -> Unit,
    resetLabel: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Text(text = currentPath, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSelect, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.setting_select_folder)) }
            OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text(resetLabel) }
        }
    }
}

@Composable
private fun DialogSettingRow(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(text = value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private sealed class SettingsDialog {
    data class FolderStructureDialog(val current: FolderStructure) : SettingsDialog()
    object SplitFoldersConfig : SettingsDialog()
    data class NightMode(val current: NightModeSetting) : SettingsDialog()
    data class Prefix(val current: String) : SettingsDialog()
    data class Suffix(val current: String) : SettingsDialog()
    data class SharedProcessingMode(val current: ProcessingMode) : SettingsDialog()
    data class SharedOutputAction(val current: SharedInputOutputAction) : SettingsDialog()
    data class Language(val current: AppLanguage) : SettingsDialog()
}

@Composable
private fun HandleSettingsDialog(
    dialog: SettingsDialog,
    settings: AppSettings, // Pass settings for SplitFoldersConfig
    onDismiss: () -> Unit,
    onFolderStructureChanged: (FolderStructure) -> Unit,
    onPicturesSavingPathSelected: () -> Unit,
    onMusicSavingPathSelected: () -> Unit,
    onMoviesSavingPathSelected: () -> Unit,
    onDocumentsSavingPathSelected: () -> Unit,
    onPicturesReset: () -> Unit,
    onMusicReset: () -> Unit,
    onMoviesReset: () -> Unit,
    onDocumentsReset: () -> Unit,
    onNightModeChanged: (NightModeSetting) -> Unit,
    onPrefixChanged: (String) -> Unit,
    onSuffixChanged: (String) -> Unit,
    onProcessingModeChanged: (ProcessingMode) -> Unit,
    onOutputActionChanged: (SharedInputOutputAction) -> Unit,
    onLanguageChanged: (AppLanguage) -> Unit
) {
    when (dialog) {
        is SettingsDialog.FolderStructureDialog -> SingleSelectDialog(
            title = stringResource(R.string.setting_folder_structure_title),
            options = FolderStructure.entries,
            selected = dialog.current,
            labelProvider = { it.toReadableLabel() },
            onConfirm = onFolderStructureChanged,
            onDismiss = onDismiss
        )
        SettingsDialog.SplitFoldersConfig -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.setting_configure_split_folders)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SettingFolderRow(
                        title = stringResource(R.string.setting_pictures_path_title),
                        currentPath = settings.picturesSavingPath ?: "Pictures/MetaJammer",
                        onSelect = onPicturesSavingPathSelected,
                        onReset = onPicturesReset,
                        resetLabel = stringResource(R.string.setting_reset_path_pictures)
                    )
                    SettingFolderRow(
                        title = stringResource(R.string.setting_music_path_title),
                        currentPath = settings.musicSavingPath ?: "Music/MetaJammer",
                        onSelect = onMusicSavingPathSelected,
                        onReset = onMusicReset,
                        resetLabel = stringResource(R.string.setting_reset_path_music)
                    )
                    SettingFolderRow(
                        title = stringResource(R.string.setting_movies_path_title),
                        currentPath = settings.moviesSavingPath ?: "Movies/MetaJammer",
                        onSelect = onMoviesSavingPathSelected,
                        onReset = onMoviesReset,
                        resetLabel = stringResource(R.string.setting_reset_path_movies)
                    )
                    SettingFolderRow(
                        title = stringResource(R.string.setting_documents_path_title),
                        currentPath = settings.documentsSavingPath ?: "Documents/MetaJammer",
                        onSelect = onDocumentsSavingPathSelected,
                        onReset = onDocumentsReset,
                        resetLabel = stringResource(R.string.setting_reset_path_documents)
                    )
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } }
        )
        is SettingsDialog.Prefix -> TextFieldDialog(
            title = stringResource(R.string.setting_prefix_title),
            initialValue = dialog.current,
            onConfirm = onPrefixChanged,
            onDismiss = onDismiss
        )
        is SettingsDialog.Suffix -> TextFieldDialog(
            title = stringResource(R.string.setting_suffix_title),
            initialValue = dialog.current,
            onConfirm = onSuffixChanged,
            onDismiss = onDismiss
        )
        is SettingsDialog.NightMode -> SingleSelectDialog(
            title = stringResource(R.string.setting_night_mode_title),
            options = NightModeSetting.entries,
            selected = dialog.current,
            labelProvider = { it.toReadableLabel() },
            onConfirm = onNightModeChanged,
            onDismiss = onDismiss
        )
        is SettingsDialog.SharedProcessingMode -> SingleSelectDialog(
            title = stringResource(R.string.setting_metadata_action_title),
            options = ProcessingMode.entries,
            selected = dialog.current,
            labelProvider = { it.toReadableLabel() },
            onConfirm = onProcessingModeChanged,
            onDismiss = onDismiss
        )
        is SettingsDialog.SharedOutputAction -> SingleSelectDialog(
            title = stringResource(R.string.setting_output_action_title),
            options = SharedInputOutputAction.entries,
            selected = dialog.current,
            labelProvider = { it.toReadableLabel() },
            onConfirm = onOutputActionChanged,
            onDismiss = onDismiss
        )

        is SettingsDialog.Language -> SingleSelectDialog(
            title = stringResource(R.string.setting_language_title),
            options = AppLanguage.entries,
            selected = dialog.current,
            labelProvider = { it.toReadableLabel() },
            onConfirm = onLanguageChanged,
            onDismiss = onDismiss
        )
    }
}

@Composable
private fun TextFieldDialog(
    title: String,
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(value); onDismiss() }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun <T> SingleSelectDialog(
    title: String,
    options: List<T>,
    selected: T,
    labelProvider: @Composable (T) -> String,
    onConfirm: (T) -> Unit,
    onDismiss: () -> Unit
) {
    var currentSelection by remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = currentSelection == option,
                                onClick = { currentSelection = option }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = currentSelection == option, onClick = { currentSelection = option })
                        Text(text = labelProvider(option), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(currentSelection); onDismiss() }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

// Labels
@Composable
private fun FolderStructure.toReadableLabel() = when (this) {
    FolderStructure.UNIFIED -> stringResource(R.string.setting_folder_structure_unified)
    FolderStructure.SPLIT -> stringResource(R.string.setting_folder_structure_split)
}

@Composable
private fun NightModeSetting.toReadableLabel() = when (this) {
    NightModeSetting.ALWAYS -> stringResource(R.string.night_mode_always)
    NightModeSetting.AUTOMATIC -> stringResource(R.string.night_mode_automatic)
    NightModeSetting.ONLY_LOW_BATTERY -> stringResource(R.string.night_mode_low_battery)
    NightModeSetting.NEVER -> stringResource(R.string.night_mode_never)
}

@Composable
private fun ProcessingMode.toReadableLabel() = when (this) {
    ProcessingMode.POISON_METADATA -> stringResource(R.string.processing_mode_poison)
    ProcessingMode.REMOVE_METADATA -> stringResource(R.string.processing_mode_remove)
}

@Composable
private fun SharedInputOutputAction.toReadableLabel() = when (this) {
    SharedInputOutputAction.SAVE_TO_DEFAULT_FOLDER -> stringResource(R.string.output_action_default_folder)
    SharedInputOutputAction.SAVE_TO_SHARED_FOLDER -> stringResource(R.string.output_action_shared_folder)
    SharedInputOutputAction.SHARE_TO_ANOTHER_APP -> stringResource(R.string.output_action_reshare)
}

@Composable
private fun AppLanguage.toReadableLabel() = when (this) {
    AppLanguage.SYSTEM -> stringResource(R.string.language_system)
    AppLanguage.ENGLISH -> stringResource(R.string.language_en)
    AppLanguage.CHINESE -> stringResource(R.string.language_zh)
    AppLanguage.SPANISH -> stringResource(R.string.language_es)
    AppLanguage.HINDI -> stringResource(R.string.language_hi)
    AppLanguage.ARABIC -> stringResource(R.string.language_ar)
    AppLanguage.FRENCH -> stringResource(R.string.language_fr)
    AppLanguage.PORTUGUESE -> stringResource(R.string.language_pt)
    AppLanguage.RUSSIAN -> stringResource(R.string.language_ru)
    AppLanguage.JAPANESE -> stringResource(R.string.language_ja)
    AppLanguage.GERMAN -> stringResource(R.string.language_de)
    AppLanguage.KOREAN -> stringResource(R.string.language_ko)
    AppLanguage.INDONESIAN -> stringResource(R.string.language_in)
    AppLanguage.ITALIAN -> stringResource(R.string.language_it)
    AppLanguage.TURKISH -> stringResource(R.string.language_tr)
    AppLanguage.VIETNAMESE -> stringResource(R.string.language_vi)
    AppLanguage.THAI -> stringResource(R.string.language_th)
    AppLanguage.POLISH -> stringResource(R.string.language_pl)
    AppLanguage.UKRAINIAN -> stringResource(R.string.language_uk)
    AppLanguage.DUTCH -> stringResource(R.string.language_nl)
    AppLanguage.ROMANIAN -> stringResource(R.string.language_ro)
    AppLanguage.PERSIAN -> stringResource(R.string.language_fa)
    AppLanguage.GREEK -> stringResource(R.string.language_el)
    AppLanguage.HEBREW -> stringResource(R.string.language_iw)
    AppLanguage.LATIN -> stringResource(R.string.language_la)
}
