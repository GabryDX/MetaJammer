package com.heronikostudios.metajammer.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import com.heronikostudios.metajammer.R
import com.heronikostudios.metajammer.domain.model.*

@Composable
fun ProcessingScreen(
    selectedFiles: List<SelectedFile>,
    selectedMode: ProcessingMode?,
    changePreview: Map<Uri, List<MetadataDiffEntry>>,
    processing: Boolean,
    workInfo: WorkInfo?,
    onModeSelected: (ProcessingMode) -> Unit,
    onRegeneratePlans: () -> Unit,
    onProcess: () -> Unit,
    onEditLocation: (Uri) -> Unit,
    hasProcessedFiles: Boolean,
    selectedProfile: PoisoningProfile = PoisoningProfile.RANDOM,
    onProfileSelected: (PoisoningProfile) -> Unit = {},
    selectedLocationPreset: LocationPreset = LocationPreset.RANDOM,
    onLocationPresetSelected: (LocationPreset) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var activeDiffFilter by remember { mutableStateOf<MetadataDiffStatus?>(null) }
    var expandedUris by remember(selectedFiles) { mutableStateOf(selectedFiles.map { it.uri }.toSet()) }

    val allDiffEntries = remember(changePreview) { changePreview.values.flatten() }
    val totalCount = allDiffEntries.size
    val removedCount = remember(allDiffEntries) { allDiffEntries.count { it.status == MetadataDiffStatus.REMOVED } }
    val poisonedCount = remember(allDiffEntries) { allDiffEntries.count { it.status == MetadataDiffStatus.POISONED } }
    val keptCount = remember(allDiffEntries) { allDiffEntries.count { it.status == MetadataDiffStatus.KEPT } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.pick_one_option),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val poisonSelected = selectedMode == ProcessingMode.POISON_METADATA
                    val removeSelected = selectedMode == ProcessingMode.REMOVE_METADATA

                    Button(
                        onClick = { onModeSelected(ProcessingMode.POISON_METADATA) },
                        modifier = Modifier.weight(1f),
                        colors = if (poisonSelected) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors()
                    ) {
                        Text(stringResource(R.string.poison_metadata))
                    }

                    Button(
                        onClick = { onModeSelected(ProcessingMode.REMOVE_METADATA) },
                        modifier = Modifier.weight(1f),
                        colors = if (removeSelected) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors()
                    ) {
                        Text(stringResource(R.string.remove_metadata))
                    }
                }

                if (selectedMode == ProcessingMode.POISON_METADATA) {
                    Text(
                        text = stringResource(R.string.hardware_profile_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PoisoningProfile.entries.forEach { profile ->
                            FilterChip(
                                selected = profile == selectedProfile,
                                onClick = { onProfileSelected(profile) },
                                label = { Text(profile.displayName) }
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.location_preset_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LocationPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = preset == selectedLocationPreset,
                                onClick = { onLocationPresetSelected(preset) },
                                label = { Text(preset.displayName) }
                            )
                        }
                    }

                    Button(
                        onClick = onRegeneratePlans,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.regenerate_plans))
                    }
                }
            }
        }

        if (selectedMode != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.preview_of_changes),
                    style = MaterialTheme.typography.titleMedium
                )

                // Interactive filter chips for diff statuses
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = activeDiffFilter == null,
                        onClick = { activeDiffFilter = null },
                        label = { Text(stringResource(R.string.diff_filter_all, totalCount)) }
                    )
                    FilterChip(
                        selected = activeDiffFilter == MetadataDiffStatus.REMOVED,
                        onClick = { activeDiffFilter = MetadataDiffStatus.REMOVED },
                        label = { Text(stringResource(R.string.diff_filter_removed, removedCount)) }
                    )
                    if (selectedMode == ProcessingMode.POISON_METADATA) {
                        FilterChip(
                            selected = activeDiffFilter == MetadataDiffStatus.POISONED,
                            onClick = { activeDiffFilter = MetadataDiffStatus.POISONED },
                            label = { Text(stringResource(R.string.diff_filter_poisoned, poisonedCount)) }
                        )
                    }
                    FilterChip(
                        selected = activeDiffFilter == MetadataDiffStatus.KEPT,
                        onClick = { activeDiffFilter = MetadataDiffStatus.KEPT },
                        label = { Text(stringResource(R.string.diff_filter_kept, keptCount)) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(selectedFiles) { file ->
                    val isExpanded = file.uri in expandedUris
                    val fileEntries = changePreview[file.uri].orEmpty()
                    val filteredEntries = fileEntries.filter { activeDiffFilter == null || it.status == activeDiffFilter }

                    val fileRemoved = fileEntries.count { it.status == MetadataDiffStatus.REMOVED }
                    val filePoisoned = fileEntries.count { it.status == MetadataDiffStatus.POISONED }
                    val fileKept = fileEntries.count { it.status == MetadataDiffStatus.KEPT }

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Header row with toggle expansion
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedUris = if (isExpanded) {
                                            expandedUris - file.uri
                                        } else {
                                            expandedUris + file.uri
                                        }
                                    },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = file.displayName,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = stringResource(R.string.file_type, file.mimeType ?: stringResource(R.string.unknown)),
                                        style = MaterialTheme.typography.bodySmall
                                    )

                                    // Quick summary badges
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        if (fileRemoved > 0) {
                                            DiffSummaryPill(
                                                text = "-$fileRemoved",
                                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                        if (filePoisoned > 0) {
                                            DiffSummaryPill(
                                                text = "~$filePoisoned",
                                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                            )
                                        }
                                        if (fileKept > 0) {
                                            DiffSummaryPill(
                                                text = "=$fileKept",
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        expandedUris = if (isExpanded) {
                                            expandedUris - file.uri
                                        } else {
                                            expandedUris + file.uri
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null
                                    )
                                }
                            }

                            AnimatedVisibility(visible = isExpanded) {
                                Column(modifier = Modifier.padding(top = 8.dp)) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                                    if (fileEntries.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.no_preview_available),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    } else if (filteredEntries.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.diff_no_tags_for_filter),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            filteredEntries.forEach { entry ->
                                                DiffItemRow(entry = entry)
                                            }
                                        }

                                        if (selectedMode == ProcessingMode.POISON_METADATA) {
                                            Button(
                                                onClick = { onEditLocation(file.uri) },
                                                modifier = Modifier.padding(top = 8.dp)
                                            ) {
                                                Text(stringResource(R.string.change_location_map))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = onProcess,
            enabled = !processing && selectedMode != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (hasProcessedFiles) stringResource(R.string.continue_label) else stringResource(R.string.process_and_continue))
        }

        if (processing) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Text(stringResource(R.string.processing_foreground), style = MaterialTheme.typography.bodySmall)
            }
        }

        workInfo?.let { info ->
            if (info.state == WorkInfo.State.RUNNING || info.state == WorkInfo.State.ENQUEUED) {
                val progress = info.progress.getInt("progress", 0)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(stringResource(R.string.background_progress, progress), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun DiffSummaryPill(
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun DiffStatusBadge(status: MetadataDiffStatus) {
    val (bg, fg, label) = when (status) {
        MetadataDiffStatus.REMOVED -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            stringResource(R.string.diff_badge_removed)
        )
        MetadataDiffStatus.POISONED -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            stringResource(R.string.diff_badge_poisoned)
        )
        MetadataDiffStatus.KEPT -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            stringResource(R.string.diff_badge_kept)
        )
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bg,
        contentColor = fg
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun DiffItemRow(entry: MetadataDiffEntry, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.key,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DiffStatusBadge(status = entry.status)
            }

            when (entry.status) {
                MetadataDiffStatus.REMOVED -> {
                    Text(
                        text = stringResource(R.string.diff_before, entry.originalValue ?: stringResource(R.string.diff_value_none)),
                        style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.LineThrough),
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = stringResource(R.string.diff_after, stringResource(R.string.diff_value_stripped)),
                        style = MaterialTheme.typography.labelSmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                MetadataDiffStatus.POISONED -> {
                    if (entry.originalValue != null) {
                        Text(
                            text = stringResource(R.string.diff_before, entry.originalValue),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Text(
                        text = stringResource(R.string.diff_after, entry.newValue ?: stringResource(R.string.diff_value_none)),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                MetadataDiffStatus.KEPT -> {
                    Text(
                        text = stringResource(R.string.diff_before, entry.originalValue ?: stringResource(R.string.diff_value_none)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
