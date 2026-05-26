package com.heronikostudios.metajammer.ui.screens

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
import com.heronikostudios.metajammer.domain.model.PostProcessAction

@Composable
fun SettingsScreen(
    currentDefaultAction: PostProcessAction,
    onSetDefaultAction: (PostProcessAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = "Current default action: $currentDefaultAction",
            style = MaterialTheme.typography.bodyLarge
        )

        PostProcessAction.entries.forEach { action ->
            Button(
                onClick = { onSetDefaultAction(action) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Set ${action.name} as default")
            }
        }
    }
}
