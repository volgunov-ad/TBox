package vad.dashing.mqtt.bridge

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

class LocationThrottle(
    private val minIntervalMs: Long = 30_000L,
    private val minMeters: Double = 50.0,
) {
    private var lastSent: GeoPoint? = null
    private var lastSentAtMs: Long = 0L

    fun shouldSend(
        point: GeoPoint,
        nowMs: Long,
        force: Boolean,
        minIntervalMs: Long = this.minIntervalMs,
    ): Boolean {
        if (!point.latitude.isFinite() || !point.longitude.isFinite()) return false
        if (force) return true
        val previous = lastSent ?: return true
        if (minIntervalMs > 0L && nowMs - lastSentAtMs < minIntervalMs) return false
        return distanceMeters(previous, point) >= minMeters
    }

    fun markSent(point: GeoPoint, nowMs: Long) {
        lastSent = point
        lastSentAtMs = nowMs
    }

    fun lastSent(): GeoPoint? = lastSent
}

fun distanceMeters(from: GeoPoint, to: GeoPoint): Double {
    val earth = 6_371_000.0
    val lat1 = Math.toRadians(from.latitude)
    val lat2 = Math.toRadians(to.latitude)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians(to.longitude - from.longitude)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * earth * atan2(sqrt(a), sqrt(1 - a))
}
