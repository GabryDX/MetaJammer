package com.heronikostudios.metajammer.ui.components.map

import android.content.Context
import androidx.compose.ui.graphics.Path
import kotlinx.serialization.json.Json
import java.io.InputStream

/**
 * Loads and caches the Natural Earth 110m land polygons dataset into an Android Compose [Path].
 *
 * The geometry is stored in a clean, human-readable JSON format (65 KB uncompressed, ~25 KB packaged)
 * in assets/map/world_land.json.
 * Coordinates are pre-projected into the virtual space [0..360, 0..180].
 */
object WorldMapGeometry {

    @Volatile
    private var cachedPath: Path? = null

    /**
     * Retrieves the cached world landmass path, or loads it from application assets if not yet loaded.
     */
    fun getOrLoadPath(context: Context): Path {
        cachedPath?.let { return it }
        return synchronized(this) {
            cachedPath ?: run {
                val path = loadFromStream(context.assets.open("map/world_land.json"))
                cachedPath = path
                path
            }
        }
    }

    /**
     * Parses the JSON array-of-rings representation from an input stream into a Compose [Path].
     */
    fun loadFromStream(inputStream: InputStream): Path {
        val jsonText = inputStream.bufferedReader().use { it.readText() }
        val rings: List<List<Float>> = Json.decodeFromString(jsonText)
        val path = Path()
        for (ring in rings) {
            if (ring.size < 2) continue
            path.moveTo(ring[0], ring[1])
            var i = 2
            while (i < ring.size) {
                path.lineTo(ring[i], ring[i + 1])
                i += 2
            }
            path.close()
        }
        return path
    }
}

