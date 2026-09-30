package com.heronikostudios.metajammer.ui.map

import androidx.compose.ui.geometry.Offset
import com.heronikostudios.metajammer.ui.components.map.MapProjection
import com.heronikostudios.metajammer.ui.components.map.WorldMapGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MapProjectionTest {

    private val delta = 0.0001

    @Test
    fun `virtual projection round trip for key global landmarks`() {
        val testPoints = listOf(
            Pair(0.0, 0.0),          // Null Island
            Pair(90.0, 0.0),         // North Pole
            Pair(-90.0, 0.0),        // South Pole
            Pair(0.0, 180.0),        // Date Line East
            Pair(0.0, -180.0),       // Date Line West
            Pair(48.8566, 2.3522),   // Paris
            Pair(35.6762, 139.6503), // Tokyo
            Pair(40.7128, -74.0060), // New York
            Pair(-33.8688, 151.2093) // Sydney
        )

        for ((lat, lon) in testPoints) {
            val virtual = MapProjection.latLonToVirtual(lat, lon)
            assertTrue("Virtual X out of bounds: ${virtual.x}", virtual.x in 0f..360f)
            assertTrue("Virtual Y out of bounds: ${virtual.y}", virtual.y in 0f..180f)

            val (outLat, outLon) = MapProjection.virtualToLatLon(virtual)
            assertEquals("Latitude mismatch for ($lat, $lon)", lat, outLat, delta)
            assertEquals("Longitude mismatch for ($lat, $lon)", lon, outLon, delta)
        }
    }

    @Test
    fun `canvas projection round trip with zoom and pan`() {
        val canvasWidth = 1080f
        val canvasHeight = 2400f
        val baseScale = MapProjection.computeBaseScale(canvasWidth, canvasHeight)
        val initialPan = MapProjection.computeInitialPan(canvasWidth, canvasHeight, baseScale)

        val zooms = listOf(1.0f, 2.0f, 5.0f, 10.0f)
        val testCoords = listOf(
            Pair(0.0, 0.0),
            Pair(51.5074, -0.1278),  // London
            Pair(-22.9068, -43.1729), // Rio
            Pair(30.0444, 31.2357)   // Cairo
        )

        for (zoom in zooms) {
            val pan = initialPan + Offset(-100f * (zoom - 1f), -50f * (zoom - 1f))
            for ((lat, lon) in testCoords) {
                val canvasOffset = MapProjection.latLonToCanvas(lat, lon, baseScale, zoom, pan)
                val (resultLat, resultLon) = MapProjection.canvasToLatLon(canvasOffset, baseScale, zoom, pan)

                assertEquals("Lat failed at zoom $zoom for ($lat, $lon)", lat, resultLat, delta)
                assertEquals("Lon failed at zoom $zoom for ($lat, $lon)", lon, resultLon, delta)
            }
        }
    }

    @Test
    fun `clampPan restricts pan translation within valid bounds`() {
        val canvasWidth = 1000f
        val canvasHeight = 1000f
        val baseScale = MapProjection.computeBaseScale(canvasWidth, canvasHeight)
        val zoom = 2.0f

        val excessPan = Offset(500f, 500f) // Way off to the top-right
        val clamped = MapProjection.clampPan(excessPan, canvasWidth, canvasHeight, baseScale, zoom)

        val mapWidth = MapProjection.VIRTUAL_WIDTH * baseScale * zoom
        val minX = canvasWidth - mapWidth
        assertTrue("Clamped X (${clamped.x}) should be <= 0", clamped.x <= 0f)
        assertTrue("Clamped X (${clamped.x}) should be >= minX ($minX)", clamped.x >= minX)
    }

    @Test
    fun `formatCoordinates produces directional labels and 4-decimal precision`() {
        assertEquals("48.8566° N, 2.3522° E", MapProjection.formatCoordinates(48.8566, 2.3522))
        assertEquals("33.8688° S, 151.2093° E", MapProjection.formatCoordinates(-33.8688, 151.2093))
        assertEquals("40.7128° N, 74.0060° W", MapProjection.formatCoordinates(40.7128, -74.0060))
        assertEquals("0.0000° N, 0.0000° E", MapProjection.formatCoordinates(0.0, 0.0))
    }

    @Test
    fun `world land binary geometry asset loads into valid Compose Path`() {
        val binFile = sequenceOf(
            File("src/main/assets/map/world_land.bin"),
            File("app/src/main/assets/map/world_land.bin")
        ).firstOrNull { it.exists() } ?: File("src/main/assets/map/world_land.bin")

        assertTrue("Asset file must exist at ${binFile.absolutePath}", binFile.exists())
        assertTrue("Asset file size must be > 10KB", binFile.length() > 10_000)

        binFile.inputStream().use { stream ->
            val path = WorldMapGeometry.loadFromStream(stream)
            assertNotNull(path)
        }
    }
}
