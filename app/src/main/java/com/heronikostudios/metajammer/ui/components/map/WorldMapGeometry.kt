package com.heronikostudios.metajammer.ui.components.map

import android.content.Context
import androidx.compose.ui.graphics.Path
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.InputStream

/**
 * Loads and caches the Natural Earth 110m land polygons dataset into an Android Compose [Path].
 *
 * The geometry is stored in a compact binary format (20 KB) in assets/map/world_land.bin.
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
                val path = loadFromStream(context.assets.open("map/world_land.bin"))
                cachedPath = path
                path
            }
        }
    }

    /**
     * Parses the binary map representation from an input stream into a Compose [Path].
     */
    fun loadFromStream(inputStream: InputStream): Path {
        val path = Path()
        DataInputStream(BufferedInputStream(inputStream)).use { data ->
            val ringCount = data.readUnsignedShort()
            for (r in 0 until ringCount) {
                val ptCount = data.readUnsignedShort()
                if (ptCount == 0) continue
                val x0 = data.readUnsignedShort() / 100f
                val y0 = data.readUnsignedShort() / 100f
                path.moveTo(x0, y0)
                for (p in 1 until ptCount) {
                    val x = data.readUnsignedShort() / 100f
                    val y = data.readUnsignedShort() / 100f
                    path.lineTo(x, y)
                }
                path.close()
            }
        }
        return path
    }
}
