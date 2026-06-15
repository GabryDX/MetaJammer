package com.heronikostudios.metajammer.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.heronikostudios.metajammer.R
import com.heronikostudios.metajammer.domain.model.SelectedFile
import com.heronikostudios.metajammer.ui.theme.MetaJammerTheme

@Composable
fun HomeScreen(
    selectedFiles: List<SelectedFile>,
    onFilesPicked: (List<Uri>) -> Unit,
    onContinue: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            onFilesPicked(uris)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            HeroSection()
        }

        if (selectedFiles.isEmpty()) {
            item {
                FeatureHighlights()
            }
            item {
                TipCard()
            }
        } else {
            item {
                SelectionSummary(
                    count = selectedFiles.size,
                    totalSize = selectedFiles.sumOf { it.sizeBytes ?: 0L }
                )
            }

            items(selectedFiles) { file ->
                FileListItem(file = file)
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { pickerLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (selectedFiles.isEmpty()) stringResource(R.string.select_files) else stringResource(R.string.select_more_files))
                }

                if (selectedFiles.isNotEmpty()) {
                    Button(
                        onClick = onContinue,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.continue_label))
                    }

                    OutlinedButton(
                        onClick = onClearSelection,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.clear_selection))
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_security),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(R.string.hero_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.hero_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun FeatureHighlights() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FeatureItem(
            icon = painterResource(R.drawable.ic_movie),
            title = stringResource(R.string.feature_images_video_title),
            description = stringResource(R.string.feature_images_video_desc)
        )
        FeatureItem(
            icon = painterResource(R.drawable.ic_bug_report),
            title = stringResource(R.string.feature_poisoning_title),
            description = stringResource(R.string.feature_poisoning_desc)
        )
        FeatureItem(
            icon = painterResource(R.drawable.ic_batch_prediction),
            title = stringResource(R.string.feature_background_title),
            description = stringResource(R.string.feature_background_desc)
        )
        FeatureItem(
            icon = Icons.Default.Lock,
            title = stringResource(R.string.feature_on_device_title),
            description = stringResource(R.string.feature_on_device_desc)
        )
    }
}

@Composable
private fun FeatureItem(
    icon: Any,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        when (icon) {
            is ImageVector -> {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(24.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            is androidx.compose.ui.graphics.painter.Painter -> {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(24.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TipCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.pro_tip_gallery),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun SelectionSummary(
    count: Int,
    totalSize: Long
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.files_selected, count),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = stringResource(R.string.total_size, formatSize(totalSize)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun FileListItem(file: SelectedFile) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = if (file.mimeType?.startsWith("video") == true) {
                    painterResource(R.drawable.ic_movie)
                } else {
                    painterResource(R.drawable.ic_photo_library)
                },
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1
                )
                Text(
                    text = "${file.mimeType ?: "unknown"} • ${formatSize(file.sizeBytes ?: 0)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenEmptyPreview() {
    MetaJammerTheme {
        HomeScreen(
            selectedFiles = emptyList(),
            onFilesPicked = {},
            onContinue = {},
            onClearSelection = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenSelectedPreview() {
    MetaJammerTheme {
        HomeScreen(
            selectedFiles = listOf(
                SelectedFile(Uri.EMPTY, "image.jpg", "image/jpeg", 1024 * 500),
                SelectedFile(Uri.EMPTY, "video.mp4", "video/mp4", 1024 * 1024 * 10)
            ),
            onFilesPicked = {},
            onContinue = {},
            onClearSelection = {}
        )
    }
}

@Composable
private fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> stringResource(R.string.size_mb, mb)
        kb >= 1.0 -> stringResource(R.string.size_kb, kb)
        else -> stringResource(R.string.size_bytes, bytes)
    }
}
