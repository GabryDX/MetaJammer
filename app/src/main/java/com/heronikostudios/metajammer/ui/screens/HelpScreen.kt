package com.heronikostudios.metajammer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.heronikostudios.metajammer.R
import com.heronikostudios.metajammer.ui.theme.MetaJammerTheme

@Composable
fun HelpScreen(
    onBack: () -> Unit,
    onReportIssue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val packageInfo = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
    }
    val versionName = packageInfo?.versionName ?: "Unknown"

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.help_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        HelpItem(
            icon = painterResource(R.drawable.ic_photo_library),
            title = stringResource(R.string.help_item_image_title),
            body = stringResource(R.string.help_item_image_body)
        )

        HelpItem(
            icon = painterResource(R.drawable.ic_movie),
            title = stringResource(R.string.help_item_video_title),
            body = stringResource(R.string.help_item_video_body)
        )

        HelpItem(
            icon = painterResource(R.drawable.ic_security),
            title = stringResource(R.string.help_item_other_title),
            body = stringResource(R.string.help_item_other_body)
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        HelpItem(
            icon = painterResource(R.drawable.ic_bug_report),
            title = stringResource(R.string.help_report_issue_title),
            body = stringResource(R.string.help_report_issue_body),
            action = {
                Button(
                    onClick = onReportIssue,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(stringResource(R.string.help_open_github))
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.help_app_version, versionName),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.back))
        }
    }
}

@Composable
private fun HelpItem(
    icon: Painter,
    title: String,
    body: String,
    action: (@Composable () -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            action?.invoke()
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HelpScreenPreview() {
    MetaJammerTheme {
        HelpScreen(onBack = {}, onReportIssue = {})
    }
}
