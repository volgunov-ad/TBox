package vad.dashing.tbox.location

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
 * Enhancement (CAN speed, retention up to [FIX_RETENTION_MS], coordinate DR, heading hold,
 * gyro yaw while retaining) runs only when [MockCanSpeedMode] is [MockCanSpeedMode.ALWAYS]
 * or [MockCanSpeedMode.WHEN_FIX_LOST]. With [MockCanSpeedMode.NONE], mock gets live GNSS
 * as-is from the selected source.
 *
 * [MockCanSpeedMode.ALWAYS]: CAN speed while live; on fix loss — retention + DR (+ CAN).
 * [MockCanSpeedMode.WHEN_FIX_LOST]: while live — GNSS as-is; on fix loss — retention + DR + CAN.
 * [MockCanSpeedMode.CONSTANT]: continuous shadow + soft GNSS blend (Advanced);
 * junk filter is ignored (soft weights handle bad GNSS).
 *
 * DR path length uses [SpeedIntegrator] (trapezoid over accounting-speed samples
 * between mock ticks) instead of a single `v_end · Δt`. Heading uses gyro or
 * steering via [applyHeadingDelta] / [SteerHeadingIntegrator].
 *
 * Optional [junkFixFilterEnabled] (default on): always feeds [isLiveUsable] / truth for
 * NONE / ALWAYS / WHEN_FIX_LOST. CONSTANT bypasses junk for its own path.
 * Cold-start disk seed when enhancement / CONSTANT is on.
 * Reverse gear is subscribed while enhancement (incl. CONSTANT) is active.
 * When [considerReverseEnabled] is on, reverse (HU PRND → switch → TBox) inverts travel
 * bearing in all enhancement modes; Direct ([MockCanSpeedMode.NONE]) never uses reverse.
 *
 * Online yaw bias/scale ([OnlineYawCalibEstimator]) runs in all enhancement modes while
 * GNSS is truthful (not in Direct).
 */
