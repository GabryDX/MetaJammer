package com.heronikostudios.metajammer.ui.components.map

import androidx.compose.ui.geometry.Offset
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

/**
 * Mathematical projection engine for the Equirectangular (Plate Carrée) Vector World Map.
 *
 * Maps longitude in [-180.0, +180.0] and latitude in [-90.0, +90.0] to a virtual 2D
 * coordinate space of 360x180, and provides bidirectional transformations between
 * screen canvas touch offsets and geographic coordinates.
 */
object MapProjection {
    const val VIRTUAL_WIDTH = 360f
    const val VIRTUAL_HEIGHT = 180f

    /**
     * Converts (lat, lon) to normalized virtual coordinates:
     * x in [0.0, 360.0], y in [0.0, 180.0].
     */
    fun latLonToVirtual(lat: Double, lon: Double): Offset {
        val clampedLon = lon.coerceIn(-180.0, 180.0)
        val clampedLat = lat.coerceIn(-90.0, 90.0)
        val x = (clampedLon + 180.0).toFloat()
        val y = (90.0 - clampedLat).toFloat()
        return Offset(x, y)
    }

    /**
     * Converts virtual coordinates (x in [0, 360], y in [0, 180]) to (lat, lon).
     */
    fun virtualToLatLon(virtual: Offset): Pair<Double, Double> {
        val lon = (virtual.x.toDouble() - 180.0).coerceIn(-180.0, 180.0)
        val lat = (90.0 - virtual.y.toDouble()).coerceIn(-90.0, 90.0)
        return Pair(lat, lon)
    }

    /**
     * Calculates the base scale factor to fit the 360x180 map within the canvas.
     */
    fun computeBaseScale(canvasWidth: Float, canvasHeight: Float): Float {
        if (canvasWidth <= 0f || canvasHeight <= 0f) return 1f
        return canvasWidth / VIRTUAL_WIDTH
    }

    /**
     * Computes the initial pan offset to center the map vertically in the canvas at zoom 1.0.
     */
    fun computeInitialPan(canvasWidth: Float, canvasHeight: Float, baseScale: Float): Offset {
        val mapHeight = VIRTUAL_HEIGHT * baseScale
        val initialY = (canvasHeight - mapHeight) / 2f
        return Offset(0f, initialY)
    }

    /**
     * Converts geographic coordinates to screen canvas pixels given zoom and pan.
     */
    fun latLonToCanvas(
        lat: Double,
        lon: Double,
        baseScale: Float,
        zoom: Float,
        pan: Offset
    ): Offset {
        val virtual = latLonToVirtual(lat, lon)
        val totalScale = baseScale * zoom
        val canvasX = virtual.x * totalScale + pan.x
        val canvasY = virtual.y * totalScale + pan.y
        return Offset(canvasX, canvasY)
    }

    /**
     * Converts screen canvas touch pixel offset to geographic (lat, lon) coordinates.
     */
    fun canvasToLatLon(
        canvasOffset: Offset,
        baseScale: Float,
        zoom: Float,
        pan: Offset
    ): Pair<Double, Double> {
        val totalScale = (baseScale * zoom).coerceAtLeast(0.0001f)
        val virtualX = (canvasOffset.x - pan.x) / totalScale
        val virtualY = (canvasOffset.y - pan.y) / totalScale
        return virtualToLatLon(Offset(virtualX, virtualY))
    }

    /**
     * Clamps the pan translation offset so the map stays comfortably visible within view.
     */
    fun clampPan(
        pan: Offset,
        canvasWidth: Float,
        canvasHeight: Float,
        baseScale: Float,
        zoom: Float
    ): Offset {
        val totalScale = baseScale * zoom
        val mapWidth = VIRTUAL_WIDTH * totalScale
        val mapHeight = VIRTUAL_HEIGHT * totalScale

        val minX: Float
        val maxX: Float
        if (mapWidth > canvasWidth) {
            minX = canvasWidth - mapWidth
            maxX = 0f
        } else {
            val centeredX = (canvasWidth - mapWidth) / 2f
            minX = centeredX
            maxX = centeredX
        }

        val minY: Float
        val maxY: Float
        if (mapHeight > canvasHeight) {
            minY = canvasHeight - mapHeight
            maxY = 0f
        } else {
            val centeredY = (canvasHeight - mapHeight) / 2f
            minY = centeredY
            maxY = centeredY
        }

        return Offset(
            x = pan.x.coerceIn(minX, maxX),
            y = pan.y.coerceIn(minY, maxY)
        )
    }

    /**
     * Formats latitude and longitude with direction suffixes and 4-decimal precision (~11 meters).
     * Example: "48.8584° N, 2.2945° E"
     */
    fun formatCoordinates(lat: Double, lon: Double): String {
        val roundedLat = round(lat * 10000) / 10000.0
        val roundedLon = round(lon * 10000) / 10000.0
        val latDir = if (roundedLat >= 0) "N" else "S"
        val lonDir = if (roundedLon >= 0) "E" else "W"
        return String.format(Locale.US, "%.4f° %s, %.4f° %s", abs(roundedLat), latDir, abs(roundedLon), lonDir)
    }
}
