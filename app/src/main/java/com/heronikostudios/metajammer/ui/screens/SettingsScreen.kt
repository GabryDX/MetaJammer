package com.heronikostudios.metajammer.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.heronikostudios.metajammer.domain.model.*

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onUseRandomFileNamesChanged: (Boolean) -> Unit,
    onDefaultSavingPathSelected: (Uri?) -> Unit,
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
    modifier: Modifier = Modifier
) {
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { 
        onDefaultSavingPathSelected(it) 
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
        SettingsCategory(title = "File Management") {
            SettingSwitchRow(
                title = "Use random file names",
                subtitle = "Generate randomized output names instead of using the original file name",
                checked = settings.useRandomFileNames,
                onCheckedChange = onUseRandomFileNamesChanged
            )

            HorizontalDivider()

            SettingFolderRow(
                title = "Default saving path",
                currentPath = settings.defaultSavingPath ?: "Pictures/MetaJammer",
                onSelect = { folderPicker.launch(null) },
                onReset = { onDefaultSavingPathSelected(null) },
                resetLabel = "Reset to Pictures/MetaJammer"
            )
        }

        SettingsCategory(title = "Image / File") {
            SettingSwitchRow(
                title = "Keep image orientation",
                subtitle = "Preserve image orientation metadata when possible",
                checked = settings.keepImageOrientation,
                onCheckedChange = onKeepImageOrientationChanged
            )

            SettingSwitchRow(
                title = "Share result as default",
                subtitle = "Saving will also trigger sharing",
                checked = settings.shareResultAsDefault,
                onCheckedChange = onShareResultAsDefaultChanged
            )

            HorizontalDivider()

            DialogSettingRow(
                title = "Default Prefix",
                value = settings.defaultPrefix.ifBlank { "Not set" },
                onClick = { activeDialog = SettingsDialog.Prefix(settings.defaultPrefix) }
            )

            DialogSettingRow(
                title = "Default Suffix",
                value = settings.defaultSuffix.ifBlank { "Not set" },
                onClick = { activeDialog = SettingsDialog.Suffix(settings.defaultSuffix) }
            )

            DialogSettingRow(
                title = "Embedded thumbnail",
                value = settings.thumbnailHandling.toReadableLabel(),
                onClick = { activeDialog = SettingsDialog.Thumbnail(settings.thumbnailHandling) }
            )

            SettingSwitchRow(
                title = "Enable map picker",
                subtitle = "Allow the app to connect to the internet to show OpenStreetMap for location selection",
                checked = settings.allowInternetForMap,
                onCheckedChange = onAllowInternetForMapChanged
            )
        }

        SettingsCategory(title = "Shared Files Behavior") {
            SettingSwitchRow(
                title = "Quick Scrub & Share",
                subtitle = "Automatically strip metadata and re-open Share Sheet for shared files",
                checked = settings.autoHandleSharedFiles,
                onCheckedChange = onAutoHandleSharedFilesChanged
            )

            DialogSettingRow(
                title = "Metadata action",
                value = settings.sharedFilesProcessingMode.toReadableLabel(),
                onClick = { activeDialog = SettingsDialog.SharedProcessingMode(settings.sharedFilesProcessingMode) }
            )

            DialogSettingRow(
                title = "Output action",
                value = settings.sharedFilesOutputAction.toReadableLabel(),
                onClick = { activeDialog = SettingsDialog.SharedOutputAction(settings.sharedFilesOutputAction) }
            )

            if (settings.sharedFilesOutputAction == SharedInputOutputAction.SAVE_TO_SHARED_FOLDER) {
                SettingFolderRow(
                    title = "Dedicated shared-files folder",
                    currentPath = settings.sharedFilesCustomPath ?: "No dedicated folder selected",
                    onSelect = { sharedFolderPicker.launch(null) },
                    onReset = { onSharedFilesCustomPathSelected(null) },
                    resetLabel = "Clear Dedicated Folder"
                )
            }
        }

        SettingsCategory(title = "UI") {
            DialogSettingRow(
                title = "Night mode",
                value = settings.nightMode.toReadableLabel(),
                onClick = { activeDialog = SettingsDialog.NightMode(settings.nightMode) }
            )

            SettingSwitchRow(
                title = "OLED mode",
                subtitle = "Use darker black tones for supported dark themes",
                checked = settings.oledMode,
                onCheckedChange = onOledModeChanged
            )
        }
    }

    // Centralized Dialog Controller
    activeDialog?.let { dialog ->
        HandleSettingsDialog(
            dialog = dialog,
            onDismiss = { activeDialog = null },
            onNightModeChanged = onNightModeChanged,
            onPrefixChanged = onDefaultPrefixChanged,
            onSuffixChanged = onDefaultSuffixChanged,
            onProcessingModeChanged = onSharedFilesProcessingModeChanged,
            onOutputActionChanged = onSharedFilesOutputActionChanged,
            onThumbnailHandlingChanged = onThumbnailHandlingChanged
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
            Button(onClick = onSelect, modifier = Modifier.weight(1f)) { Text("Select Folder") }
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
    data class NightMode(val current: NightModeSetting) : SettingsDialog()
    data class Prefix(val current: String) : SettingsDialog()
    data class Suffix(val current: String) : SettingsDialog()
    data class SharedProcessingMode(val current: ProcessingMode) : SettingsDialog()
    data class SharedOutputAction(val current: SharedInputOutputAction) : SettingsDialog()
    data class Thumbnail(val current: ThumbnailHandling) : SettingsDialog()
}

@Composable
private fun HandleSettingsDialog(
    dialog: SettingsDialog,
    onDismiss: () -> Unit,
    onNightModeChanged: (NightModeSetting) -> Unit,
    onPrefixChanged: (String) -> Unit,
    onSuffixChanged: (String) -> Unit,
    onProcessingModeChanged: (ProcessingMode) -> Unit,
    onOutputActionChanged: (SharedInputOutputAction) -> Unit,
    onThumbnailHandlingChanged: (ThumbnailHandling) -> Unit
) {
    when (dialog) {
        is SettingsDialog.Prefix -> TextFieldDialog(
            title = "Default Prefix",
            initialValue = dialog.current,
            onConfirm = onPrefixChanged,
            onDismiss = onDismiss
        )
        is SettingsDialog.Suffix -> TextFieldDialog(
            title = "Default Suffix",
            initialValue = dialog.current,
            onConfirm = onSuffixChanged,
            onDismiss = onDismiss
        )
        is SettingsDialog.NightMode -> SingleSelectDialog(
            title = "Night Mode",
            options = NightModeSetting.entries,
            selected = dialog.current,
            labelProvider = { it.toReadableLabel() },
            onConfirm = onNightModeChanged,
            onDismiss = onDismiss
        )
        is SettingsDialog.SharedProcessingMode -> SingleSelectDialog(
            title = "Metadata Action",
            options = ProcessingMode.entries,
            selected = dialog.current,
            labelProvider = { it.toReadableLabel() },
            onConfirm = onProcessingModeChanged,
            onDismiss = onDismiss
        )
        is SettingsDialog.SharedOutputAction -> SingleSelectDialog(
            title = "Output Action",
            options = SharedInputOutputAction.entries,
            selected = dialog.current,
            labelProvider = { it.toReadableLabel() },
            onConfirm = onOutputActionChanged,
            onDismiss = onDismiss
        )
        is SettingsDialog.Thumbnail -> SingleSelectDialog(
            title = "Embedded Thumbnail",
            options = ThumbnailHandling.entries,
            selected = dialog.current,
            labelProvider = { it.toReadableLabel() },
            onConfirm = onThumbnailHandlingChanged,
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
        confirmButton = { TextButton(onClick = { onConfirm(value); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun <T> SingleSelectDialog(
    title: String,
    options: List<T>,
    selected: T,
    labelProvider: (T) -> String,
    onConfirm: (T) -> Unit,
    onDismiss: () -> Unit
) {
    var currentSelection by remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = currentSelection == option,
                                onClick = { currentSelection = option }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = currentSelection == option, onClick = { currentSelection = option })
                        Text(text = labelProvider(option), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(currentSelection); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// Labels
private fun NightModeSetting.toReadableLabel() = when (this) {
    NightModeSetting.ALWAYS -> "Always"
    NightModeSetting.AUTOMATIC -> "Automatic"
    NightModeSetting.ONLY_LOW_BATTERY -> "Only low battery"
    NightModeSetting.NEVER -> "Never"
}

private fun ProcessingMode.toReadableLabel() = when (this) {
    ProcessingMode.POISON_METADATA -> "Poison metadata (Fake info)"
    ProcessingMode.REMOVE_METADATA -> "Remove metadata (Full strip)"
}

private fun SharedInputOutputAction.toReadableLabel() = when (this) {
    SharedInputOutputAction.SAVE_TO_DEFAULT_FOLDER -> "Save to default folder"
    SharedInputOutputAction.SAVE_TO_SHARED_FOLDER -> "Save to dedicated folder"
    SharedInputOutputAction.SHARE_TO_ANOTHER_APP -> "Re-share sanitized files"
}

private fun ThumbnailHandling.toReadableLabel() = when (this) {
    ThumbnailHandling.REMOVE -> "Remove (Maximum Privacy)"
    ThumbnailHandling.KEEP_SCRUBBED -> "Keep (Metadata cleared)"
}
