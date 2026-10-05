package vad.dashing.tbox.speedcam

import kotlin.math.cos
import kotlin.math.floor

/**
 * Equirectangular grid for ~70k SpeedCam points. Cell size ~[cellM] metres.
 */
class SpeedCamIndex(
    points: List<SpeedCamPoint>,
    private val cellM: Double = 500.0,
) {
    private val byCell = HashMap<Long, MutableList<SpeedCamPoint>>()
    val size: Int = points.size
    val all: List<SpeedCamPoint> = points

    init {
        for (p in points) {
            val key = cellKey(p.lat, p.lon)
            byCell.getOrPut(key) { ArrayList(4) }.add(p)
        }
    }

    fun query(
        lat: Double,
        lon: Double,
        radiusM: Double,
    ): List<SpeedCamPoint> {
        if (!lat.isFinite() || !lon.isFinite() || radiusM <= 0.0 || byCell.isEmpty()) {
            return emptyList()
        }
        val latCell = cellSizeLatDeg()
        val lonCell = cellSizeLonDeg(lat)
        val dLat = radiusM / 111_320.0
        val cosLat = cos(Math.toRadians(lat)).coerceAtLeast(1e-6)
        val dLon = radiusM / (111_320.0 * cosLat)
        val minLat = lat - dLat
        val maxLat = lat + dLat
        val minLon = lon - dLon
        val maxLon = lon + dLon
        val i0 = floor(minLat / latCell).toInt()
        val i1 = floor(maxLat / latCell).toInt()
        val j0 = floor(minLon / lonCell).toInt()
        val j1 = floor(maxLon / lonCell).toInt()
        val out = ArrayList<SpeedCamPoint>(32)
        for (i in i0..i1) {
            for (j in j0..j1) {
                val bucket = byCell[pack(i, j)] ?: continue
                out.addAll(bucket)
            }
        }
        return out
    }

    private fun cellKey(lat: Double, lon: Double): Long {
        val i = floor(lat / cellSizeLatDeg()).toInt()
        val j = floor(lon / cellSizeLonDeg(lat)).toInt()
        return pack(i, j)
    }

    private fun cellSizeLatDeg(): Double = cellM / 111_320.0

    private fun cellSizeLonDeg(lat: Double): Double {
        val cosLat = cos(Math.toRadians(lat)).coerceAtLeast(1e-6)
        return cellM / (111_320.0 * cosLat)
    }

    private fun pack(i: Int, j: Int): Long =
        (i.toLong() shl 32) xor (j.toLong() and 0xffff_ffffL)
}
