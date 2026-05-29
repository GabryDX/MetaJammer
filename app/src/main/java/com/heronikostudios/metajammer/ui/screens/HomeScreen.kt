package com.heronikostudios.metajammer.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.heronikostudios.metajammer.domain.model.SelectedFile

@Composable
fun HomeScreen(
    selectedFiles: List<SelectedFile>,
    onFilesPicked: (List<Uri>) -> Unit,
    onContinue: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onFilesPicked(uris)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (selectedFiles.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Protect metadata before sharing files.",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "MetaJammer helps you inspect image metadata and either remove it or replace it before saving or sharing.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "How it works",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = "1. Select files from your device or share them into the app.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "2. Review the detected metadata.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "3. Remove metadata or poison it with realistic replacement values.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Tip",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "You can also open Gallery or another app and share files directly into MetaJammer.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        } else {
            Text(
                text = "Selected Files",
                style = MaterialTheme.typography.titleMedium
            )

            selectedFiles.forEach { file ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = file.displayName,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Type: ${file.mimeType ?: "unknown"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Size: ${file.sizeBytes ?: 0} bytes",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        Button(
            onClick = { pickerLauncher.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (selectedFiles.isEmpty()) "Select Files" else "Select More Files")
        }

        if (selectedFiles.isNotEmpty()) {
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Continue")
            }

            OutlinedButton(
                onClick = onClearSelection,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear Selection")
            }
        }
    }
}
