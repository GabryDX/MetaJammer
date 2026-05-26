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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.heronikostudios.metajammer.domain.model.MetadataEntry
import com.heronikostudios.metajammer.domain.model.SelectedFile

@Composable
fun MetadataPreviewScreen(
    selectedFiles: List<SelectedFile>,
    metadataPreview: Map<Uri, List<MetadataEntry>>,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Metadata Preview",
            style = MaterialTheme.typography.headlineSmall
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

                        val entries = metadataPreview[file.uri].orEmpty()

                        if (entries.isEmpty()) {
                            Text("No metadata found")
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

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
        }

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }
}
