package com.heronikostudios.metajammer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun MessageBanner(
    message: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = message,
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFE8F0FE))
            .padding(12.dp),
        color = Color.Black,
        style = MaterialTheme.typography.bodyMedium
    )
}
