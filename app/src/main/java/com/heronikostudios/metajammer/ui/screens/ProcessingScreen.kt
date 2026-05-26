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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.heronikostudios.metajammer.domain.model.MetadataEntry
import com.heronikostudios.metajammer.domain.model.ProcessingMode
import com.heronikostudios.metajammer.domain.model.SelectedFile

@Composable
fun ProcessingScreen(
    selectedFiles: List<SelectedFile>,
    selectedMode: ProcessingMode?,
    changePreview: Map<Uri, List<MetadataEntry>>,
    processing: Boolean,
    onModeSelected: (ProcessingMode) -> Unit,
    onProcess: () -> Unit,
    onNext: () -> Unit,
    hasProcessedFiles: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Choose Processing Mode",
            style = MaterialTheme.typography.headlineSmall
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Pick one option:")

                Button(
                    onClick = { onModeSelected(ProcessingMode.POISON_METADATA) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Poison Metadata")
                }

                Button(
                    onClick = { onModeSelected(ProcessingMode.REMOVE_METADATA) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Remove Metadata")
                }

                Text("Selected: ${selectedMode?.name ?: "None"}")
            }
        }

        if (selectedMode != null) {
            Text(
                text = "Preview of Changes",
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
                                text = "Type: ${file.mimeType ?: "unknown"}",
                                style = MaterialTheme.typography.bodySmall
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                            val entries = changePreview[file.uri].orEmpty()

                            if (entries.isEmpty()) {
                                Text("No preview available")
                            } else {
                                entries.forEach { entry ->
                                    Text(
                                        text = "${entry.key}: ${entry.value}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
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
            Text("Process Files")
        }

        if (processing) {
            CircularProgressIndicator()
        }

        if (hasProcessedFiles) {
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Continue to Output Options")
            }
        }
    }
}