class MockLocationJob(
    private val scope: CoroutineScope,
    private val locationMockManager: LocationMockManager,
    private val mockPower: StateFlow<MockPowerState>,
    private val locationSource: StateFlow<LocationSource>,
    private val periodMs: StateFlow<Long>,
    private val canSpeedMode: StateFlow<MockCanSpeedMode>,
    private val headingSource: StateFlow<MockHeadingSource> =
        kotlinx.coroutines.flow.MutableStateFlow(MockHeadingSource.GYRO),
    private val junkFixFilterEnabled: StateFlow<Boolean>,
    private val constantAutoCalibEnabled: StateFlow<Boolean> = kotlinx.coroutines.flow.MutableStateFlow(false),
    private val considerReverseEnabled: StateFlow<Boolean> = kotlinx.coroutines.flow.MutableStateFlow(true),
    private val loadPersistedLastGood: suspend () -> MockLastGoodFix?,
    private val savePersistedLastGood: suspend (MockLastGoodFix) -> Unit,
    private val onConstantMismatchNeedsCalib: () -> Unit = {},
    /** Debounced persist of online yaw bias (enhancement modes). */
    private val onOnlineGyroBiasPersist: (GyroBiasOffsets) -> Unit = {},
    /** Debounced persist of online yaw scale (enhancement modes). */
    private val onOnlineDriveCalibPersist: (DriveCalibrationOffsets) -> Unit = {},
) {
    companion object {
        /** Keep last valid coordinates in mock after fix loss (10 minutes). */
        const val FIX_RETENTION_MS = 600_000L

        /**
         * Max gap between consecutive high-rate gyro samples ([YawIntegrator.MAX_SAMPLE_DT_SEC]).
         * Used by [integrateYawIntoBearing] when applying an explicit dt — not the mock period.
         */
        const val MAX_YAW_INTEGRATION_DT_SEC = YawIntegrator.MAX_SAMPLE_DT_SEC

        /** Ignore stale gyro samples (instantaneous rate / diagnostics). */
        const val MAX_YAW_SAMPLE_AGE_MS = 1_000L

        /** Reject absurd yaw rates (°/s). */
        const val MAX_ABS_YAW_RATE_DEG_PER_SEC = YawIntegrator.MAX_ABS_YAW_RATE_DEG_PER_SEC

        /**
         * Below this speed (km/h), ignore GNSS course updates and do not integrate yaw.
         * ~0.5 m/s — same ballpark as HWGPS motion gate.
         */
        const val COURSE_HOLD_MIN_KMH = 1.8f

        /** After bias, |yaw| below this (°/s) is treated as zero for DR. */
        const val YAW_DEADBAND_DEG_PER_SEC = YawIntegrator.YAW_DEADBAND_DEG_PER_SEC

        /** Interest source for HU gear / reverse while enhanced mock is active. */
        const val MOCK_DR_GEAR_SOURCE_ID = "mock-location-dr-gear"

        /** Interest source for steering angle while STEER heading is active. */
        const val MOCK_DR_STEER_SOURCE_ID = "mock-location-dr-steering"

        private const val METERS_PER_DEG_LAT = 111_320.0

        fun shouldPushMock(mockEnabled: Boolean, source: LocationSource): Boolean =
            mockEnabled && source != LocationSource.ANDROID

        fun shouldPushMock(power: MockPowerState, source: LocationSource): Boolean =
            shouldPushMock(power.isMockEnabled, source)

        /**
         * GNSS has a usable fix for [MockPowerState.WHEN_NO_FIX] injection gating
         * (fresh coords + locateStatus). Junk filter is not required here.
         */
        fun hasGnssFixForPowerGate(
            live: LocValues,
            gnssFresh: Boolean,
        ): Boolean = gnssFresh && live.locateStatus && hasValidCoordinates(live)

        /**
         * Reverse for DR: HU PRND first, else CEM switch (MT), else TBox PRND.
         * See [vad.dashing.tbox.mbcan.VehicleGearDomain.isReverseEngaged].
         */
        fun isReverseEngagedNow(): Boolean {
            val huSwitch =
                vad.dashing.tbox.mbcan.UniversalCanRepository.reverseGearSwitchState.value
            val huMode = vad.dashing.tbox.mbcan.UniversalCanRepository.gearBoxModeState.value
            val tboxMode = vad.dashing.tbox.CanDataRepository.gearBoxMode.value
            return vad.dashing.tbox.mbcan.VehicleGearDomain.isReverseEngaged(
                huSwitch,
                huMode,
                tboxMode,
            )
        }

        /**
         * Apply reverse travel invert only when the setting is on and the mode is not Direct.
         */
        fun shouldApplyReverse(mode: MockCanSpeedMode, considerReverse: Boolean): Boolean =
            considerReverse && mode.enhancesMock && isReverseEngagedNow()

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
         * CAN-first standstill gate for enhance / Advanced: if CAN speed is present,
         * it alone decides motion — so phantom GNSS speed on a stop cannot refresh COG.
         * When CAN is absent, fall back to GNSS speed.
         */
        fun shouldAcceptGnssCourse(
            canKmh: Float?,
            gnssSpeedKmh: Float,
            courseDeg: Float,
        ): Boolean {
            val speedForGate = canKmh ?: gnssSpeedKmh
            return shouldAcceptGnssCourse(speedForGate, courseDeg)
        }

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
         * Apply a pre-integrated nav bearing delta (from [YawIntegrator.consumeDeltaDeg]).
         */
        fun applyYawDeltaToBearing(bearingDeg: Float, deltaDeg: Float): Float {
            if (!bearingDeg.isFinite() || !deltaDeg.isFinite()) return bearingDeg
            if (deltaDeg == 0f) return bearingDeg
            return wrapBearingDeg(bearingDeg + deltaDeg)
        }

        /**
         * Integrate HU gyro yaw into navigation bearing (single rate × dt helper).
         * Prefer [YawIntegrator] + [applyYawDeltaToBearing] for mock DR so high-rate
         * samples are not lost between mock ticks.
         * Yaw: left +, right − (°/s). Nav bearing: 0=N, 90=E, clockwise → subtract yaw×dt.
         * [dtSec] is clamped to [MAX_YAW_INTEGRATION_DT_SEC] (per-sample gap, not mock period).
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
         * Mid-course for one DR step: half the shortest signed turn from [fromDeg]
         * to [toDeg]. Used so path length is not projected entirely on the end nose.
         */
        fun averageBearingDeg(fromDeg: Float, toDeg: Float): Float {
            if (!fromDeg.isFinite()) return wrapBearingDeg(toDeg)
            if (!toDeg.isFinite()) return wrapBearingDeg(fromDeg)
            val d = DriveCalibrationMath.wrapDeltaDeg(fromDeg, toDeg)
            return wrapBearingDeg(fromDeg + d * 0.5f)
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
    /** Accounting-speed samples → [SpeedIntegrator] between mock ticks. */
    private var speedSampleJob: Job? = null
    private var lastSig: String? = null
    private var lastGoodLoc: LocValues? = null
    private var lastGoodAtElapsedMs: Long = 0L
    private var retainLat: Double = 0.0
    private var retainLon: Double = 0.0
    private var lastPushElapsedMs: Long = 0L
    private var wasRetaining: Boolean = false
    /** Last non-zero course from a live fix; used when retention sees bearing 0. */
    private var lastKnownBearingDeg: Float? = null
    private var gearInterestActive = false
    /** Desired state is checked under [gearInterestMutex] to serialize set/clear races. */
    @Volatile private var desiredGearInterest = false
    private val gearInterestMutex = Mutex()
    private var steerInterestActive = false
    @Volatile private var desiredSteerInterest = false
    private val steerInterestMutex = Mutex()
    private var steerSampleJob: Job? = null
    /** Retention from disk seed until first live good fix (not limited by [FIX_RETENTION_MS]). */
    private var usingPersistedSeed: Boolean = false
    private var persistedSeed: MockLastGoodFix? = null
    private var persistedSeedLoaded: Boolean = false
    private val persistDebouncer = MockLastGoodFixDebouncer()

    /** CONSTANT: consecutive large shadow↔GNSS mismatches (auto-calib gate). */
    private var constantMismatchStreak: Int = 0
    private var lastCalibSeenAtEpochMs: Long = 0L
    private var constantAlt: Double = 0.0
    private var constantVisibleSats: Int = 0
    private var constantUsingSats: Int = 0
    private var constantHasOrigin: Boolean = false
    private var lastMode: MockCanSpeedMode? = null
    private var lastEnabled: Boolean? = null
    /** Elapsed ms when continuous hard-resync GNSS trust started; 0 = not trusting. */
    private var hardResyncTrustSinceElapsedMs: Long = 0L

    /** Continuous yaw bias/scale from truthful GNSS (CONSTANT only). */
    private val onlineYawCalib = OnlineYawCalibEstimator()

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
        clearGearInterest()
        clearSteerInterest()
        steerSampleJob?.cancel()
        steerSampleJob = null
        stopSpeedSampleCollection()
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
        constantMismatchStreak = 0
        lastCalibSeenAtEpochMs = 0L
        constantHasOrigin = false
        lastMode = null
        lastEnabled = null
        hardResyncTrustSinceElapsedMs = 0L
        onlineYawCalib.reset()
        ConstantDrRuntimeDebug.clear()
        OnlineYawCalibRuntimeDebug.clear()
        YawIntegrator.discard()
        SteerHeadingIntegrator.reset()
        SpeedIntegrator.reset()
        locationMockManager.stopMockLocation()
        // Leave GeoDisplayRepository to live passthrough from BackgroundService.
    }

    /**
     * Collect accounting speed only (same priority/freshness as [TripTelemetryRepository.accountingCarSpeed]).
     * Do not also collect [UniversalCanRepository.carSpeedState] — that would double-count HU updates.
     * While STEER heading is active, the same samples also advance [SteerHeadingIntegrator]
     * so held-wheel turns track speed changes between angle emits.
     */
    private fun ensureSpeedSampleCollection() {
        if (speedSampleJob?.isActive == true) return
        speedSampleJob = scope.launch {
            TripTelemetryRepository.carSpeed.collect { speed ->
                val now = SystemClock.elapsedRealtime()
                SpeedIntegrator.onRawSample(speed, now)
                if (headingSource.value == MockHeadingSource.STEER) {
                    SteerHeadingIntegrator.onSpeedKmh(signedSteerSpeedKmh(speed), now)
                }
            }
        }
    }

    /** Signed calibrated speed for the bicycle model (negative in reverse). */
    private fun signedSteerSpeedKmh(rawCanKmh: Float?): Float? {
        if (rawCanKmh == null || !rawCanKmh.isFinite()) return null
        val scaled = DriveCalibrationStore.applyCanSpeed(rawCanKmh)
        val reverse = shouldApplyReverse(canSpeedMode.value, considerReverseEnabled.value)
        return if (reverse) -scaled else scaled
    }

    private fun signedSteerSpeedKmhNow(now: Long): Float? =
        signedSteerSpeedKmh(TripTelemetryRepository.accountingCarSpeed(now))

    private fun stopSpeedSampleCollection() {
        speedSampleJob?.cancel()
        speedSampleJob = null
        SpeedIntegrator.reset()
    }

    /**
     * Take integrated path length for one DR step.
     *
     * Always [SpeedIntegrator.flushTo] first so constant-speed stretches without
     * StateFlow re-emits still cover the full mock period (1–5 s). Then refresh
     * the held sample with current [canKmh] at the same timestamp (no extra gap).
     * When [stepAllowed] is false, pending distance is discarded.
     */
    private fun takeDrDistanceM(now: Long, canKmh: Float?, stepAllowed: Boolean): Double {
        if (!stepAllowed) {
            SpeedIntegrator.discardThrough(now)
            return 0.0
        }
        SpeedIntegrator.flushTo(now)
        if (canKmh != null) {
            // Same timestamp as flush → updates hold without integrating another dt.
            SpeedIntegrator.onRawSample(canKmh, now)
        }
        val d = SpeedIntegrator.consumeDistanceM()
        return if (d.isFinite() && d > 0.0) d else 0.0
    }

    /**
     * While DR step is gated off (stopped / too slow / no nose), keep the speed
     * hold current and discard pending distance so the next pull-away tick does
     * not clamp across a long idle gap.
     */
    private fun refreshSpeedIntegratorWhileGated(now: Long, canKmh: Float?) {
        if (canKmh != null) {
            SpeedIntegrator.flushTo(now)
            SpeedIntegrator.onRawSample(canKmh, now)
            SpeedIntegrator.discardThrough(now)
        } else {
            // accountingCarSpeed can become null from freshness/path state without
            // carSpeed StateFlow emitting null. Clear held speed explicitly so a
            // same-value recovery cannot backfill the unknown interval.
            SpeedIntegrator.onRawSample(null, now)
            SpeedIntegrator.discard()
        }
    }

    private fun ensureGearInterest(enhanceOn: Boolean) {
        desiredGearInterest = enhanceOn
        scope.launch {
            gearInterestMutex.withLock {
                val wanted = desiredGearInterest
                if (wanted == gearInterestActive) return@withLock
                runCatching {
                    if (wanted) {
                        vad.dashing.tbox.mbcan.UniversalCanRepository.setSourceSignals(
                            MOCK_DR_GEAR_SOURCE_ID,
                            setOf(
                                vad.dashing.tbox.mbcan.MbCanSignal.VehicleGear,
                                vad.dashing.tbox.mbcan.MbCanSignal.ReverseGearSwitch,
                            ),
                        )
                    } else {
                        vad.dashing.tbox.mbcan.UniversalCanRepository.enqueueClearSource(
                            MOCK_DR_GEAR_SOURCE_ID,
                        )
                    }
                }
                gearInterestActive = wanted
            }
        }
    }

    private fun clearGearInterest() {
        ensureGearInterest(enhanceOn = false)
    }

    private fun ensureSteerInterest(steerHeadingActive: Boolean) {
        desiredSteerInterest = steerHeadingActive
        scope.launch {
            steerInterestMutex.withLock {
                val wanted = desiredSteerInterest
                if (wanted == steerInterestActive) return@withLock
                runCatching {
                    if (wanted) {
                        vad.dashing.tbox.mbcan.UniversalCanRepository.setSourceSignals(
                            MOCK_DR_STEER_SOURCE_ID,
                            setOf(vad.dashing.tbox.mbcan.MbCanSignal.SteeringAngle),
                        )
                    } else {
                        vad.dashing.tbox.mbcan.UniversalCanRepository.enqueueClearSource(
                            MOCK_DR_STEER_SOURCE_ID,
                        )
                    }
                }
                steerInterestActive = wanted
                if (wanted) {
                    ensureSteerSampleCollection()
                } else {
                    steerSampleJob?.cancel()
                    steerSampleJob = null
                    SteerHeadingIntegrator.reset()
                }
            }
        }
    }

    private fun clearSteerInterest() {
        ensureSteerInterest(steerHeadingActive = false)
    }

    private fun ensureSteerSampleCollection() {
        if (steerSampleJob?.isActive == true) return
        steerSampleJob = scope.launch {
            vad.dashing.tbox.mbcan.UniversalCanRepository.steerAngleState.collect { angle ->
                val now = SystemClock.elapsedRealtime()
                // Speed timebase is advanced by the speed collector; here only refresh v.
                SteerHeadingIntegrator.onSpeedKmh(signedSteerSpeedKmhNow(now))
                SteerHeadingIntegrator.onRawSample(angle, now)
            }
        }
    }

    /**
     * Apply pending heading delta from gyro or steer, discarding the inactive integrator.
     * Returns updated nose and whether a non-zero delta was applied.
     */
    private fun applyHeadingDelta(
        nose: Float,
        source: MockHeadingSource,
        allowIntegrate: Boolean,
        now: Long = SystemClock.elapsedRealtime(),
    ): Pair<Float, Boolean> {
        if (!allowIntegrate) {
            YawIntegrator.discardThrough(now)
            SteerHeadingIntegrator.discardThrough(now)
            return nose to false
        }
        return when (source) {
            MockHeadingSource.GYRO -> {
                SteerHeadingIntegrator.discardThrough(now)
                val lastYawAt = YawIntegrator.lastSampleElapsedMs()
                if (lastYawAt > 0L && now - lastYawAt > MAX_YAW_SAMPLE_AGE_MS) {
                    YawIntegrator.discardThrough(now)
                    return nose to false
                }
                val delta = YawIntegrator.consumeDeltaDeg()
                if (delta != 0f) {
                    applyYawDeltaToBearing(nose, delta) to true
                } else {
                    nose to false
                }
            }
            MockHeadingSource.STEER -> {
                YawIntegrator.discardThrough(now)
                // Speed samples already advanced the bicycle model via the speed
                // collector. Here only refresh the held v (no time) then flush the
                // remaining held-wheel interval up to the mock tick.
                SteerHeadingIntegrator.onSpeedKmh(signedSteerSpeedKmhNow(now))
                SteerHeadingIntegrator.tick(now)
                val delta = SteerHeadingIntegrator.consumeDeltaDeg()
                if (delta != 0f) {
                    applyYawDeltaToBearing(nose, delta) to true
                } else {
                    nose to false
                }
            }
        }
    }

    /**
     * Shared DR motion step: heading and distance share the same gate so crawl /
     * braking cannot advance path while discarding turn (or the reverse).
     * Path length is projected on the mid-course of the step when heading moved.
     */
    private fun applyDrMotionStep(
        noseIn: Float,
        reverse: Boolean,
        now: Long,
        canKmh: Float?,
        useCan: Boolean,
        speedKmh: Float,
        dtSec: Double,
    ): Pair<Float, Boolean> {
        val hasPendingDistance = SpeedIntegrator.pendingDistanceM() > 0.0
        val allowDr = dtSec > 0.0 &&
            (speedKmh >= COURSE_HOLD_MIN_KMH || hasPendingDistance)
        if (!allowDr) {
            applyHeadingDelta(noseIn, headingSource.value, allowIntegrate = false, now = now)
            refreshSpeedIntegratorWhileGated(now, canKmh.takeIf { useCan })
            return noseIn to false
        }
        val noseBefore = noseIn
        val (noseAfter, applied) = applyHeadingDelta(
            nose = noseIn,
            source = headingSource.value,
            allowIntegrate = true,
            now = now,
        )
        if (useCan) {
            val distanceM = takeDrDistanceM(now, canKmh, stepAllowed = true)
            if (distanceM > 0.0) {
                val stepNose = if (applied) {
                    averageBearingDeg(noseBefore, noseAfter)
                } else {
                    noseAfter
                }
                val travel = ConstantDrMath.travelBearingFromNoseHeading(stepNose, reverse)
                val stepped = extrapolateLatLon(retainLat, retainLon, travel, distanceM)
                retainLat = stepped.first
                retainLon = stepped.second
            }
        } else {
            refreshSpeedIntegratorWhileGated(now, null)
        }
        return noseAfter to applied
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

    private fun clearConstantOrigin() {
        constantHasOrigin = false
        constantMismatchStreak = 0
        hardResyncTrustSinceElapsedMs = 0L
        onlineYawCalib.reset()
        ConstantDrRuntimeDebug.clear()
        OnlineYawCalibRuntimeDebug.clear()
    }

    private fun restartInner() {
        val power = mockPower.value
        val enabled = shouldPushMock(power, locationSource.value)
        val period = periodMs.value.coerceAtLeast(200L)
        val storedMode = canSpeedMode.value
        val mode = power.effectiveCanSpeedMode(storedMode)
        val heading = headingSource.value
        val filterOn = junkFixFilterEnabled.value
        val autoCalib = constantAutoCalibEnabled.value
        val considerRev = considerReverseEnabled.value
        val sig =
            "$enabled:${power.name}:$period:${locationSource.value}:$mode:$heading:$filterOn:$autoCalib:$considerRev"

        if (sig == lastSig) {
            if (!enabled) return
            if (job?.isActive == true) return
        }

        val prevMode = lastMode
        val prevEnabled = lastEnabled
        lastMode = mode
        lastEnabled = enabled

        // Leaving CONSTANT, entering CONSTANT, or mock off→on: reseed shadow.
        if (prevMode == MockCanSpeedMode.CONSTANT && mode != MockCanSpeedMode.CONSTANT) {
            clearConstantOrigin()
        }
        if (mode == MockCanSpeedMode.CONSTANT &&
            prevMode != null &&
            prevMode != MockCanSpeedMode.CONSTANT
        ) {
            clearConstantOrigin()
        }
        if (prevEnabled == true && !enabled) {
            clearConstantOrigin()
        }
        if (prevEnabled == false && enabled && mode == MockCanSpeedMode.CONSTANT) {
            clearConstantOrigin()
        }

        lastSig = sig
        job?.cancel()
        job = null
        ensureGearInterest(enabled && mode.enhancesMock)
        ensureSteerInterest(enabled && mode.enhancesMock && heading == MockHeadingSource.STEER)
        if (!enabled || !mode.enhancesMock) {
            stopSpeedSampleCollection()
        } else {
            ensureSpeedSampleCollection()
        }
        if (!enabled) {
            val now = SystemClock.elapsedRealtime()
            YawIntegrator.discardThrough(now)
            SteerHeadingIntegrator.discardThrough(now)
            SpeedIntegrator.discardThrough(now)
            flushPersistedAsync()
            locationMockManager.stopMockLocation()
            return
        }
        job = scope.launch {
            while (isActive) {
                pushOnce(power, mode, filterOn)
                delay(period)
            }
        }
    }

    private fun pushOnce(power: MockPowerState, mode: MockCanSpeedMode, junkFilterOn: Boolean) {
        val now = SystemClock.elapsedRealtime()
        val live = TboxRepository.locValues.value
        val canKmh = TripTelemetryRepository.accountingCarSpeed(now)
        val lastLocAtMs = TboxRepository.locationUpdateTime.value?.time
        val gnssFresh = GnssFreshness.isFresh(lastLocAtMs, System.currentTimeMillis())
        val gnssTruthful = gnssFresh && isLiveUsable(live, junkFilterOn, canKmh, now)
        val injectToSystem = when (power) {
            MockPowerState.OFF -> false
            MockPowerState.ALWAYS_ON -> true
            MockPowerState.WHEN_NO_FIX -> !hasGnssFixForPowerGate(live, gnssFresh)
        }

        if (mode.isConstantCalc) {
            // Advanced: junk filter does not gate soft blend, but still defines truth /
            // hard-resync / auto-calib trust. Stale LocValues (USB unplug) must not blend.
            val gnssPresent = gnssFresh && live.locateStatus && hasValidCoordinates(live)
            if (gnssPresent) {
                lastGoodLoc = live
                lastGoodAtElapsedMs = now
                usingPersistedSeed = false
                persistLiveGood(live, now)
            }
            pushOnceConstant(
                live = live,
                gnssPresent = gnssPresent,
                gnssTruthful = gnssTruthful,
                canKmh = canKmh,
                reverse = shouldApplyReverse(mode, considerReverseEnabled.value),
                now = now,
                injectToSystem = injectToSystem,
            )
            return
        }

        ConstantDrRuntimeDebug.clear()
        val liveUsable = gnssTruthful

        // lastGood updates while usable; on fix loss / junk latch it freezes as the
        // retention anchor (last accepted good at the moment of invalidation).
        if (liveUsable) {
            lastGoodLoc = live
            lastGoodAtElapsedMs = now
            usingPersistedSeed = false
            if (shouldAcceptGnssCourse(canKmh, live.speed, live.trueDirection)) {
                lastKnownBearingDeg = if (mode.enhancesMock) {
                    ConstantDrMath.noseHeadingFromCourseOverGround(
                        live.trueDirection,
                        shouldApplyReverse(mode, considerReverseEnabled.value),
                    )
                } else {
                    live.trueDirection
                }
            }
            // Persist for cold start / junk hold even without enhance mode.
            persistLiveGood(live, now)
        }

        if (!mode.enhancesMock) {
            YawIntegrator.discardThrough(now)
            SteerHeadingIntegrator.discardThrough(now)
            SpeedIntegrator.discardThrough(now)
            onlineYawCalib.reset()
            OnlineYawCalibRuntimeDebug.clear()
            wasRetaining = false
            usingPersistedSeed = false
            lastPushElapsedMs = now
            if (liveUsable) {
                publishLivePassthrough(live, liveUsable = true, gnssTruthful = gnssTruthful)
            } else if (isJunkLive(live, junkFilterOn, liveUsable)) {
                val good = lastGoodLoc
                if (good != null && hasValidCoordinates(good)) {
                    publishStaticLastGood(good, liveUsable = false, gnssTruthful = gnssTruthful)
                } else {
                    // No last good yet — do not push junk into mock.
                    publishLostDisplay(liveUsable = false, live = live, gnssTruthful = gnssTruthful)
                }
            } else {
                // No enhance, not junk (e.g. no fix) — GNSS as-is.
                publishLivePassthrough(live, liveUsable = false, gnssTruthful = gnssTruthful)
            }
            return
        }

        // Enhancement modes: junk / no-fix → retention path (ignore live for mock out).
        val reverse = shouldApplyReverse(mode, considerReverseEnabled.value)

        // Refine yaw bias/scale while GNSS is good — same stores used later in retention DR.
        if (liveUsable) {
            maybeRunOnlineYawCalib(
                now = now,
                live = live,
                canKmh = canKmh,
                reverse = reverse,
                gnssTruthful = true,
            )
        } else {
            onlineYawCalib.reset()
            OnlineYawCalibRuntimeDebug.clear()
        }

        val retaining: Boolean
        val base: LocValues

        if (liveUsable) {
            base = live
            retaining = false
            wasRetaining = false
            retainLat = live.latitude
            retainLon = live.longitude
            if (shouldAcceptGnssCourse(canKmh, live.speed, live.trueDirection)) {
                lastKnownBearingDeg = ConstantDrMath.noseHeadingFromCourseOverGround(
                    live.trueDirection,
                    reverse,
                )
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
                        lastKnownBearingDeg = ConstantDrMath.noseHeadingFromCourseOverGround(
                            good.trueDirection,
                            reverse,
                        )
                    }
                }
            } else {
                publishLostDisplay(liveUsable = false, live = live, gnssTruthful = gnssTruthful)
                YawIntegrator.discardThrough(now)
                SteerHeadingIntegrator.discardThrough(now)
                refreshSpeedIntegratorWhileGated(now, canKmh)
                return
            }
        }
        if (mode == MockCanSpeedMode.WHEN_FIX_LOST && !retaining) {
            YawIntegrator.discardThrough(now)
            SteerHeadingIntegrator.discardThrough(now)
            refreshSpeedIntegratorWhileGated(now, canKmh)
            lastPushElapsedMs = now
            publishLiveWithHeldCourse(
                live = live,
                liveUsable = true,
                gnssTruthful = gnssTruthful,
                canKmh = canKmh,
            )
            return
        }

        val useCan = when (mode) {
            MockCanSpeedMode.ALWAYS -> canKmh != null
            MockCanSpeedMode.WHEN_FIX_LOST -> retaining && canKmh != null
            MockCanSpeedMode.NONE, MockCanSpeedMode.CONSTANT -> false
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

        var nose = lastKnownBearingDeg?.takeIf { it != 0f }
        var bearingSource = when {
            retaining -> GeoBearingSource.RETENTION
            nose != null -> GeoBearingSource.HELD
            else -> GeoBearingSource.HELD
        }
        if (!retaining && shouldAcceptGnssCourse(canKmh, base.speed, base.trueDirection)) {
            nose = ConstantDrMath.noseHeadingFromCourseOverGround(base.trueDirection, reverse)
            bearingSource = GeoBearingSource.GNSS
            lastKnownBearingDeg = nose
        }

        var lat = base.latitude
        var lon = base.longitude
        if (retaining) {
            val dtSec = if (lastPushElapsedMs > 0L) {
                ((now - lastPushElapsedMs).coerceAtLeast(0L) / 1000.0)
            } else {
                0.0
            }
            if (nose != null) {
                val (nextNose, applied) = applyDrMotionStep(
                    noseIn = nose,
                    reverse = reverse,
                    now = now,
                    canKmh = canKmh,
                    useCan = useCan,
                    speedKmh = speedKmh,
                    dtSec = dtSec,
                )
                nose = nextNose
                if (applied) {
                    lastKnownBearingDeg = nose
                    bearingSource = GeoBearingSource.RETENTION
                }
            } else {
                applyHeadingDelta(0f, headingSource.value, allowIntegrate = false, now = now)
                refreshSpeedIntegratorWhileGated(now, canKmh.takeIf { useCan })
            }
            lat = retainLat
            lon = retainLon
        } else {
            // ALWAYS while live: GNSS course; retire pending heading/speed so they do not dump on fix loss.
            applyHeadingDelta(nose ?: 0f, headingSource.value, allowIntegrate = false, now = now)
            refreshSpeedIntegratorWhileGated(now, canKmh)
        }
        lastPushElapsedMs = now
        val outBearing = nose?.let { ConstantDrMath.travelBearingFromNoseHeading(it, reverse) }
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
                gnssTruthful = gnssTruthful,
            ),
        )
    }

    /**
     * CONSTANT (Advanced): continuous shadow DR by CAN + yaw; soft GNSS blend every tick;
     * optional reverse invert of travel bearing; junk filter bypassed for soft blend
     * but used for [gnssTruthful], hard resync, and auto-calib.
     */
    private fun pushOnceConstant(
        live: LocValues,
        gnssPresent: Boolean,
        gnssTruthful: Boolean,
        canKmh: Float?,
        reverse: Boolean,
        now: Long,
        injectToSystem: Boolean = true,
    ) {
        if (!constantHasOrigin) {
            if (gnssPresent) {
                retainLat = live.latitude
                retainLon = live.longitude
                constantAlt = live.altitude
                constantVisibleSats = live.visibleSatellites
                constantUsingSats = live.usingSatellites
                constantHasOrigin = true
                wasRetaining = true
                if (shouldAcceptGnssCourse(canKmh, live.speed, live.trueDirection)) {
                    lastKnownBearingDeg = ConstantDrMath.noseHeadingFromCourseOverGround(
                        live.trueDirection,
                        reverse,
                    )
                }
            } else {
                if (lastGoodLoc == null) {
                    trySeedFromPersisted(MockCanSpeedMode.CONSTANT, now)
                }
                val good = lastGoodLoc
                if (good != null && hasValidCoordinates(good)) {
                    retainLat = good.latitude
                    retainLon = good.longitude
                    constantAlt = good.altitude
                    constantVisibleSats = good.visibleSatellites
                    constantUsingSats = good.usingSatellites
                    constantHasOrigin = true
                    wasRetaining = true
                    if (shouldAcceptGnssCourse(good.speed, good.trueDirection) ||
                        (good.trueDirection != 0f && lastKnownBearingDeg == null)
                    ) {
                        lastKnownBearingDeg = good.trueDirection
                    }
                } else {
                    ConstantDrRuntimeDebug.publish(
                        ConstantDrRuntimeDebug.Snapshot(
                            active = true,
                            constantHasOrigin = false,
                        ),
                    )
                    YawIntegrator.discardThrough(now)
                    SteerHeadingIntegrator.discardThrough(now)
                    refreshSpeedIntegratorWhileGated(now, canKmh)
                    publishLostDisplay(liveUsable = false, live = live, gnssTruthful = gnssTruthful)
                    lastPushElapsedMs = now
                    return
                }
            }
        }

        val useCan = canKmh != null
        val speedKmh = if (useCan) {
            DriveCalibrationStore.applyCanSpeed(canKmh!!)
        } else {
            0f
        }
        val speedSource = if (useCan) GeoSpeedSource.CAN else GeoSpeedSource.RETENTION
        val speedMps = speedKmh / 3.6f

        var nose = lastKnownBearingDeg?.takeIf { it != 0f }
        var bearingSource = GeoBearingSource.RETENTION

        val dtSec = if (lastPushElapsedMs > 0L) {
            ((now - lastPushElapsedMs).coerceAtLeast(0L) / 1000.0)
        } else {
            0.0
        }
        // Heading + distance share one gate (≥ COURSE_HOLD_MIN_KMH or braking tail).
        if (nose != null) {
            val (nextNose, applied) = applyDrMotionStep(
                noseIn = nose,
                reverse = reverse,
                now = now,
                canKmh = canKmh,
                useCan = useCan,
                speedKmh = speedKmh,
                dtSec = dtSec,
            )
            nose = nextNose
            if (applied) {
                lastKnownBearingDeg = nose
                bearingSource = GeoBearingSource.RETENTION
            }
        } else {
            applyHeadingDelta(0f, headingSource.value, allowIntegrate = false, now = now)
            refreshSpeedIntegratorWhileGated(now, canKmh.takeIf { useCan })
        }

        var effectivePosWeight = 0f
        var shadowDistM: Double? = null
        var thresholdMOut: Double? = null
        var accuracyMOut: Float? = null
        var didHardResync = false

        if (gnssPresent) {
            val accuracyM = LocationMockManager.horizontalAccuracyMeters(
                hdop = live.hdop,
                retainingFix = false,
                hrms = live.hrms,
            )
            accuracyMOut = accuracyM
            val confidence = ConstantDrMath.confidenceFromAccuracyM(accuracyM)
            val dist = ConstantDrMath.distanceMeters(
                retainLat,
                retainLon,
                live.latitude,
                live.longitude,
            )
            shadowDistM = dist
            val speedForMismatch = speedKmh.takeIf { it > 0f } ?: (canKmh ?: live.speed)
            val thresholdM = ConstantDrMath.mismatchThresholdM(
                speedKmh = speedForMismatch,
                intervalSec = dtSec.coerceAtLeast(ConstantDrMath.BLEND_INTERVAL_SEC),
                horizontalAccuracyM = accuracyM,
            )
            thresholdMOut = thresholdM

            // Hard resync: far shadow + continuous trusted GNSS → snap to GNSS.
            val trustCandidate = gnssTruthful &&
                ConstantDrMath.gnssSpeedAgreesForHardResync(live.speed, canKmh)
            if (trustCandidate) {
                if (hardResyncTrustSinceElapsedMs == 0L) {
                    hardResyncTrustSinceElapsedMs = now
                }
            } else {
                hardResyncTrustSinceElapsedMs = 0L
            }
            val trustHeld = hardResyncTrustSinceElapsedMs > 0L &&
                (now - hardResyncTrustSinceElapsedMs) >= ConstantDrMath.HARD_RESYNC_TRUST_MS

            if (ConstantDrMath.shouldHardResync(dist, thresholdM) && trustHeld) {
                retainLat = live.latitude
                retainLon = live.longitude
                constantAlt = live.altitude
                constantVisibleSats = live.visibleSatellites
                constantUsingSats = live.usingSatellites
                if (shouldAcceptGnssCourse(canKmh, live.speed, live.trueDirection)) {
                    nose = ConstantDrMath.noseHeadingFromCourseOverGround(
                        live.trueDirection,
                        reverse,
                    )
                    lastKnownBearingDeg = nose
                    bearingSource = GeoBearingSource.GNSS
                }
                effectivePosWeight = 1f
                constantMismatchStreak = 0
                hardResyncTrustSinceElapsedMs = 0L
                didHardResync = true
            } else {
                val mScale = ConstantDrMath.mismatchScale(dist, thresholdM)
                val posW = ConstantDrMath.positionWeightFromConfidence(confidence) * mScale
                effectivePosWeight = posW

                if (posW > 0f) {
                    val blended = ConstantDrMath.blendLatLon(
                        retainLat,
                        retainLon,
                        live.latitude,
                        live.longitude,
                        posW,
                    )
                    retainLat = blended.first
                    retainLon = blended.second
                    constantAlt = live.altitude
                    constantVisibleSats = live.visibleSatellites
                    constantUsingSats = live.usingSatellites
                }

                val gnssNose = if (live.trueDirection != 0f && live.trueDirection.isFinite()) {
                    ConstantDrMath.noseHeadingFromCourseOverGround(live.trueDirection, reverse)
                } else {
                    null
                }
                if (gnssNose != null && nose != null) {
                    val residual = kotlin.math.abs(
                        DriveCalibrationMath.wrapDeltaDeg(nose, gnssNose),
                    )
                    val courseW = ConstantDrMath.courseWeightFromConfidence(confidence, residual) *
                        mScale *
                        ConstantDrMath.speedScaleForGnssCourse(speedMps)
                    if (courseW > 0f) {
                        nose = ConstantDrMath.blendBearingDeg(nose, gnssNose, courseW)
                        lastKnownBearingDeg = nose
                        bearingSource = GeoBearingSource.GNSS
                    }
                } else if (gnssNose != null && nose == null &&
                    shouldAcceptGnssCourse(canKmh, live.speed, live.trueDirection)
                ) {
                    nose = gnssNose
                    lastKnownBearingDeg = nose
                    bearingSource = GeoBearingSource.GNSS
                }
            }

            // Optional auto-calib: only when toggle is on and GNSS is trustworthy.
            if (constantAutoCalibEnabled.value) {
                val driveCalibAt = DriveCalibrationStore.offsets.calibratedAtEpochMs
                if (driveCalibAt > 0L && driveCalibAt != lastCalibSeenAtEpochMs) {
                    lastCalibSeenAtEpochMs = driveCalibAt
                    constantMismatchStreak = 0
                }
                if (gnssTruthful) {
                    val distLarge = ConstantDrMath.isLargeMismatch(dist, thresholdM)
                    if (ConstantDrMath.shouldCountMismatch(speedForMismatch)) {
                        constantMismatchStreak =
                            ConstantDrMath.nextMismatchStreak(constantMismatchStreak, distLarge)
                        val required = ConstantDrMath.requiredMismatchStreak(
                            nowEpochMs = System.currentTimeMillis(),
                            lastCalibratedAtEpochMs = driveCalibAt,
                        )
                        if (ConstantDrMath.shouldRequestCalibration(
                                constantMismatchStreak,
                                required,
                            )
                        ) {
                            onConstantMismatchNeedsCalib()
                            constantMismatchStreak = 0
                        }
                    } else if (!distLarge) {
                        constantMismatchStreak = 0
                    }
                }
            } else {
                constantMismatchStreak = 0
            }

            // Online yaw bias (straights) / scale (turns) from truthful GNSS — no ay/v.
            maybeRunOnlineYawCalib(
                now = now,
                live = live,
                canKmh = canKmh,
                reverse = reverse,
                gnssTruthful = gnssTruthful,
            )
        } else {
            hardResyncTrustSinceElapsedMs = 0L
            onlineYawCalib.reset()
            OnlineYawCalibRuntimeDebug.clear()
        }

        val calibAt = GeoCalibrationState.lastCalibratedAtEpochMs.value
        if (calibAt > 0L && calibAt != lastCalibSeenAtEpochMs) {
            lastCalibSeenAtEpochMs = calibAt
            constantMismatchStreak = 0
        } else if (lastCalibSeenAtEpochMs == 0L && calibAt > 0L) {
            lastCalibSeenAtEpochMs = calibAt
        }

        lastPushElapsedMs = now
        val outBearing = nose?.let { ConstantDrMath.travelBearingFromNoseHeading(it, reverse) }
        // Green when GNSS contributes (soft blend or hard resync); blue when shadow alone.
        val liveUsableOut = gnssPresent && effectivePosWeight > 0.05f
        val retainingOut = !liveUsableOut
        ConstantDrRuntimeDebug.publish(
            ConstantDrRuntimeDebug.Snapshot(
                active = true,
                shadowDistM = shadowDistM,
                thresholdM = thresholdMOut,
                posW = effectivePosWeight,
                constantHasOrigin = constantHasOrigin,
                blendLive = liveUsableOut,
                hardResync = didHardResync,
                accuracyM = accuracyMOut,
            ),
        )
        val out = LocValues(
            locateStatus = true,
            latitude = retainLat,
            longitude = retainLon,
            altitude = constantAlt,
            speed = speedKmh,
            trueDirection = outBearing ?: 0f,
            visibleSatellites = constantVisibleSats,
            usingSatellites = constantUsingSats,
        )
        if (injectToSystem) {
            locationMockManager.setMockLocation(
                locValues = out,
                retainingFix = retainingOut,
                hasReliableSpeed = true,
                hasReliableBearing = outBearing != null,
            )
        } else {
            // WHEN_NO_FIX with live GNSS: keep shadow warm but do not spoof Android.
            locationMockManager.stopMockLocation()
        }
        GeoDisplayRepository.publish(
            GeoDisplayState(
                liveUsable = liveUsableOut,
                retaining = retainingOut,
                locateStatus = true,
                latitude = retainLat,
                longitude = retainLon,
                altitude = constantAlt,
                speedKmh = speedKmh,
                speedSource = speedSource,
                bearingDeg = outBearing,
                bearingSource = bearingSource,
                hasReliableBearing = outBearing != null,
                visibleSatellites = constantVisibleSats,
                usingSatellites = constantUsingSats,
                mockActive = true,
                gnssTruthful = gnssTruthful,
            ),
        )
    }

    /** Push live GNSS without CAN / retention / heading-hold / DR. */
    private fun publishLivePassthrough(
        live: LocValues,
        liveUsable: Boolean,
        gnssTruthful: Boolean,
    ) {
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
                gnssTruthful = gnssTruthful,
            ),
        )
    }

    /**
     * WHEN_FIX_LOST while live: GNSS position/speed/sats as-is; course held on standstill
     * so mock / nav do not spin with noisy COG. Direct ([publishLivePassthrough]) stays raw.
     */
    private fun publishLiveWithHeldCourse(
        live: LocValues,
        liveUsable: Boolean,
        gnssTruthful: Boolean,
        canKmh: Float?,
    ) {
        val accepted = shouldAcceptGnssCourse(canKmh, live.speed, live.trueDirection)
        if (accepted) {
            lastKnownBearingDeg = live.trueDirection
        }
        val bearing = lastKnownBearingDeg?.takeIf { it != 0f }
        val bearingSource = when {
            accepted -> GeoBearingSource.GNSS
            bearing != null -> GeoBearingSource.HELD
            else -> GeoBearingSource.HELD
        }
        val out = live.copy(trueDirection = bearing ?: 0f)
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
                locateStatus = live.locateStatus,
                latitude = live.latitude,
                longitude = live.longitude,
                altitude = live.altitude,
                speedKmh = live.speed,
                speedSource = GeoSpeedSource.GNSS,
                bearingDeg = bearing,
                bearingSource = bearingSource,
                hasReliableBearing = bearing != null,
                visibleSatellites = live.visibleSatellites,
                usingSatellites = live.usingSatellites,
                mockActive = true,
                gnssTruthful = gnssTruthful,
            ),
        )
    }

    /** Hold last good in mock without DR (junk rejected while enhance mode is off). */
    private fun publishStaticLastGood(
        good: LocValues,
        liveUsable: Boolean,
        gnssTruthful: Boolean,
    ) {
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
                gnssTruthful = gnssTruthful,
            ),
        )
    }

    private fun publishLostDisplay(
        liveUsable: Boolean,
        live: LocValues,
        gnssTruthful: Boolean,
    ) {
        // No fix + no coords + not retaining → red NONE (all modes except CONSTANT shadow).
        val noFixNoCoords = !live.locateStatus && !hasValidCoordinates(live)
        GeoDisplayRepository.publish(
            GeoDisplayState(
                liveUsable = liveUsable,
                retaining = false,
                locateStatus = if (noFixNoCoords) false else live.locateStatus,
                latitude = if (noFixNoCoords) 0.0 else live.latitude,
                longitude = if (noFixNoCoords) 0.0 else live.longitude,
                altitude = live.altitude,
                speedKmh = live.speed,
                speedSource = GeoSpeedSource.GNSS,
                bearingDeg = lastKnownBearingDeg,
                bearingSource = GeoBearingSource.HELD,
                hasReliableBearing = lastKnownBearingDeg != null,
                visibleSatellites = live.visibleSatellites,
                usingSatellites = live.usingSatellites,
                mockActive = true,
                gnssTruthful = gnssTruthful,
            ),
        )
    }

    /**
     * Continuous yaw bias (straights) and scale (turns) while GNSS is truthful.
     * Used by CONSTANT and by ALWAYS / WHEN_FIX_LOST while live.
     * Updates in-memory stores immediately; persists via debounced callbacks.
     */
    private fun maybeRunOnlineYawCalib(
        now: Long,
        live: LocValues,
        canKmh: Float?,
        reverse: Boolean,
        gnssTruthful: Boolean,
    ) {
        // Online yaw calib only applies when gyro is the heading source.
        if (headingSource.value != MockHeadingSource.GYRO) {
            onlineYawCalib.reset()
            OnlineYawCalibRuntimeDebug.clear()
            return
        }
        val accuracyM = LocationMockManager.horizontalAccuracyMeters(
            hdop = live.hdop,
            retainingFix = false,
            hrms = live.hrms,
        )
        val speedKmh = when {
            canKmh != null -> DriveCalibrationStore.applyCanSpeed(canKmh)
            else -> live.speed
        }
        runOnlineYawCalib(
            now = now,
            live = live,
            speedKmh = speedKmh,
            accuracyM = accuracyM,
            reverse = reverse,
            gnssTruthful = gnssTruthful,
        )
    }

    private fun runOnlineYawCalib(
        now: Long,
        live: LocValues,
        speedKmh: Float,
        accuracyM: Float?,
        reverse: Boolean,
        gnssTruthful: Boolean,
    ) {
        val rawYaw = vad.dashing.tbox.drsensor.DrSensorRepository.snapshot.value.gyroYaw
        val gyroTemp = vad.dashing.tbox.drsensor.DrSensorRepository.snapshot.value.gyroTemp
        val gnssNose = if (live.trueDirection != 0f && live.trueDirection.isFinite()) {
            ConstantDrMath.noseHeadingFromCourseOverGround(live.trueDirection, reverse)
        } else {
            null
        }
        val result = onlineYawCalib.onTick(
            elapsedMs = now,
            rawYawDegPerSec = rawYaw,
            gnssNoseCourseDeg = gnssNose,
            speedKmh = speedKmh,
            accuracyM = accuracyM,
            reverse = reverse,
            gnssTruthful = gnssTruthful,
            gyroTempC = gyroTemp,
        )
        OnlineYawCalibRuntimeDebug.publish(result.debug)
        if (result.persistBias) {
            onOnlineGyroBiasPersist(GyroBiasStore.offsets)
        }
        if (result.persistScale) {
            onOnlineDriveCalibPersist(DriveCalibrationStore.offsets)
        }
    }
}
