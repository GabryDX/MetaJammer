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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun OutputOptionsScreen(
    shareResultAsDefault: Boolean,
    onSaveDefault: () -> Unit,
    onSaveCustom: (Uri) -> Unit,
    onShareOnly: () -> Unit,
    modifier: Modifier = Modifier
) {
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let(onSaveCustom)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Output Options",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = if (shareResultAsDefault) {
                "Sharing is enabled by default. Saving will also trigger sharing."
            } else {
                "Choose whether to save or share the processed result."
            },
            style = MaterialTheme.typography.bodyMedium
        )

        Button(
            onClick = onSaveDefault,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save to Default Folder")
        }

        Button(
            onClick = { folderPicker.launch(null) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save to Custom Folder")
        }

        Button(
            onClick = onShareOnly,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Share")
        }
    }
}
