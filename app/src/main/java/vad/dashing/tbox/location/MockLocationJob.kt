package vad.dashing.tbox.location

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vad.dashing.tbox.LocValues
import vad.dashing.tbox.TboxRepository
import vad.dashing.tbox.TripTelemetryRepository
import vad.dashing.tbox.drsensor.DrSensorRepository
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
 * course; if it is 0 / missing, the last non-zero course is reused. While retaining, fresh
 * HU gyro yaw (°/s from [DrSensorRepository]) updates the course (left +, right − → nav
 * bearing decreases on left turn). Reverse gear is not applied yet.
 *
 * Optional [junkFixFilterEnabled]: live fixes that fail [MockJunkFixFilter] are ignored
 * (same path as no fix — retention / DR on the last good point).
 *
 * Accepted live points are persisted (60 s debounce) for cold start: if CAN speed mode is
 * [MockCanSpeedMode.ALWAYS] or [MockCanSpeedMode.WHEN_FIX_LOST] and there is no in-memory
 * good fix yet, a fresh disk seed is used until a live fix arrives (no 120 s cap).
 */
class MockLocationJob(
    private val scope: CoroutineScope,
    private val locationMockManager: LocationMockManager,
    private val mockLocation: StateFlow<Boolean>,
    private val locationSource: StateFlow<LocationSource>,
    private val periodMs: StateFlow<Long>,
    private val canSpeedMode: StateFlow<MockCanSpeedMode>,
    private val junkFixFilterEnabled: StateFlow<Boolean>,
    private val loadPersistedLastGood: suspend () -> MockLastGoodFix?,
    private val savePersistedLastGood: suspend (MockLastGoodFix) -> Unit,
    private val yawRateDegPerSec: () -> Float? = {
        DrSensorRepository.snapshot.value.gyroYaw
    },
    private val yawSampleElapsedMs: () -> Long = {
        DrSensorRepository.snapshot.value.lastUpdateElapsedMs
    },
) {
    companion object {
        /** Keep last valid coordinates in mock after fix loss. */
        const val FIX_RETENTION_MS = 120_000L

        /** Cap gyro integration step (matches typical HU sample cadence / HWGPS Jetour dt). */
        const val MAX_YAW_INTEGRATION_DT_SEC = 0.25

        /** Ignore stale gyro samples. */
        const val MAX_YAW_SAMPLE_AGE_MS = 1_000L

        /** Reject absurd yaw rates (°/s). */
        const val MAX_ABS_YAW_RATE_DEG_PER_SEC = 80f

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
         * Integrate HU gyro yaw into navigation bearing.
         * Yaw: left +, right − (°/s). Nav bearing: 0=N, 90=E, clockwise → subtract yaw×dt.
         */
        fun integrateYawIntoBearing(
            bearingDeg: Float,
            yawRateDegPerSec: Float,
            dtSec: Double,
        ): Float {
            if (!bearingDeg.isFinite() || !yawRateDegPerSec.isFinite() || !dtSec.isFinite()) {
                return bearingDeg
            }
            if (dtSec <= 0.0) return bearingDeg
            if (kotlin.math.abs(yawRateDegPerSec) > MAX_ABS_YAW_RATE_DEG_PER_SEC) {
                return bearingDeg
            }
            val dt = dtSec.coerceAtMost(MAX_YAW_INTEGRATION_DT_SEC)
            val next = bearingDeg - yawRateDegPerSec * dt.toFloat()
            return wrapBearingDeg(next)
        }

        fun wrapBearingDeg(bearingDeg: Float): Float {
            var b = bearingDeg % 360f
            if (b < 0f) b += 360f
            return b
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
    /** Retention from disk seed until first live good fix (not limited by [FIX_RETENTION_MS]). */
    private var usingPersistedSeed: Boolean = false
    private var persistedSeed: MockLastGoodFix? = null
    private var persistedSeedLoaded: Boolean = false
    private val persistDebouncer = MockLastGoodFixDebouncer()

    fun start() {
        if (collectJob?.isActive == true) return
        collectJob = scope.launch {
            if (!persistedSeedLoaded) {
                persistedSeed = loadPersistedLastGood()
                persistedSeedLoaded = true
            }
            while (isActive) {
                restartInner()
                delay(500)
            }
        }
    }

    fun stop() {
        flushPersistedAsync()
        collectJob?.cancel()
        collectJob = null
        job?.cancel()
        job = null
        lastSig = null
        lastGoodLoc = null
        lastGoodAtElapsedMs = 0L
        wasRetaining = false
        lastKnownBearingDeg = null
        usingPersistedSeed = false
        lastPushElapsedMs = 0L
        locationMockManager.stopMockLocation()
    }

    private fun flushPersistedAsync() {
        val pending = persistDebouncer.takeFlush() ?: return
        scope.launch {
            withContext(NonCancellable) {
                runCatching { savePersistedLastGood(pending) }
            }
        }
    }

    private fun persistLiveGood(loc: LocValues, nowElapsedMs: Long) {
        val fix = MockLastGoodFix.fromLive(loc, System.currentTimeMillis()) ?: return
        val toWrite = persistDebouncer.note(fix, nowElapsedMs) ?: return
        scope.launch {
            runCatching { savePersistedLastGood(toWrite) }
        }
    }

    private fun trySeedFromPersisted(mode: MockCanSpeedMode, nowElapsedMs: Long): Boolean {
        if (lastGoodLoc != null) return false
        if (!MockLastGoodFix.canUseForColdStart(mode)) return false
        val seed = persistedSeed ?: return false
        if (!seed.isFresh(System.currentTimeMillis())) {
            persistedSeed = null
            return false
        }
        lastGoodLoc = seed.toLocValues()
        lastGoodAtElapsedMs = nowElapsedMs
        usingPersistedSeed = true
        if (seed.bearingDeg != 0f) {
            lastKnownBearingDeg = seed.bearingDeg
        }
        persistedSeed = null
        return true
    }

    private fun restartInner() {
        val enabled = shouldPushMock(mockLocation.value, locationSource.value)
        val period = periodMs.value.coerceAtLeast(200L)
        val mode = canSpeedMode.value
        val filterOn = junkFixFilterEnabled.value
        val sig = "$enabled:$period:${locationSource.value}:$mode:$filterOn"

        if (sig == lastSig) {
            if (!enabled) return
            if (job?.isActive == true) return
        }

        lastSig = sig
        job?.cancel()
        job = null
        if (!enabled) {
            flushPersistedAsync()
            locationMockManager.stopMockLocation()
            return
        }
        job = scope.launch {
            while (isActive) {
                pushOnce(mode, filterOn)
                delay(period)
            }
        }
    }

    private fun pushOnce(mode: MockCanSpeedMode, junkFilterOn: Boolean) {
        val now = SystemClock.elapsedRealtime()
        val live = TboxRepository.locValues.value
        val canKmh = TripTelemetryRepository.accountingCarSpeed(now)
        val liveUsable = live.locateStatus &&
            hasValidCoordinates(live) &&
            (!junkFilterOn || MockJunkFixFilter.isAcceptable(live, canKmh))
        val retaining: Boolean
        val base: LocValues

        if (liveUsable) {
            lastGoodLoc = live
            lastGoodAtElapsedMs = now
            usingPersistedSeed = false
            base = live
            retaining = false
            wasRetaining = false
            retainLat = live.latitude
            retainLon = live.longitude
            if (live.trueDirection != 0f) {
                lastKnownBearingDeg = live.trueDirection
            }
            persistLiveGood(live, now)
        } else {
            if (lastGoodLoc == null) {
                trySeedFromPersisted(mode, now)
            }
            val good = lastGoodLoc
            val retentionOk = usingPersistedSeed ||
                (good != null && now - lastGoodAtElapsedMs <= FIX_RETENTION_MS)
            if (good != null && hasValidCoordinates(good) && retentionOk) {
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
        var bearing = resolveBearingForExtrapolation(base.trueDirection, lastKnownBearingDeg)
        var lat = base.latitude
        var lon = base.longitude
        if (retaining) {
            val dtSec = if (lastPushElapsedMs > 0L) {
                ((now - lastPushElapsedMs).coerceAtLeast(0L) / 1000.0)
            } else {
                0.0
            }
            if (bearing != null && dtSec > 0.0) {
                val yaw = usableYawRateDegPerSec(now)
                if (yaw != null) {
                    bearing = integrateYawIntoBearing(bearing, yaw, dtSec)
                    lastKnownBearingDeg = bearing
                }
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

    private fun usableYawRateDegPerSec(nowElapsedMs: Long): Float? {
        val yaw = yawRateDegPerSec() ?: return null
        if (!yaw.isFinite()) return null
        if (kotlin.math.abs(yaw) > MAX_ABS_YAW_RATE_DEG_PER_SEC) return null
        val sampleAt = yawSampleElapsedMs()
        if (sampleAt <= 0L || nowElapsedMs - sampleAt > MAX_YAW_SAMPLE_AGE_MS) return null
        return yaw
    }
}
