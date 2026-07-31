package vad.dashing.tbox.location

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vad.dashing.tbox.LocValues
import vad.dashing.tbox.TboxRepository
import vad.dashing.tbox.TripTelemetryRepository
import vad.dashing.tbox.esp.LocationSource
import kotlin.math.cos
import kotlin.math.sin

/**
 * Periodically pushes the latest [TboxRepository.locValues] into the Android mock
 * location provider when mock is enabled and the source is not [LocationSource.ANDROID].
 *
 * After a fix is lost, the last valid point is retained for [FIX_RETENTION_MS] so HU apps
 * keep receiving updates; optional CAN speed can replace GNSS speed always or only then.
 * During retention with usable speed and a heading, lat/lon are dead-reckoned so navigators
 * that derive motion from coordinates (e.g. Yandex) do not freeze. Heading prefers the live
 * course; if it is 0 / missing, the last non-zero course is reused.
 */
class MockLocationJob(
    private val scope: CoroutineScope,
    private val locationMockManager: LocationMockManager,
    private val mockLocation: StateFlow<Boolean>,
    private val locationSource: StateFlow<LocationSource>,
    private val periodMs: StateFlow<Long>,
    private val canSpeedMode: StateFlow<MockCanSpeedMode>,
) {
    companion object {
        /** Keep last valid coordinates in mock after fix loss. */
        const val FIX_RETENTION_MS = 120_000L

        private const val METERS_PER_DEG_LAT = 111_320.0

        fun shouldPushMock(mockEnabled: Boolean, source: LocationSource): Boolean =
            mockEnabled && source != LocationSource.ANDROID

        fun hasValidCoordinates(loc: LocValues): Boolean =
            loc.latitude != 0.0 || loc.longitude != 0.0

        /**
         * Prefer [currentBearingDeg] when non-zero; otherwise keep [lastKnownBearingDeg].
         * (NMEA often reports 0 when course is unknown — not only when heading true north.)
         */
        fun resolveBearingForExtrapolation(
            currentBearingDeg: Float,
            lastKnownBearingDeg: Float?,
        ): Float? {
            if (currentBearingDeg != 0f) return currentBearingDeg
            val last = lastKnownBearingDeg ?: return null
            return if (last != 0f) last else null
        }

        /**
         * Equirectangular step: move [distanceM] along [bearingDeg] from [lat]/[lon].
         */
        fun extrapolateLatLon(
            lat: Double,
            lon: Double,
            bearingDeg: Float,
            distanceM: Double,
        ): Pair<Double, Double> {
            if (distanceM <= 0.0 || !distanceM.isFinite()) return lat to lon
            val bearingRad = Math.toRadians(bearingDeg.toDouble())
            val north = distanceM * cos(bearingRad)
            val east = distanceM * sin(bearingRad)
            val latRad = Math.toRadians(lat)
            val dLat = north / METERS_PER_DEG_LAT
            val metersPerDegLon = METERS_PER_DEG_LAT * cos(latRad).coerceAtLeast(1e-6)
            val dLon = east / metersPerDegLon
            return (lat + dLat) to (lon + dLon)
        }
    }

    private var job: Job? = null
    private var collectJob: Job? = null
    private var lastSig: String? = null
    private var lastGoodLoc: LocValues? = null
    private var lastGoodAtElapsedMs: Long = 0L
    private var retainLat: Double = 0.0
    private var retainLon: Double = 0.0
    private var lastPushElapsedMs: Long = 0L
    private var wasRetaining: Boolean = false
    /** Last non-zero course from a live fix; used when retention sees bearing 0. */
    private var lastKnownBearingDeg: Float? = null

    fun start() {
        if (collectJob?.isActive == true) return
        collectJob = scope.launch {
            while (isActive) {
                restartInner()
                delay(500)
            }
        }
    }

    fun stop() {
        collectJob?.cancel()
        collectJob = null
        job?.cancel()
        job = null
        lastSig = null
        lastGoodLoc = null
        lastGoodAtElapsedMs = 0L
        wasRetaining = false
        lastKnownBearingDeg = null
        locationMockManager.stopMockLocation()
    }

    private fun restartInner() {
        val enabled = shouldPushMock(mockLocation.value, locationSource.value)
        val period = periodMs.value.coerceAtLeast(200L)
        val mode = canSpeedMode.value
        val sig = "$enabled:$period:${locationSource.value}:$mode"

        if (sig == lastSig) {
            if (!enabled) return
            if (job?.isActive == true) return
        }

        lastSig = sig
        job?.cancel()
        job = null
        if (!enabled) {
            locationMockManager.stopMockLocation()
            return
        }
        job = scope.launch {
            while (isActive) {
                pushOnce(mode)
                delay(period)
            }
        }
    }

    private fun pushOnce(mode: MockCanSpeedMode) {
        val now = SystemClock.elapsedRealtime()
        val live = TboxRepository.locValues.value
        val retaining: Boolean
        val base: LocValues

        if (live.locateStatus && hasValidCoordinates(live)) {
            lastGoodLoc = live
            lastGoodAtElapsedMs = now
            base = live
            retaining = false
            wasRetaining = false
            retainLat = live.latitude
            retainLon = live.longitude
            if (live.trueDirection != 0f) {
                lastKnownBearingDeg = live.trueDirection
            }
        } else {
            val good = lastGoodLoc
            if (good != null &&
                hasValidCoordinates(good) &&
                now - lastGoodAtElapsedMs <= FIX_RETENTION_MS
            ) {
                base = good
                retaining = true
                if (!wasRetaining) {
                    retainLat = good.latitude
                    retainLon = good.longitude
                    wasRetaining = true
                    if (good.trueDirection != 0f) {
                        lastKnownBearingDeg = good.trueDirection
                    }
                }
            } else {
                return
            }
        }

        val canKmh = TripTelemetryRepository.accountingCarSpeed(now)
        val useCan = when (mode) {
            MockCanSpeedMode.ALWAYS -> canKmh != null
            MockCanSpeedMode.WHEN_FIX_LOST -> retaining && canKmh != null
            MockCanSpeedMode.NONE -> false
        }
        val speedKmh = when {
            useCan -> canKmh!!
            retaining -> 0f
            else -> base.speed
        }
        val bearing = resolveBearingForExtrapolation(base.trueDirection, lastKnownBearingDeg)
        var lat = base.latitude
        var lon = base.longitude
        if (retaining) {
            val dtSec = if (lastPushElapsedMs > 0L) {
                ((now - lastPushElapsedMs).coerceAtLeast(0L) / 1000.0)
            } else {
                0.0
            }
            if (speedKmh > 0f && bearing != null && dtSec > 0.0) {
                val distanceM = (speedKmh / 3.6) * dtSec
                val stepped = extrapolateLatLon(retainLat, retainLon, bearing, distanceM)
                retainLat = stepped.first
                retainLon = stepped.second
            }
            lat = retainLat
            lon = retainLon
        }
        lastPushElapsedMs = now
        val out = base.copy(
            latitude = lat,
            longitude = lon,
            speed = speedKmh,
            trueDirection = bearing ?: base.trueDirection,
            locateStatus = true,
        )
        // Always publish speed (incl. 0) so consumers do not keep a stale value.
        locationMockManager.setMockLocation(
            locValues = out,
            retainingFix = retaining,
            hasReliableSpeed = true,
            hasReliableBearing = bearing != null,
        )
    }
}
