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
    modifier: Modifier = Modifier
) {
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            onFilesPicked(uris)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "MetaJammer",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Select files from storage or share them into the app.",
            style = MaterialTheme.typography.bodyMedium
        )

        Button(
            onClick = { pickerLauncher.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Select Files")
        }

        if (selectedFiles.isNotEmpty()) {
            Text(
                text = "Selected Files",
                style = MaterialTheme.typography.titleMedium
            )

            selectedFiles.forEach { file ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(file.displayName, style = MaterialTheme.typography.bodyLarge)
                        Text("Type: ${file.mimeType ?: "unknown"}", style = MaterialTheme.typography.bodySmall)
                        Text("Size: ${file.sizeBytes ?: 0} bytes", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Continue")
            }
        }
    }
}
