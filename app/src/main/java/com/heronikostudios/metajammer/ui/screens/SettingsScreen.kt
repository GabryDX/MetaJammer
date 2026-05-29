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

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onUseRandomFileNamesChanged: (Boolean) -> Unit,
    onDefaultSavingPathSelected: (Uri?) -> Unit,
    onAutomaticDeletionChanged: (Boolean) -> Unit,
    onKeepImageOrientationChanged: (Boolean) -> Unit,
    onShareResultAsDefaultChanged: (Boolean) -> Unit,
    onDefaultPrefixChanged: (String) -> Unit,
    onDefaultSuffixChanged: (String) -> Unit,
    onNightModeChanged: (NightModeSetting) -> Unit,
    onOledModeChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        onDefaultSavingPathSelected(uri)
    }

    var showNightModeDialog by remember { mutableStateOf(false) }
    var showPrefixDialog by remember { mutableStateOf(false) }
    var showSuffixDialog by remember { mutableStateOf(false) }

    var tempNightMode by remember(settings.nightMode) { mutableStateOf(settings.nightMode) }
    var tempPrefix by remember(settings.defaultPrefix) { mutableStateOf(settings.defaultPrefix) }
    var tempSuffix by remember(settings.defaultSuffix) { mutableStateOf(settings.defaultSuffix) }

    LaunchedEffect(settings.nightMode) {
        tempNightMode = settings.nightMode
    }

    LaunchedEffect(settings.defaultPrefix) {
        tempPrefix = settings.defaultPrefix
    }

    LaunchedEffect(settings.defaultSuffix) {
        tempSuffix = settings.defaultSuffix
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall
        )

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
                    title = "Automatic deletion",
                    subtitle = "Delete temporary processed files automatically after saving or sharing",
                    checked = settings.automaticDeletion,
                    onCheckedChange = onAutomaticDeletionChanged
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
                    value = if (settings.defaultPrefix.isBlank()) "Not set" else settings.defaultPrefix,
                    onClick = {
                        tempPrefix = settings.defaultPrefix
                        showPrefixDialog = true
                    }
                )

                DialogSettingRow(
                    title = "Default Suffix",
                    value = if (settings.defaultSuffix.isBlank()) "Not set" else settings.defaultSuffix,
                    onClick = {
                        tempSuffix = settings.defaultSuffix
                        showSuffixDialog = true
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
            onDismissRequest = {
                showNightModeDialog = false
            },
            title = {
                Text("Night mode")
            },
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
            onDismissRequest = {
                showPrefixDialog = false
            },
            title = {
                Text("Default Prefix")
            },
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
            onDismissRequest = {
                showSuffixDialog = false
            },
            title = {
                Text("Default Suffix")
            },
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
