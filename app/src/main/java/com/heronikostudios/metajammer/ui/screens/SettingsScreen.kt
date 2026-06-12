package com.heronikostudios.metajammer.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.heronikostudios.metajammer.domain.model.AppSettings
import com.heronikostudios.metajammer.domain.model.NightModeSetting
import com.heronikostudios.metajammer.domain.model.ProcessingMode
import com.heronikostudios.metajammer.domain.model.SharedInputOutputAction
import com.heronikostudios.metajammer.domain.model.ThumbnailHandling

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
    modifier: Modifier = Modifier
) {
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        onDefaultSavingPathSelected(uri)
    }

    val sharedFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        onSharedFilesCustomPathSelected(uri)
    }

    var showNightModeDialog by remember { mutableStateOf(false) }
    var showPrefixDialog by remember { mutableStateOf(false) }
    var showSuffixDialog by remember { mutableStateOf(false) }
    var showSharedProcessingModeDialog by remember { mutableStateOf(false) }
    var showSharedOutputActionDialog by remember { mutableStateOf(false) }
    var showThumbnailHandlingDialog by remember { mutableStateOf(false) }

    var tempNightMode by remember(settings.nightMode) { mutableStateOf(settings.nightMode) }
    var tempPrefix by remember(settings.defaultPrefix) { mutableStateOf(settings.defaultPrefix) }
    var tempSuffix by remember(settings.defaultSuffix) { mutableStateOf(settings.defaultSuffix) }
    var tempSharedProcessingMode by remember(settings.sharedFilesProcessingMode) {
        mutableStateOf(settings.sharedFilesProcessingMode)
    }
    var tempSharedOutputAction by remember(settings.sharedFilesOutputAction) {
        mutableStateOf(settings.sharedFilesOutputAction)
    }
    var tempThumbnailHandling by remember(settings.thumbnailHandling) {
        mutableStateOf(settings.thumbnailHandling)
    }

    LaunchedEffect(settings.nightMode) {
        tempNightMode = settings.nightMode
    }
    LaunchedEffect(settings.defaultPrefix) {
        tempPrefix = settings.defaultPrefix
    }
    LaunchedEffect(settings.defaultSuffix) {
        tempSuffix = settings.defaultSuffix
    }
    LaunchedEffect(settings.sharedFilesProcessingMode) {
        tempSharedProcessingMode = settings.sharedFilesProcessingMode
    }
    LaunchedEffect(settings.sharedFilesOutputAction) {
        tempSharedOutputAction = settings.sharedFilesOutputAction
    }
    LaunchedEffect(settings.thumbnailHandling) {
        tempThumbnailHandling = settings.thumbnailHandling
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "File management",
                    style = MaterialTheme.typography.titleMedium
                )

                SettingSwitchRow(
                    title = "Use random file names",
                    subtitle = "Generate randomized output names instead of using the original file name",
                    checked = settings.useRandomFileNames,
                    onCheckedChange = onUseRandomFileNamesChanged
                )

                HorizontalDivider()

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Default saving path",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Text(
                        text = settings.defaultSavingPath ?: "Pictures/MetaJammer",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Button(
                        onClick = { folderPicker.launch(null) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Select Default Saving Path")
                    }

                    Button(
                        onClick = { onDefaultSavingPathSelected(null) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reset to Pictures/MetaJammer")
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Image / File",
                    style = MaterialTheme.typography.titleMedium
                )

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

                DialogSettingRow(
                    title = "Default Prefix",
                    value = settings.defaultPrefix.ifBlank { "Not set" },
                    onClick = {
                        tempPrefix = settings.defaultPrefix
                        showPrefixDialog = true
                    }
                )

                DialogSettingRow(
                    title = "Default Suffix",
                    value = settings.defaultSuffix.ifBlank { "Not set" },
                    onClick = {
                        tempSuffix = settings.defaultSuffix
                        showSuffixDialog = true
                    }
                )

                DialogSettingRow(
                    title = "Embedded thumbnail",
                    value = settings.thumbnailHandling.toReadableLabel(),
                    onClick = {
                        tempThumbnailHandling = settings.thumbnailHandling
                        showThumbnailHandlingDialog = true
                    }
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Default behavior for shared files",
                    style = MaterialTheme.typography.titleMedium
                )

                SettingSwitchRow(
                    title = "Enable automatic handling",
                    subtitle = "When files are shared from another app, process them immediately using the defaults below",
                    checked = settings.autoHandleSharedFiles,
                    onCheckedChange = onAutoHandleSharedFilesChanged
                )

                DialogSettingRow(
                    title = "Metadata action",
                    value = settings.sharedFilesProcessingMode.toReadableLabel(),
                    onClick = {
                        tempSharedProcessingMode = settings.sharedFilesProcessingMode
                        showSharedProcessingModeDialog = true
                    }
                )

                DialogSettingRow(
                    title = "Output action",
                    value = settings.sharedFilesOutputAction.toReadableLabel(),
                    onClick = {
                        tempSharedOutputAction = settings.sharedFilesOutputAction
                        showSharedOutputActionDialog = true
                    }
                )

                if (settings.sharedFilesOutputAction == SharedInputOutputAction.SAVE_TO_SHARED_FOLDER) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Dedicated shared-files folder",
                            style = MaterialTheme.typography.bodyLarge
                        )

                        Text(
                            text = settings.sharedFilesCustomPath ?: "No dedicated folder selected",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Button(
                            onClick = { sharedFolderPicker.launch(null) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Select Dedicated Folder")
                        }

                        Button(
                            onClick = { onSharedFilesCustomPathSelected(null) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Clear Dedicated Folder")
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "UI",
                    style = MaterialTheme.typography.titleMedium
                )

                DialogSettingRow(
                    title = "Night mode",
                    value = settings.nightMode.toReadableLabel(),
                    onClick = {
                        tempNightMode = settings.nightMode
                        showNightModeDialog = true
                    }
                )

                HorizontalDivider()

                SettingSwitchRow(
                    title = "OLED mode",
                    subtitle = "Use darker black tones for supported dark themes",
                    checked = settings.oledMode,
                    onCheckedChange = onOledModeChanged
                )
            }
        }
    }

    if (showNightModeDialog) {
        AlertDialog(
            onDismissRequest = { showNightModeDialog = false },
            title = { Text("Night mode") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    NightModeSetting.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = tempNightMode == mode,
                                    onClick = { tempNightMode = mode }
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = tempNightMode == mode,
                                onClick = { tempNightMode = mode }
                            )
                            Text(text = mode.toReadableLabel())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onNightModeChanged(tempNightMode)
                        showNightModeDialog = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        tempNightMode = settings.nightMode
                        showNightModeDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPrefixDialog) {
        AlertDialog(
            onDismissRequest = { showPrefixDialog = false },
            title = { Text("Default Prefix") },
            text = {
                OutlinedTextField(
                    value = tempPrefix,
                    onValueChange = { tempPrefix = it },
                    label = { Text("Default Prefix") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDefaultPrefixChanged(tempPrefix)
                        showPrefixDialog = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        tempPrefix = settings.defaultPrefix
                        showPrefixDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSuffixDialog) {
        AlertDialog(
            onDismissRequest = { showSuffixDialog = false },
            title = { Text("Default Suffix") },
            text = {
                OutlinedTextField(
                    value = tempSuffix,
                    onValueChange = { tempSuffix = it },
                    label = { Text("Default Suffix") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDefaultSuffixChanged(tempSuffix)
                        showSuffixDialog = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        tempSuffix = settings.defaultSuffix
                        showSuffixDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSharedProcessingModeDialog) {
        AlertDialog(
            onDismissRequest = { showSharedProcessingModeDialog = false },
            title = { Text("Metadata action for shared files") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ProcessingMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = tempSharedProcessingMode == mode,
                                    onClick = { tempSharedProcessingMode = mode }
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = tempSharedProcessingMode == mode,
                                onClick = { tempSharedProcessingMode = mode }
                            )
                            Text(text = mode.toReadableLabel())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSharedFilesProcessingModeChanged(tempSharedProcessingMode)
                        showSharedProcessingModeDialog = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        tempSharedProcessingMode = settings.sharedFilesProcessingMode
                        showSharedProcessingModeDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSharedOutputActionDialog) {
        AlertDialog(
            onDismissRequest = { showSharedOutputActionDialog = false },
            title = { Text("Output action for shared files") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SharedInputOutputAction.entries.forEach { action ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = tempSharedOutputAction == action,
                                    onClick = { tempSharedOutputAction = action }
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = tempSharedOutputAction == action,
                                onClick = { tempSharedOutputAction = action }
                            )
                            Text(text = action.toReadableLabel())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSharedFilesOutputActionChanged(tempSharedOutputAction)
                        showSharedOutputActionDialog = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        tempSharedOutputAction = settings.sharedFilesOutputAction
                        showSharedOutputActionDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showThumbnailHandlingDialog) {
        AlertDialog(
            onDismissRequest = { showThumbnailHandlingDialog = false },
            title = { Text("Embedded thumbnail") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ThumbnailHandling.entries.forEach { handling ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = tempThumbnailHandling == handling,
                                    onClick = { tempThumbnailHandling = handling }
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = tempThumbnailHandling == handling,
                                onClick = { tempThumbnailHandling = handling }
                            )
                            Text(text = handling.toReadableLabel())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onThumbnailHandlingChanged(tempThumbnailHandling)
                        showThumbnailHandlingDialog = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        tempThumbnailHandling = settings.thumbnailHandling
                        showThumbnailHandlingDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
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
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun DialogSettingRow(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun NightModeSetting.toReadableLabel(): String {
    return when (this) {
        NightModeSetting.ALWAYS -> "Always"
        NightModeSetting.AUTOMATIC -> "Automatic"
        NightModeSetting.ONLY_LOW_BATTERY -> "Only low battery"
        NightModeSetting.NEVER -> "Never"
    }
}

private fun ProcessingMode.toReadableLabel(): String {
    return when (this) {
        ProcessingMode.POISON_METADATA -> "Poison metadata"
        ProcessingMode.REMOVE_METADATA -> "Remove metadata"
    }
}

private fun SharedInputOutputAction.toReadableLabel(): String {
    return when (this) {
        SharedInputOutputAction.SAVE_TO_DEFAULT_FOLDER -> "Save to default folder"
        SharedInputOutputAction.SAVE_TO_SHARED_FOLDER -> "Save to dedicated shared-files folder"
        SharedInputOutputAction.SHARE_TO_ANOTHER_APP -> "Share to another app"
    }
}

private fun ThumbnailHandling.toReadableLabel(): String {
    return when (this) {
        ThumbnailHandling.REMOVE -> "Remove (recommended for maximum privacy)"
        ThumbnailHandling.KEEP_SCRUBBED -> "Keep scrubbed (thumbnail remains but its metadata is cleared)"
    }
}
