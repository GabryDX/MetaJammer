package com.heronikostudios.metajammer.ui.components.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Interactive, 100% offline Vector World Map composable rendered natively in Jetpack Compose.
 *
 * Features:
 * - Hardware-accelerated Skia Canvas rendering of Natural Earth landmasses.
 * - Fluid pinch-to-zoom (1.0x to 15.0x) and 2D pan with boundary clamping.
 * - Tap to set pin, double-tap to zoom in.
 * - Themed ocean, land fill, coastlines, and coordinate graticules (Equator, Prime Meridian, tropics).
 * - Animated target pin marker with radar crosshairs.
 */
@Composable
fun VectorWorldMap(
    selectedLat: Double,
    selectedLon: Double,
    onLocationChanged: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
    targetLocation: Pair<Double, Double>? = null,
    minZoom: Float = 1.0f,
    maxZoom: Float = 15.0f
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Lazy load vector landmass path from assets
    val worldPath = remember {
        WorldMapGeometry.getOrLoadPath(context)
    }

    var zoom by remember { mutableFloatStateOf(minZoom) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(Offset.Zero) }
    var isInitialized by remember { mutableStateOf(false) }

    // Colors matching MetaJammer's palette
    val oceanColor = MaterialTheme.colorScheme.surface
    val landColor = MaterialTheme.colorScheme.surfaceVariant
    val coastlineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    val gridMajorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val gridMinorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val pinColor = Color(0xFFE53935) // High-visibility red pin
    val pinPulseColor = Color(0x44E53935)
    val crosshairColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)

    // Smoothly animate to targetLocation when preset selected
    LaunchedEffect(targetLocation) {
        if (targetLocation != null && canvasSize.x > 0f && canvasSize.y > 0f) {
            val (tLat, tLon) = targetLocation
            val baseScale = MapProjection.computeBaseScale(canvasSize.x, canvasSize.y)
            val newZoom = zoom.coerceAtLeast(3.5f)
            val totalScale = baseScale * newZoom
            val virtual = MapProjection.latLonToVirtual(tLat, tLon)

            val targetPanX = canvasSize.x / 2f - virtual.x * totalScale
            val targetPanY = canvasSize.y / 2f - virtual.y * totalScale
            val clampedPan = MapProjection.clampPan(
                Offset(targetPanX, targetPanY),
                canvasSize.x,
                canvasSize.y,
                baseScale,
                newZoom
            )

            launch {
                val animZoom = Animatable(zoom)
                val animPanX = Animatable(pan.x)
                val animPanY = Animatable(pan.y)

                launch {
                    animZoom.animateTo(newZoom, tween(500)) {
                        zoom = value
                    }
                }
                launch {
                    animPanX.animateTo(clampedPan.x, tween(500)) {
                        pan = Offset(value, pan.y)
                    }
                }
                launch {
                    animPanY.animateTo(clampedPan.y, tween(500)) {
                        pan = Offset(pan.x, value)
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(oceanColor)
            .pointerInput(Unit) {
                detectTransformGestures { centroid, panChange, zoomChange, _ ->
                    val baseScale = MapProjection.computeBaseScale(size.width.toFloat(), size.height.toFloat())
                    val oldZoom = zoom
                    val newZoom = (zoom * zoomChange).coerceIn(minZoom, maxZoom)
                    val newPan = (pan - centroid) * (newZoom / oldZoom) + centroid + panChange
                    zoom = newZoom
                    pan = MapProjection.clampPan(newPan, size.width.toFloat(), size.height.toFloat(), baseScale, newZoom)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { tapOffset ->
                        val baseScale = MapProjection.computeBaseScale(size.width.toFloat(), size.height.toFloat())
                        val (lat, lon) = MapProjection.canvasToLatLon(tapOffset, baseScale, zoom, pan)
                        onLocationChanged(lat, lon)
                    },
                    onDoubleTap = { tapOffset ->
                        val baseScale = MapProjection.computeBaseScale(size.width.toFloat(), size.height.toFloat())
                        val newZoom = (zoom * 2f).coerceAtMost(maxZoom)
                        val newPan = (pan - tapOffset) * (newZoom / zoom) + tapOffset
                        zoom = newZoom
                        pan = MapProjection.clampPan(newPan, size.width.toFloat(), size.height.toFloat(), baseScale, newZoom)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cWidth = size.width
            val cHeight = size.height

            if (!isInitialized || canvasSize.x != cWidth || canvasSize.y != cHeight) {
                canvasSize = Offset(cWidth, cHeight)
                val baseScale = MapProjection.computeBaseScale(cWidth, cHeight)
                if (!isInitialized) {
                    pan = MapProjection.computeInitialPan(cWidth, cHeight, baseScale)
                    isInitialized = true
                }
            }

            val baseScale = MapProjection.computeBaseScale(cWidth, cHeight)
            val totalScale = baseScale * zoom

            // 1. Draw transformed world landmasses and graticules
            withTransform({
                translate(left = pan.x, top = pan.y)
                scale(scaleX = totalScale, scaleY = totalScale, pivot = Offset.Zero)
            }) {
                // Ocean border frame
                drawRect(
                    color = coastlineColor.copy(alpha = 0.2f),
                    topLeft = Offset.Zero,
                    size = androidx.compose.ui.geometry.Size(MapProjection.VIRTUAL_WIDTH, MapProjection.VIRTUAL_HEIGHT),
                    style = Stroke(width = 0.5f / totalScale)
                )

                // Graticule: Minor latitude lines (every 30 degrees)
                val dashedMinor = PathEffect.dashPathEffect(floatArrayOf(2f / totalScale, 4f / totalScale), 0f)
                val dashedMajor = PathEffect.dashPathEffect(floatArrayOf(4f / totalScale, 4f / totalScale), 0f)

                listOf(30f, 60f, 120f, 150f).forEach { y ->
                    drawLine(
                        color = gridMinorColor,
                        start = Offset(0f, y),
                        end = Offset(MapProjection.VIRTUAL_WIDTH, y),
                        strokeWidth = 0.5f / totalScale,
                        pathEffect = dashedMinor
                    )
                }

                // Graticule: Minor longitude lines (every 30 degrees)
                for (lonDeg in 30 until 360 step 30) {
                    if (lonDeg == 180) continue
                    drawLine(
                        color = gridMinorColor,
                        start = Offset(lonDeg.toFloat(), 0f),
                        end = Offset(lonDeg.toFloat(), MapProjection.VIRTUAL_HEIGHT),
                        strokeWidth = 0.5f / totalScale,
                        pathEffect = dashedMinor
                    )
                }

                // Graticule: Major Equator (lat = 0 => y = 90)
                drawLine(
                    color = gridMajorColor,
                    start = Offset(0f, 90f),
                    end = Offset(MapProjection.VIRTUAL_WIDTH, 90f),
                    strokeWidth = 1.0f / totalScale,
                    pathEffect = dashedMajor
                )

                // Graticule: Major Prime Meridian (lon = 0 => x = 180)
                drawLine(
                    color = gridMajorColor,
                    start = Offset(180f, 0f),
                    end = Offset(180f, MapProjection.VIRTUAL_HEIGHT),
                    strokeWidth = 1.0f / totalScale,
                    pathEffect = dashedMajor
                )

                // Draw filled landmasses
                drawPath(path = worldPath, color = landColor)

                // Draw coastline borders
                drawPath(
                    path = worldPath,
                    color = coastlineColor,
                    style = Stroke(
                        width = 0.8f / totalScale.coerceAtLeast(0.1f),
                        cap = StrokeCap.Round
                    )
                )
            }

            // 2. Draw Pin & Radar Target in screen space
            val pinScreenPos = MapProjection.latLonToCanvas(
                selectedLat,
                selectedLon,
                baseScale,
                zoom,
                pan
            )

            drawPinTarget(
                pinScreenPos = pinScreenPos,
                pinColor = pinColor,
                pinPulseColor = pinPulseColor,
                crosshairColor = crosshairColor,
                zoom = zoom
            )
        }
    }
}

/**
 * Draws the high-visibility radar target and map pin in screen space coordinates.
 */
private fun DrawScope.drawPinTarget(
    pinScreenPos: Offset,
    pinColor: Color,
    pinPulseColor: Color,
    crosshairColor: Color,
    zoom: Float
) {
    val px = pinScreenPos.x
    val py = pinScreenPos.y

    // Crosshairs (tactical radar lines)
    if (zoom > 2.0f) {
        val hairLength = (16.dp * (zoom / 4f).coerceAtMost(3f)).toPx()
        drawLine(
            color = crosshairColor,
            start = Offset(px - hairLength, py),
            end = Offset(px + hairLength, py),
            strokeWidth = 1.5.dp.toPx()
        )
        drawLine(
            color = crosshairColor,
            start = Offset(px, py - hairLength),
            end = Offset(px, py + hairLength),
            strokeWidth = 1.5.dp.toPx()
        )
    }

    // Outer pulse ring
    drawCircle(
        color = pinPulseColor,
        radius = 18.dp.toPx(),
        center = pinScreenPos
    )

    // Inner target ring
    drawCircle(
        color = pinColor,
        radius = 8.dp.toPx(),
        center = pinScreenPos,
        style = Stroke(width = 2.dp.toPx())
    )

    // Pin head (teardrop pin shape pointing at px, py)
    val pinPath = Path().apply {
        val pinHeadRadius = 9.dp.toPx()
        val pinBottomY = py
        val pinCenterY = py - 20.dp.toPx()

        moveTo(px, pinBottomY)
        lineTo(px - pinHeadRadius * 0.75f, pinCenterY)
        arcTo(
            rect = androidx.compose.ui.geometry.Rect(
                px - pinHeadRadius,
                pinCenterY - pinHeadRadius,
                px + pinHeadRadius,
                pinCenterY + pinHeadRadius
            ),
            startAngleDegrees = 150f,
            sweepAngleDegrees = 240f,
            forceMoveTo = false
        )
        lineTo(px, pinBottomY)
        close()
    }

    // Shadow
    drawCircle(
        color = Color.Black.copy(alpha = 0.25f),
        radius = 4.dp.toPx(),
        center = Offset(px, py + 1.dp.toPx())
    )

    // Pin body
    drawPath(path = pinPath, color = pinColor)

    // White dot inside pin head
    drawCircle(
        color = Color.White,
        radius = 3.5.dp.toPx(),
        center = Offset(px, py - 20.dp.toPx())
    )
}
