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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.heronikostudios.metajammer.domain.model.PostProcessAction

@Composable
fun OutputOptionsScreen(
    selectedAction: PostProcessAction,
    onActionSelected: (PostProcessAction) -> Unit,
    onSaveDefaultPreference: (PostProcessAction) -> Unit,
    onSaveDefault: () -> Unit,
    onSaveCustom: (Uri) -> Unit,
    onShareOnly: () -> Unit,
    onSaveAndShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let(onSaveCustom)
    }

    val actions = PostProcessAction.entries

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

        actions.forEach { action ->
            androidx.compose.foundation.layout.Row {
                RadioButton(
                    selected = selectedAction == action,
                    onClick = { onActionSelected(action) }
                )
                Text(
                    text = action.name,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }

        Button(
            onClick = { onSaveDefaultPreference(selectedAction) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save as Default Action")
        }

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
            Text("Share Processed File")
        }

        Button(
            onClick = onSaveAndShare,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save and Share")
        }
    }
}
