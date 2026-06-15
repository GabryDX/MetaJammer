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

import androidx.compose.ui.res.stringResource
import com.heronikostudios.metajammer.R

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
            text = if (shareResultAsDefault) {
                stringResource(R.string.saving_will_trigger_sharing)
            } else {
                stringResource(R.string.choose_save_or_share)
            },
            style = MaterialTheme.typography.bodyMedium
        )

        Button(
            onClick = onSaveDefault,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.save_to_default_folder))
        }

        Button(
            onClick = { folderPicker.launch(null) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.save_to_custom_folder))
        }

        Button(
            onClick = onShareOnly,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.share))
        }
    }
}
