package com.heronikostudios.metajammer.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.heronikostudios.metajammer.R
import com.heronikostudios.metajammer.domain.model.MetadataEntry
import com.heronikostudios.metajammer.domain.model.ProcessingMode
import com.heronikostudios.metajammer.domain.model.SelectedFile
import androidx.work.WorkInfo

@Composable
fun ProcessingScreen(
    selectedFiles: List<SelectedFile>,
    selectedMode: ProcessingMode?,
    changePreview: Map<Uri, List<MetadataEntry>>,
    processing: Boolean,
    workInfo: WorkInfo?,
    onModeSelected: (ProcessingMode) -> Unit,
    onRegeneratePlans: () -> Unit,
    onProcess: () -> Unit,
    onNext: () -> Unit,
    onEditLocation: (Uri) -> Unit,
    hasProcessedFiles: Boolean,
    modifier: Modifier = Modifier
) {
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
                Text(stringResource(R.string.pick_one_option))

                Button(
                    onClick = { onModeSelected(ProcessingMode.POISON_METADATA) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.poison_metadata))
                }

                Button(
                    onClick = { onModeSelected(ProcessingMode.REMOVE_METADATA) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.remove_metadata))
                }

                Text(stringResource(R.string.selected_mode, selectedMode?.name ?: stringResource(R.string.none)))

                if (selectedMode == ProcessingMode.POISON_METADATA) {
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
            Text(
                text = stringResource(R.string.preview_of_changes),
                style = MaterialTheme.typography.titleMedium
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(selectedFiles) { file ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = file.displayName,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(R.string.file_type, file.mimeType ?: stringResource(R.string.unknown)),
                                style = MaterialTheme.typography.bodySmall
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                            val entries = changePreview[file.uri].orEmpty()

                            if (entries.isEmpty()) {
                                Text(stringResource(R.string.no_preview_available))
                            } else {
                                entries.forEach { entry ->
                                    Text(
                                        text = "${entry.key}: ${entry.value}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
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

        Button(
            onClick = onProcess,
            enabled = !processing && selectedMode != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.process_files))
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

        if (hasProcessedFiles) {
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.continue_to_output))
            }
        }
    }
}
