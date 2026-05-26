package com.heronikostudios.metajammer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = "Default post-processing action",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            text = "Choose what the app should do by default after processing a file.",
            style = MaterialTheme.typography.bodyMedium
        )

        PostProcessAction.entries.forEach { action ->
            Card(modifier = Modifier.fillMaxWidth()) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    RadioButton(
                        selected = currentDefaultAction == action,
                        onClick = { onSetDefaultAction(action) }
                    )

                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            text = action.name.replace("_", " "),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}
