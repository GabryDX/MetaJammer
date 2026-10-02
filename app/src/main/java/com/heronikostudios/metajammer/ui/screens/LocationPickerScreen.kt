package com.heronikostudios.metajammer.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.heronikostudios.metajammer.R
import com.heronikostudios.metajammer.domain.model.LocationPreset
import com.heronikostudios.metajammer.ui.components.map.MapProjection
import com.heronikostudios.metajammer.ui.components.map.VectorWorldMap
import kotlin.random.Random

/**
 * 100% Offline, Air-Gapped Location Picker Screen using Native Compose Vector World Map.
 *
 * Allows visual coordinate selection on a hardware-accelerated vector map,
 * jumping to preset landmarks, random worldwide spoofing, and realistic GPS jitter drift.
 */
@Composable
fun LocationPickerScreen(
    initialLat: Double,
    initialLon: Double,
    onLocationPicked: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
    customPresets: List<com.heronikostudios.metajammer.domain.model.CustomLocationPreset> = emptyList()
) {
    var selectedLat by remember { mutableDoubleStateOf(initialLat) }
    var selectedLon by remember { mutableDoubleStateOf(initialLon) }
    var targetLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var showManualInputDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Interactive Native Vector World Map
        VectorWorldMap(
            selectedLat = selectedLat,
            selectedLon = selectedLon,
            onLocationChanged = { lat, lon ->
                selectedLat = lat
                selectedLon = lon
            },
            targetLocation = targetLocation,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Preset Landmark Chips (Top)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val presets = remember(customPresets) {
                LocationPreset.entries.filter { it.latitude != null && it.longitude != null } + customPresets
            }

            presets.forEach { preset ->
                val pLat = preset.latitude!!
                val pLon = preset.longitude!!
                SuggestionChip(
                    onClick = {
                        selectedLat = pLat
                        selectedLon = pLon
                        targetLocation = Pair(pLat, pLon)
                    },
                    label = { Text(preset.displayName) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        labelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // 3. Bottom Controls HUD Card
        ElevatedCard(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Coordinates display row with click-to-edit
                Surface(
                    onClick = { showManualInputDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = MapProjection.formatCoordinates(selectedLat, selectedLon),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit coordinates",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Actions row: Center on Pin, Randomize, Confirm
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Center Camera on current Pin
                    FilledTonalIconButton(
                        onClick = { targetLocation = Pair(selectedLat, selectedLon) },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = "Center on Pin"
                        )
                    }

                    // Randomize worldwide coordinates
                    FilledTonalIconButton(
                        onClick = {
                            val rLat = Random.nextDouble(-60.0, 75.0)
                            val rLon = Random.nextDouble(-180.0, 180.0)
                            selectedLat = rLat
                            selectedLon = rLon
                            targetLocation = Pair(rLat, rLon)
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Randomize location"
                        )
                    }

                    // Confirm Location Button
                    Button(
                        onClick = { onLocationPicked(selectedLat, selectedLon) },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.confirm_location))
                    }
                }
            }
        }
    }

    // Manual Coordinate Input Dialog
    if (showManualInputDialog) {
        var inputLat by remember { mutableStateOf(selectedLat.toString()) }
        var inputLon by remember { mutableStateOf(selectedLon.toString()) }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showManualInputDialog = false },
            title = { Text(stringResource(R.string.pick_location)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = inputLat,
                        onValueChange = { inputLat = it; errorMsg = null },
                        label = { Text(stringResource(R.string.latitude_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputLon,
                        onValueChange = { inputLon = it; errorMsg = null },
                        label = { Text(stringResource(R.string.longitude_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (errorMsg != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = errorMsg!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val latVal = inputLat.trim().toDoubleOrNull()
                        val lonVal = inputLon.trim().toDoubleOrNull()

                        if (latVal == null || latVal !in -90.0..90.0) {
                            errorMsg = "Latitude must be between -90 and 90"
                            return@Button
                        }
                        if (lonVal == null || lonVal !in -180.0..180.0) {
                            errorMsg = "Longitude must be between -180 and 180"
                            return@Button
                        }

                        selectedLat = latVal
                        selectedLon = lonVal
                        targetLocation = Pair(latVal, lonVal)
                        showManualInputDialog = false
                    }
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualInputDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
