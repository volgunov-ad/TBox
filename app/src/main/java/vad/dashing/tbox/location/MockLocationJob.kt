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
 * Enhancement (CAN speed, retention up to [FIX_RETENTION_MS], coordinate DR, heading hold,
 * gyro yaw while retaining) runs only when [MockCanSpeedMode] is [MockCanSpeedMode.ALWAYS]
 * or [MockCanSpeedMode.WHEN_FIX_LOST]. With [MockCanSpeedMode.NONE], mock gets live GNSS
 * as-is from the selected source.
 *
 * [MockCanSpeedMode.ALWAYS]: CAN speed while live; on fix loss — retention + DR (+ CAN).
 * [MockCanSpeedMode.WHEN_FIX_LOST]: while live — GNSS as-is; on fix loss — retention + DR + CAN.
 *
 * Optional [junkFixFilterEnabled] (default on): always feeds [isLiveUsable] / truth.
 * When mock is pushing, junk live points are not written to the mock provider
 * (last good is kept; with enhance modes — full retention / DR).
 * Cold-start disk seed only when enhancement mode is on.
 * Reverse gear: [UniversalCanRepository.reverseGearSwitchState] is populated when a
 * `gearBoxMode` widget uses HU CAN (or via `MbCanSignal.ReverseGearSwitch` interest);
 * mock/DR invert is not applied yet.
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
        /** Keep last valid coordinates in mock after fix loss (10 minutes). */
        const val FIX_RETENTION_MS = 600_000L

        /** Cap gyro integration step (matches typical HU sample cadence / HWGPS Jetour dt). */
        const val MAX_YAW_INTEGRATION_DT_SEC = 0.25

        /** Ignore stale gyro samples. */
        const val MAX_YAW_SAMPLE_AGE_MS = 1_000L

        /** Reject absurd yaw rates (°/s). */
        const val MAX_ABS_YAW_RATE_DEG_PER_SEC = 80f

        /**
         * Below this speed (km/h), ignore GNSS course updates and do not integrate yaw.
         * ~0.5 m/s — same ballpark as HWGPS motion gate.
         */
        const val COURSE_HOLD_MIN_KMH = 1.8f

        /** After bias, |yaw| below this (°/s) is treated as zero for DR. */
        const val YAW_DEADBAND_DEG_PER_SEC = 0.7f

        private const val METERS_PER_DEG_LAT = 111_320.0

        fun shouldPushMock(mockEnabled: Boolean, source: LocationSource): Boolean =
            mockEnabled && source != LocationSource.ANDROID

        fun hasValidCoordinates(loc: LocValues): Boolean =
            loc.latitude != 0.0 || loc.longitude != 0.0

        fun isLiveUsable(
            loc: LocValues,
            junkFilterOn: Boolean,
            carSpeedKmh: Float?,
            nowElapsedMs: Long,
        ): Boolean =
            loc.locateStatus &&
                hasValidCoordinates(loc) &&
                (!junkFilterOn || MockJunkFixFilter.isAcceptable(loc, carSpeedKmh, nowElapsedMs))

        /**
         * Live has fix+coords but [isLiveUsable] is false while junk detection is on
         * (does not re-run the filter — pass the same [liveUsable] from this tick).
         */
        fun isJunkLive(
            loc: LocValues,
            junkFilterOn: Boolean,
            liveUsable: Boolean,
        ): Boolean =
            junkFilterOn &&
                loc.locateStatus &&
                hasValidCoordinates(loc) &&
                !liveUsable

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
         * Accept GNSS course only when moving and course is non-zero.
         */
        fun shouldAcceptGnssCourse(speedKmh: Float, courseDeg: Float): Boolean =
            speedKmh >= COURSE_HOLD_MIN_KMH && courseDeg != 0f && courseDeg.isFinite()

        /**
         * Apply deadband after bias; null if unusable / zeroed.
         */
        fun applyYawDeadband(yawRateDegPerSec: Float?): Float? {
            val yaw = yawRateDegPerSec ?: return null
            if (!yaw.isFinite()) return null
            if (kotlin.math.abs(yaw) > MAX_ABS_YAW_RATE_DEG_PER_SEC) return null
            if (kotlin.math.abs(yaw) < YAW_DEADBAND_DEG_PER_SEC) return null
            return yaw
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

        fun formatSatellites(visible: Int, using: Int): String =
            if (visible == using) visible.toString() else "$visible/$using"

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
        // Leave GeoDisplayRepository to live passthrough from BackgroundService.
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
        val bearingForDisk = lastKnownBearingDeg?.takeIf { it != 0f } ?: loc.trueDirection
        val fix = MockLastGoodFix.fromLive(loc, System.currentTimeMillis(), bearingForDisk) ?: return
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
        val liveUsable = isLiveUsable(live, junkFilterOn, canKmh, now)

        if (liveUsable) {
            lastGoodLoc = live
            lastGoodAtElapsedMs = now
            usingPersistedSeed = false
            if (shouldAcceptGnssCourse(live.speed, live.trueDirection)) {
                lastKnownBearingDeg = live.trueDirection
            }
            // Persist for cold start / junk hold even without enhance mode.
            persistLiveGood(live, now)
        }

        if (!mode.enhancesMock) {
            wasRetaining = false
            usingPersistedSeed = false
            lastPushElapsedMs = now
            if (liveUsable) {
                publishLivePassthrough(live, liveUsable = true)
            } else if (isJunkLive(live, junkFilterOn, liveUsable)) {
                val good = lastGoodLoc
                if (good != null && hasValidCoordinates(good)) {
                    publishStaticLastGood(good, liveUsable = false)
                } else {
                    // No last good yet — do not push junk into mock.
                    publishLostDisplay(liveUsable = false, live = live)
                }
            } else {
                // No enhance, not junk (e.g. no fix) — GNSS as-is.
                publishLivePassthrough(live, liveUsable = false)
            }
            return
        }

        // Enhancement modes: junk / no-fix → retention path (ignore live for mock out).
        val retaining: Boolean
        val base: LocValues

        if (liveUsable) {
            base = live
            retaining = false
            wasRetaining = false
            retainLat = live.latitude
            retainLon = live.longitude
            val speedForCourse = when (mode) {
                MockCanSpeedMode.ALWAYS -> canKmh ?: live.speed
                else -> live.speed
            }
            if (shouldAcceptGnssCourse(speedForCourse, live.trueDirection)) {
                lastKnownBearingDeg = live.trueDirection
            }
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
                    if (shouldAcceptGnssCourse(good.speed, good.trueDirection) ||
                        (good.trueDirection != 0f && lastKnownBearingDeg == null)
                    ) {
                        lastKnownBearingDeg = good.trueDirection
                    }
                }
            } else {
                publishLostDisplay(liveUsable = false, live = live)
                return
            }
        }

        // WHEN_FIX_LOST + live: GNSS as-is (no CAN / heading hold while fix is good).
        if (mode == MockCanSpeedMode.WHEN_FIX_LOST && !retaining) {
            lastPushElapsedMs = now
            publishLivePassthrough(live, liveUsable = true)
            return
        }

        val useCan = when (mode) {
            MockCanSpeedMode.ALWAYS -> canKmh != null
            MockCanSpeedMode.WHEN_FIX_LOST -> retaining && canKmh != null
            MockCanSpeedMode.NONE -> false
        }
        val speedKmh = when {
            useCan -> DriveCalibrationStore.applyCanSpeed(canKmh!!)
            retaining -> 0f
            else -> base.speed
        }
        val speedSource = when {
            useCan -> GeoSpeedSource.CAN
            retaining -> GeoSpeedSource.RETENTION
            else -> GeoSpeedSource.GNSS
        }

        var bearing = lastKnownBearingDeg?.takeIf { it != 0f }
        var bearingSource = when {
            retaining -> GeoBearingSource.RETENTION
            bearing != null -> GeoBearingSource.HELD
            else -> GeoBearingSource.HELD
        }
        if (!retaining && shouldAcceptGnssCourse(speedKmh, base.trueDirection)) {
            bearing = base.trueDirection
            bearingSource = GeoBearingSource.GNSS
            lastKnownBearingDeg = bearing
        }

        var lat = base.latitude
        var lon = base.longitude
        if (retaining) {
            val dtSec = if (lastPushElapsedMs > 0L) {
                ((now - lastPushElapsedMs).coerceAtLeast(0L) / 1000.0)
            } else {
                0.0
            }
            if (bearing != null &&
                speedKmh >= COURSE_HOLD_MIN_KMH &&
                dtSec > 0.0
            ) {
                val yaw = usableYawRateDegPerSec(now)
                if (yaw != null) {
                    bearing = integrateYawIntoBearing(bearing, yaw, dtSec)
                    lastKnownBearingDeg = bearing
                    bearingSource = GeoBearingSource.RETENTION
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
        val outBearing = bearing
        val out = base.copy(
            latitude = lat,
            longitude = lon,
            speed = speedKmh,
            trueDirection = outBearing ?: 0f,
            locateStatus = true,
        )
        locationMockManager.setMockLocation(
            locValues = out,
            retainingFix = retaining,
            hasReliableSpeed = true,
            hasReliableBearing = outBearing != null,
        )
        GeoDisplayRepository.publish(
            GeoDisplayState(
                liveUsable = liveUsable,
                retaining = retaining,
                locateStatus = true,
                latitude = lat,
                longitude = lon,
                altitude = base.altitude,
                speedKmh = speedKmh,
                speedSource = speedSource,
                bearingDeg = outBearing,
                bearingSource = bearingSource,
                hasReliableBearing = outBearing != null,
                visibleSatellites = base.visibleSatellites,
                usingSatellites = base.usingSatellites,
                mockActive = true,
            ),
        )
    }

    /** Push live GNSS without CAN / retention / heading-hold / DR. */
    private fun publishLivePassthrough(live: LocValues, liveUsable: Boolean) {
        val bearing = live.trueDirection.takeIf { it != 0f }
        locationMockManager.setMockLocation(
            locValues = live,
            retainingFix = false,
            hasReliableSpeed = true,
            hasReliableBearing = bearing != null,
        )
        GeoDisplayRepository.publish(
            GeoDisplayState.fromLive(
                loc = live,
                liveUsable = liveUsable,
                mockActive = true,
            ),
        )
    }

    /** Hold last good in mock without DR (junk rejected while enhance mode is off). */
    private fun publishStaticLastGood(good: LocValues, liveUsable: Boolean) {
        val bearing = lastKnownBearingDeg?.takeIf { it != 0f }
            ?: good.trueDirection.takeIf { it != 0f }
        val out = good.copy(
            trueDirection = bearing ?: 0f,
            locateStatus = true,
        )
        locationMockManager.setMockLocation(
            locValues = out,
            retainingFix = false,
            hasReliableSpeed = true,
            hasReliableBearing = bearing != null,
        )
        GeoDisplayRepository.publish(
            GeoDisplayState(
                liveUsable = liveUsable,
                retaining = false,
                locateStatus = true,
                latitude = good.latitude,
                longitude = good.longitude,
                altitude = good.altitude,
                speedKmh = good.speed,
                speedSource = GeoSpeedSource.GNSS,
                bearingDeg = bearing,
                bearingSource = if (bearing != null) GeoBearingSource.HELD else GeoBearingSource.HELD,
                hasReliableBearing = bearing != null,
                visibleSatellites = good.visibleSatellites,
                usingSatellites = good.usingSatellites,
                mockActive = true,
            ),
        )
    }

    private fun publishLostDisplay(liveUsable: Boolean, live: LocValues) {
        GeoDisplayRepository.publish(
            GeoDisplayState(
                liveUsable = liveUsable,
                retaining = false,
                locateStatus = live.locateStatus,
                latitude = live.latitude,
                longitude = live.longitude,
                altitude = live.altitude,
                speedKmh = live.speed,
                speedSource = GeoSpeedSource.GNSS,
                bearingDeg = lastKnownBearingDeg,
                bearingSource = GeoBearingSource.HELD,
                hasReliableBearing = lastKnownBearingDeg != null,
                visibleSatellites = live.visibleSatellites,
                usingSatellites = live.usingSatellites,
                mockActive = true,
            ),
        )
    }

    private fun usableYawRateDegPerSec(nowElapsedMs: Long): Float? {
        val raw = yawRateDegPerSec()
        val corrected = GyroBiasStore.applyYaw(raw)
        val scaled = corrected?.let { DriveCalibrationStore.applyYawRate(it) }
        val yaw = applyYawDeadband(scaled) ?: return null
        val sampleAt = yawSampleElapsedMs()
        if (sampleAt <= 0L || nowElapsedMs - sampleAt > MAX_YAW_SAMPLE_AGE_MS) return null
        return yaw
    }
}
