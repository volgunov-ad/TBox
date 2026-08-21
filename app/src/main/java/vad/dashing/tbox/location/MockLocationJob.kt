package vad.dashing.tbox.location

import android.os.SystemClock
import android.util.Log
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
import vad.dashing.tbox.location.roadmatch.RoadGraphStore
import vad.dashing.tbox.location.roadmatch.RoadMatchController
import vad.dashing.tbox.location.roadmatch.RoadMatchDemand
import vad.dashing.tbox.location.roadmatch.RoadMatchGnssTrust
import vad.dashing.tbox.location.roadmatch.RoadMatchManualSeedRepository
import vad.dashing.tbox.location.roadmatch.RoadMatchOverlayBuilder
import vad.dashing.tbox.location.roadmatch.RoadMatchOverlayPublisher
import vad.dashing.tbox.location.roadmatch.RoadMatchOverlayRepository
import vad.dashing.tbox.location.roadmatch.RoadMatchPose
import vad.dashing.tbox.location.roadmatch.RoadMatchRuntimeDebug
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
 * when the junk filter is on, junk GNSS is not blended and not stored as last-good.
 *
 * DR path length uses wheel pulse (`WheelPulseOdometer.flushDrDistanceM`) when that
 * toggle is on, otherwise [SpeedIntegrator] (trapezoid over accounting-speed samples
 * between DR ticks) instead of a single `v_end · Δt`. Heading uses gyro or
 * steering via [applyHeadingDelta] / [SteerHeadingIntegrator].
 * Pose + road-match advance on [INNER_CALC_MS]; system mock inject uses [periodMs].
 *
 * Optional [junkFixFilterEnabled] (default on): feeds [isLiveUsable] / truth in every
 * mode, and in CONSTANT also gates soft blend / origin / last-good.
 * Cold-start disk seed when enhancement / CONSTANT is on.
 * Reverse gear and turn signals are subscribed while enhancement (incl. CONSTANT) is active.
 * When [considerReverseEnabled] is on, reverse (HU PRND → switch → TBox) inverts travel
 * bearing in all enhancement modes; Direct ([MockCanSpeedMode.NONE]) never uses reverse.
 *
 * Online yaw L/R scale ([OnlineYawCalibEstimator]) runs in all enhancement modes while
 * GNSS is truthful and [onlineYawCalibEnabled] is on (not in Direct). Off by default.
 * Straight bias EMA is disabled; idle yaw-zero is [ConstantDrAutoCalibJob] + its own toggle.
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
    private val onlineYawCalibEnabled: StateFlow<Boolean> = kotlinx.coroutines.flow.MutableStateFlow(false),
    private val considerReverseEnabled: StateFlow<Boolean> = kotlinx.coroutines.flow.MutableStateFlow(true),
    private val roadMatchDemand: StateFlow<RoadMatchDemand> =
        kotlinx.coroutines.flow.MutableStateFlow(RoadMatchDemand.NONE),
    private val roadMatchTuning:
        StateFlow<vad.dashing.tbox.location.roadmatch.RoadMatchTuning> =
        kotlinx.coroutines.flow.MutableStateFlow(
            vad.dashing.tbox.location.roadmatch.RoadMatchTuning.DEFAULT,
        ),
    /** Process-wide matcher from [vad.dashing.tbox.BackgroundService]; fallback is local. */
    private val roadMatch: RoadMatchController? = null,
    private val roadMapsDir: () -> java.io.File = { java.io.File(".") },
    private val loadPersistedLastGood: suspend () -> MockLastGoodFix?,
    private val savePersistedLastGood: suspend (MockLastGoodFix) -> Unit,
    private val onConstantMismatchNeedsCalib: () -> Unit = {},
    /** Debounced persist of online yaw bias (legacy / unused while straight bias is off). */
    private val onOnlineGyroBiasPersist: (GyroBiasOffsets) -> Unit = {},
    /** Debounced persist of online yaw scale (enhancement modes). */
    private val onOnlineDriveCalibPersist: (DriveCalibrationOffsets) -> Unit = {},
) {
    companion object {
        private const val TAG = "MockLocationJob"
        /** Keep last valid coordinates in mock after fix loss (10 minutes). */
        const val FIX_RETENTION_MS = 600_000L

        /**
         * Shadow DR + road-match cadence, independent of the user inject period
         * (0.5 / 1 / 2 / 5 s). Speed/yaw/steer still accumulate between these ticks.
         */
        const val INNER_CALC_MS = 500L

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
         * Below this speed (km/h), ignore GNSS course updates.
         * ~0.5 m/s — same ballpark as HWGPS motion gate.
         * DR path/heading use [classifyDrMotion]: crawl with real metres still steps.
         */
        const val COURSE_HOLD_MIN_KMH = 1.8f

        /**
         * Below this, treat the car as stopped for DR (unless a braking-tail
         * pending path remains). Aligned with [SteerHeadingIntegrator.MIN_SPEED_MPS].
         */
        const val CRAWL_DR_MIN_KMH = SteerHeadingIntegrator.MIN_SPEED_MPS * 3.6f

        /**
         * Minimum pending path (m) before a crawl-speed DR step. Filters a single
         * CAN idle blip; ~1.5 s at 1 km/h. Gyro/steer stay pending until then.
         */
        const val CRAWL_DR_MIN_DISTANCE_M = 0.40

        /** After bias, |yaw| below this (°/s) is treated as zero for DR. */
        const val YAW_DEADBAND_DEG_PER_SEC = YawIntegrator.YAW_DEADBAND_DEG_PER_SEC

        /** |gyro| below this (°) is quiet — hybrid may take the full steer delta. */
        const val HYBRID_GYRO_QUIET_DEG = 0.15f

        /** |steer| above this (°) counts as a clear turn when gyro is quiet. */
        const val HYBRID_STEER_CLEAR_DEG = 0.3f

        /**
         * Same-sign steer must exceed |gyro| by this (°) before catch-up starts.
         * Filters noise; a 0.5 s inner tick with ~3°/s steer-lead still qualifies.
         */
        const val HYBRID_STEER_LEAD_MIN_DEG = 1.0f

        /** Fraction of (steer − gyro) extra applied when steer leads. Never past steer. */
        const val HYBRID_STEER_CATCHUP_BLEND = 0.6f

        /** Cap on extra degrees toward steer per DR step (inner [INNER_CALC_MS]). */
        const val HYBRID_STEER_CATCHUP_MAX_DEG = 4f

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
         * Whether [MockPowerState.WHEN_NO_FIX] should inject mock into the system.
         * Inject while GNSS is not truthful (no/stale fix, or junk when the filter is on).
         * Mock provider is removed only when [gnssTruthful] is true.
         */
        fun shouldInjectWhenNoFix(gnssTruthful: Boolean): Boolean = !gnssTruthful

        /**
         * Whether this inner DR tick should also write the mock provider.
         * First tick always injects; later ticks wait for [periodMs].
         */
        fun isInjectDue(nowElapsedMs: Long, lastInjectElapsedMs: Long, periodMs: Long): Boolean {
            if (lastInjectElapsedMs <= 0L) return true
            val period = periodMs.coerceAtLeast(INNER_CALC_MS)
            return nowElapsedMs - lastInjectElapsedMs >= period
        }

        /**
         * GNSS has a fresh locate+coords fix (presence only; WHEN_NO_FIX injection uses
         * [shouldInjectWhenNoFix] with the truthful flag).
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

        fun roadMatchTurnHint(): vad.dashing.tbox.location.roadmatch.RoadMapMatcher.TurnHint? {
            val intent = vad.dashing.tbox.mbcan.UniversalCanRepository.turnSignalIntentSnapshot()
            val side = intent.side
                ?: vad.dashing.tbox.mbcan.UniversalCanRepository.latchedTurnSignalSide()
            return when (side) {
                vad.dashing.tbox.mbcan.TurnSignalSide.Left ->
                    vad.dashing.tbox.location.roadmatch.RoadMapMatcher.TurnHint.Left
                vad.dashing.tbox.mbcan.TurnSignalSide.Right ->
                    vad.dashing.tbox.location.roadmatch.RoadMapMatcher.TurnHint.Right
                else -> null
            }
        }

        fun roadMatchTurnIntent(): Boolean =
            vad.dashing.tbox.mbcan.UniversalCanRepository.turnSignalIntentSnapshot().intentional

        fun roadMatchTurnFlashCount(): Int =
            vad.dashing.tbox.mbcan.UniversalCanRepository.turnSignalIntentSnapshot().flashCount

        fun applyTurnSignalLatchTuning(
            tuning: vad.dashing.tbox.location.roadmatch.RoadMatchTuning,
        ) {
            vad.dashing.tbox.mbcan.UniversalCanRepository.configureTurnSignalLatch(
                holdMs = tuning.long(
                    vad.dashing.tbox.location.roadmatch.RoadMatchTuningKey.TS_LATCH_HOLD_MS,
                ),
                minFlashesForIntent = tuning.int(
                    vad.dashing.tbox.location.roadmatch.RoadMatchTuningKey.TS_MIN_FLASHES_FOR_INTENT,
                ),
                continuousStalkMs = tuning.long(
                    vad.dashing.tbox.location.roadmatch.RoadMatchTuningKey.TS_CONTINUOUS_STALK_MS,
                ),
            )
        }

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
        /**
         * Whether CONSTANT may treat this tick's GNSS as present for origin, last-good,
         * and soft blend. With the junk filter on, only a truthful fix is used.
         */
        fun constantAcceptsLiveGnss(
            junkFilterOn: Boolean,
            gnssTruthful: Boolean,
            gnssFixPresent: Boolean,
        ): Boolean = if (junkFilterOn) gnssTruthful else gnssFixPresent

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
         * Prefer live [currentBearingDeg] when it is a usable GNSS course (non-zero).
         * Otherwise keep [lastKnownBearingDeg], including **0° = true north** once held —
         * only `null` means “no heading”. Raw NMEA often sends 0 when course is unknown.
         */
        fun resolveBearingForExtrapolation(
            currentBearingDeg: Float,
            lastKnownBearingDeg: Float?,
        ): Float? {
            if (currentBearingDeg != 0f && currentBearingDeg.isFinite()) return currentBearingDeg
            val last = lastKnownBearingDeg ?: return null
            return if (last.isFinite()) last else null
        }

        /**
         * Accept raw GNSS course only when moving and course is non-zero.
         * (NMEA 0° usually means “no COG”, not north — do not seed held heading from it.)
         */
        fun shouldAcceptGnssCourse(speedKmh: Float, courseDeg: Float): Boolean =
            speedKmh >= COURSE_HOLD_MIN_KMH && courseDeg != 0f && courseDeg.isFinite()

        /**
         * Do not feed the matcher a held / disk heading while live GNSS has no
         * course (NMEA 0). Field `132038`: parked course=0, disk bearing 70°
         * ranked the interchange ramp as already 75 m along.
         *
         * While parked (below [COURSE_HOLD_MIN_KMH]) a pose is still fed so the
         * road-match map can seed edges and draw neighbors without waiting for DR.
         */
        fun shouldFeedHeadingToMatcher(
            gnssPresent: Boolean,
            gnssCourseDeg: Float,
            speedKmh: Float,
        ): Boolean {
            if (!gnssPresent) return true
            if (speedKmh < COURSE_HOLD_MIN_KMH) return true
            return gnssCourseDeg != 0f && gnssCourseDeg.isFinite()
        }

        internal fun buildConstantMatchPose(
            lat: Double,
            lon: Double,
            travelBearingDeg: Float?,
            gnssPresent: Boolean,
            gnssCourseDeg: Float,
            speedKmh: Float,
        ): RoadMatchPose? {
            if (!lat.isFinite() || !lon.isFinite()) return null
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
            if (lat == 0.0 && lon == 0.0) return null
            val bearing = travelBearingDeg ?: return null
            if (!bearing.isFinite()) return null
            if (!shouldFeedHeadingToMatcher(gnssPresent, gnssCourseDeg, speedKmh)) {
                return null
            }
            return RoadMatchPose(lat, lon, bearing)
        }

        /**
         * Live GNSS trust for road-match class-penalty relaxation.
         * [shadowLat]/[shadowLon] optional; when set, a large shadow↔GNSS gap zeros trust.
         */
        fun roadMatchGnssPositionTrust(
            liveGnss: Boolean,
            live: LocValues,
            shadowLat: Double? = null,
            shadowLon: Double? = null,
            tuning: vad.dashing.tbox.location.roadmatch.RoadMatchTuning =
                vad.dashing.tbox.location.roadmatch.RoadMatchTuning.DEFAULT,
        ): Float {
            if (!liveGnss || !hasValidCoordinates(live)) return 0f
            val accuracyM = LocationMockManager.liveHorizontalAccuracyMeters(
                hdop = live.hdop,
                hrms = live.hrms,
            )
            val gapM = if (
                shadowLat != null && shadowLon != null &&
                shadowLat.isFinite() && shadowLon.isFinite()
            ) {
                ConstantDrMath.distanceMeters(
                    shadowLat,
                    shadowLon,
                    live.latitude,
                    live.longitude,
                )
            } else {
                null
            }
            return RoadMatchGnssTrust.fromLive(
                liveGnss = true,
                accuracyM = accuracyM,
                shadowGnssGapM = gapM,
                maxAccuracyM = tuning.float(
                    vad.dashing.tbox.location.roadmatch.RoadMatchTuningKey.GNSS_MAX_ACCURACY_M,
                ),
                maxShadowGapM = tuning[
                    vad.dashing.tbox.location.roadmatch.RoadMatchTuningKey.GNSS_MAX_SHADOW_GAP_M
                ],
            )
        }

        /**
         * Whether this DR tick may move and turn, hold crawl integrators, or discard.
         * GNSS course stays on [COURSE_HOLD_MIN_KMH]; this gate is path/heading only.
         */
        fun classifyDrMotion(
            speedKmh: Float,
            pendingDistanceM: Double,
            dtSec: Double,
        ): DrMotionGate {
            if (dtSec <= 0.0) return DrMotionGate.DISCARD
            val speed = if (speedKmh.isFinite() && speedKmh > 0f) speedKmh else 0f
            val pending =
                if (pendingDistanceM.isFinite() && pendingDistanceM > 0.0) pendingDistanceM else 0.0
            return when {
                speed >= COURSE_HOLD_MIN_KMH -> DrMotionGate.STEP
                speed >= CRAWL_DR_MIN_KMH && pending >= CRAWL_DR_MIN_DISTANCE_M ->
                    DrMotionGate.STEP
                speed >= CRAWL_DR_MIN_KMH -> DrMotionGate.HOLD_CRAWL
                pending > 0.0 -> DrMotionGate.STEP
                else -> DrMotionGate.DISCARD
            }
        }

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

        /** Always `visible/using`, even when the two counts are equal. */
        fun formatSatellites(visible: Int, using: Int): String = "$visible/$using"

        fun wrapBearingDeg(bearingDeg: Float): Float {
            var b = bearingDeg % 360f
            if (b < 0f) b += 360f
            return b
        }

        /**
         * A road vehicle cannot change heading without travelling. Limit an observed
         * gyro / hybrid / steer heading change by the tightest turn physically possible
         * for [distanceM] using the bicycle model. Zero distance always freezes heading.
         */
        fun constrainHeadingToTravel(
            bearingBeforeDeg: Float,
            proposedBearingDeg: Float,
            distanceM: Double,
            wheelbaseM: Float = SteerHeadingIntegrator.DEFAULT_WHEELBASE_M,
        ): Float {
            if (!bearingBeforeDeg.isFinite() || !proposedBearingDeg.isFinite()) {
                return bearingBeforeDeg
            }
            if (!distanceM.isFinite() || distanceM <= 0.0) return bearingBeforeDeg
            val wheelbase = SteerHeadingIntegrator.resolveWheelbaseM(wheelbaseM).toDouble()
            val maxRoadWheelRad = Math.toRadians(
                SteerHeadingIntegrator.MAX_ROAD_WHEEL_DEG.toDouble(),
            )
            val maxDeltaDeg = Math.toDegrees(
                distanceM / wheelbase * kotlin.math.tan(maxRoadWheelRad),
            ).toFloat()
            var delta = (proposedBearingDeg - bearingBeforeDeg) % 360f
            if (delta > 180f) delta -= 360f
            if (delta < -180f) delta += 360f
            return wrapBearingDeg(
                bearingBeforeDeg + delta.coerceIn(-maxDeltaDeg, maxDeltaDeg),
            )
        }

        /**
         * Gyro+steer heading delta for one DR step that already has travel.
         * Gyro is primary; steer fills a quiet/stale gyro; same-sign steer that
         * leads gyro pulls toward steer (not a replace, not a sum). Opposite
         * signs keep gyro. Standstill never reaches this — [applyDrMotionStep]
         * discards both integrators when there is no path.
         */
        fun hybridGyroSteerDelta(gyroDelta: Float, steerDelta: Float): Float {
            val g = if (gyroDelta.isFinite()) gyroDelta else 0f
            val s = if (steerDelta.isFinite()) steerDelta else 0f
            if (g == 0f && s == 0f) return 0f
            if (g == 0f) return s
            if (s == 0f) return g
            if (g * s <= 0f) return g
            val absG = kotlin.math.abs(g)
            val absS = kotlin.math.abs(s)
            if (absG < HYBRID_GYRO_QUIET_DEG && absS > HYBRID_STEER_CLEAR_DEG) return s
            val lead = absS - absG
            if (lead < HYBRID_STEER_LEAD_MIN_DEG) return g
            val extra = (lead * HYBRID_STEER_CATCHUP_BLEND)
                .coerceAtMost(HYBRID_STEER_CATCHUP_MAX_DEG)
            return g + kotlin.math.sign(g) * extra
        }

        /**
         * After a hard position snap to recovered GNSS, allow a short GNSS-course
         * catch-up window even if this tick integrated little/no path.
         */
        const val HARD_RESYNC_COURSE_CATCHUP_MS = 5_000L

        /**
         * GNSS may correct an existing heading only on a tick with real DR travel,
         * unless [allowWithoutTravel] (hard-resync / far-shadow recovery).
         */
        fun gnssCourseScaleForTravel(
            speedMps: Float,
            distanceM: Double,
            allowWithoutTravel: Boolean = false,
        ): Float {
            if (!allowWithoutTravel && (!distanceM.isFinite() || distanceM <= 0.0)) return 0f
            return ConstantDrMath.speedScaleForGnssCourse(speedMps)
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
    /** Accounting-speed samples → [SpeedIntegrator] between DR ticks. */
    private var speedSampleJob: Job? = null
    private var lastSig: String? = null
    private var lastGoodLoc: LocValues? = null
    private var lastGoodAtElapsedMs: Long = 0L
    private var retainLat: Double = 0.0
    private var retainLon: Double = 0.0
    private var lastPushElapsedMs: Long = 0L
    /** Last time the mock provider was written (or explicitly stopped) on an inject cadence tick. */
    private var lastInjectElapsedMs: Long = 0L
    /** Previous [takeDrDistanceM] pulse-DR flag; rising/falling edges sync cursors. */
    private var drUsedPulseLastTick: Boolean = false
    /** True on ticks that may call [LocationMockManager.setMockLocation] / stop. */
    private var mockWriteDue: Boolean = true
    private var wasRetaining: Boolean = false
    /** ElapsedRealtime when current retention / non-live mock stretch began; 0 = not retaining. */
    private var retentionStartedAtElapsedMs: Long = 0L
    /** Horizontal accuracy (m) at the moment retention began (last live estimate). */
    private var retentionBaseAccuracyM: Float = LocationMockManager.FIX_ACCURACY_M
    /** Last live horizontal accuracy while GNSS was driving the mock point. */
    private var lastLiveAccuracyM: Float = LocationMockManager.FIX_ACCURACY_M
    /**
     * Last held nose/travel heading for mock (degrees).
     * `null` = unknown; **`0f` = north (valid)** once seeded from GNSS/DR/road-match.
     * Do not filter with `!= 0f` when reading this field.
     */
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
    /** Until this elapsed ms, GNSS course may catch up without travel (post hard-resync). */
    private var courseCatchUpUntilElapsedMs: Long = 0L

    /** Continuous yaw bias/scale from truthful GNSS (CONSTANT only). */
    private val onlineYawCalib = OnlineYawCalibEstimator()

    private val sharedRoadMatch =
        roadMatch ?: RoadMatchController(roadMapsDir)

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
        persistShadowImmediate()
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
        retentionStartedAtElapsedMs = 0L
        retentionBaseAccuracyM = LocationMockManager.FIX_ACCURACY_M
        lastLiveAccuracyM = LocationMockManager.FIX_ACCURACY_M
        lastKnownBearingDeg = null
        usingPersistedSeed = false
        lastPushElapsedMs = 0L
        constantMismatchStreak = 0
        lastCalibSeenAtEpochMs = 0L
        constantHasOrigin = false
        lastMode = null
        lastEnabled = null
        hardResyncTrustSinceElapsedMs = 0L
        courseCatchUpUntilElapsedMs = 0L
        onlineYawCalib.reset()
        ConstantDrRuntimeDebug.clear()
        OnlineYawCalibRuntimeDebug.clear()
        sharedRoadMatch.reset()
        RoadMatchRuntimeDebug.clear()
        RoadMatchOverlayRepository.clear()
        RoadMatchManualSeedRepository.clear()
        YawIntegrator.discard()
        SteerHeadingIntegrator.reset()
        SpeedIntegrator.reset()
        drUsedPulseLastTick = false
        MockJunkFixFilter.resetSession()
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
                if (headingSource.value.usesSteer) {
                    SteerHeadingIntegrator.onSpeedKmh(signedSteerSpeedKmh(speed), now)
                }
            }
        }
    }

    /** Signed calibrated speed for the bicycle model (negative in reverse). */
    private fun signedSteerSpeedKmh(rawCanKmh: Float?): Float? {
        if (rawCanKmh == null || !rawCanKmh.isFinite()) return null
        val scaled = DriveCalibrationStore.applyCanSpeed(rawCanKmh)
        // WHEN_NO_FIX forces CONSTANT even when stored mode is NONE — reverse must
        // follow the effective DR engine, not the persisted Direct/NONE setting.
        val effectiveMode = mockPower.value.effectiveCanSpeedMode(canSpeedMode.value)
        val reverse = shouldApplyReverse(effectiveMode, considerReverseEnabled.value)
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
     *
     * Pulse DR never falls through to [SpeedIntegrator]: a 0-pulse tick must not
     * dump the CAN backlog that accumulated while pulse was the source. Enabling
     * pulse syncs the wheel cursor; disabling discards pending CAN metres.
     */
    private fun takeDrDistanceM(now: Long, canKmh: Float?, stepAllowed: Boolean): Double {
        if (!stepAllowed) {
            SpeedIntegrator.discardThrough(now)
            return 0.0
        }
        val pulseOn = vad.dashing.tbox.vehicle.WheelPulseCalibrationStore.isMockDrPulseEnabled()
        if (pulseOn != drUsedPulseLastTick) {
            if (pulseOn) {
                vad.dashing.tbox.vehicle.WheelPulseOdometer.syncDrCursor()
            }
            SpeedIntegrator.discardThrough(now)
            if (canKmh != null) {
                SpeedIntegrator.onRawSample(canKmh, now)
            }
            drUsedPulseLastTick = pulseOn
        }
        if (pulseOn) {
            SpeedIntegrator.discardThrough(now)
            if (canKmh != null) {
                SpeedIntegrator.onRawSample(canKmh, now)
            }
            val pulseM = vad.dashing.tbox.vehicle.WheelPulseOdometer.flushDrDistanceM().toDouble()
            return if (pulseM.isFinite() && pulseM > 0.0) pulseM else 0.0
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
                                vad.dashing.tbox.mbcan.MbCanSignal.TurnSignals,
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
            MockHeadingSource.GYRO_STEER -> {
                // Gyro primary; steer fills quiet/stale gyro and, when it leads on
                // the same side, pulls toward the bicycle-model delta (blend + cap).
                // Never sum both. Held-wheel trust drops only after age >
                // MAX_ANGLE_SAMPLE_AGE_MS (1 s) so a late mock tick still flushes
                // the fresh portion; beyond that hybrid gets steerDelta=0.
                val lastYawAt = YawIntegrator.lastSampleElapsedMs()
                val gyroFresh = lastYawAt > 0L && now - lastYawAt <= MAX_YAW_SAMPLE_AGE_MS
                if (!gyroFresh) {
                    YawIntegrator.discardThrough(now)
                }
                val gyroDelta = if (gyroFresh) YawIntegrator.consumeDeltaDeg() else 0f
                SteerHeadingIntegrator.onSpeedKmh(signedSteerSpeedKmhNow(now))
                SteerHeadingIntegrator.tick(now)
                val steerDelta = SteerHeadingIntegrator.consumeDeltaDeg()
                val delta = hybridGyroSteerDelta(gyroDelta, steerDelta)
                if (delta != 0f) {
                    applyYawDeltaToBearing(nose, delta) to true
                } else {
                    nose to false
                }
            }
        }
    }

    /**
     * Shared DR motion step: heading and distance share the same gate so a
     * parked car cannot turn, but a crawl with real metres can. Braking tail
     * still steps on pending path. Path length is projected on the mid-course
     * of the step when heading moved.
     */
    private fun applyDrMotionStep(
        noseIn: Float,
        reverse: Boolean,
        now: Long,
        canKmh: Float?,
        useCan: Boolean,
        speedKmh: Float,
        dtSec: Double,
    ): Triple<Float, Boolean, Double> {
        var pending = SpeedIntegrator.pendingDistanceM()
        val pulseOn = vad.dashing.tbox.vehicle.WheelPulseCalibrationStore.isMockDrPulseEnabled()
        if (pulseOn) {
            pending = maxOf(
                pending,
                vad.dashing.tbox.vehicle.WheelPulseOdometer.peekDrPendingM().toDouble(),
            )
        }
        var gate = classifyDrMotion(speedKmh, pending, dtSec)
        if (gate == DrMotionGate.HOLD_CRAWL) {
            if (!useCan) {
                applyHeadingDelta(noseIn, headingSource.value, allowIntegrate = false, now = now)
                refreshSpeedIntegratorWhileGated(now, null)
                return Triple(noseIn, false, 0.0)
            }
            if (pulseOn) {
                pending = maxOf(
                    SpeedIntegrator.pendingDistanceM(),
                    vad.dashing.tbox.vehicle.WheelPulseOdometer.peekDrPendingM().toDouble(),
                )
            } else {
                SpeedIntegrator.flushTo(now)
                if (canKmh != null) {
                    SpeedIntegrator.onRawSample(canKmh, now)
                }
                pending = SpeedIntegrator.pendingDistanceM()
            }
            gate = classifyDrMotion(speedKmh, pending, dtSec)
        }
        if (gate == DrMotionGate.DISCARD) {
            applyHeadingDelta(noseIn, headingSource.value, allowIntegrate = false, now = now)
            refreshSpeedIntegratorWhileGated(now, canKmh.takeIf { useCan })
            return Triple(noseIn, false, 0.0)
        }
        if (gate == DrMotionGate.HOLD_CRAWL) {
            // Not enough metres yet: keep gyro/steer pending, do not discard.
            return Triple(noseIn, false, 0.0)
        }
        val distanceM = if (useCan) {
            takeDrDistanceM(now, canKmh, stepAllowed = true)
        } else {
            refreshSpeedIntegratorWhileGated(now, null)
            0.0
        }
        if (distanceM <= 0.0) {
            // A noisy gyro, steering sample, or GNSS course must not rotate a parked
            // vehicle. Retire samples through now so they cannot be replayed at pull-away.
            applyHeadingDelta(noseIn, headingSource.value, allowIntegrate = false, now = now)
            return Triple(noseIn, false, 0.0)
        }
        val noseBefore = noseIn
        val (proposedNose, proposedApplied) = applyHeadingDelta(
            nose = noseIn,
            source = headingSource.value,
            allowIntegrate = true,
            now = now,
        )
        val noseAfter = if (proposedApplied) {
            constrainHeadingToTravel(
                bearingBeforeDeg = noseBefore,
                proposedBearingDeg = proposedNose,
                distanceM = distanceM,
                wheelbaseM = SteerCalibrationStore.offsets.wheelbaseM,
            )
        } else {
            proposedNose
        }
        val applied = proposedApplied && noseAfter != noseBefore
        val stepNose = if (applied) {
            averageBearingDeg(noseBefore, noseAfter)
        } else {
            noseAfter
        }
        val travel = ConstantDrMath.travelBearingFromNoseHeading(stepNose, reverse)
        val stepped = extrapolateLatLon(retainLat, retainLon, travel, distanceM)
        retainLat = stepped.first
        retainLon = stepped.second
        return Triple(noseAfter, applied, distanceM)
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
        val bearingForDisk = lastKnownBearingDeg ?: loc.trueDirection.takeIf { it != 0f }
        val fix = MockLastGoodFix.fromLive(loc, System.currentTimeMillis(), bearingForDisk) ?: return
        val toWrite = persistDebouncer.note(fix, nowElapsedMs) ?: return
        scope.launch {
            runCatching { savePersistedLastGood(toWrite) }
        }
    }

    private fun persistShadow(nowElapsedMs: Long) {
        val fix = currentShadowFix() ?: return
        val toWrite = persistDebouncer.note(fix, nowElapsedMs) ?: return
        scope.launch {
            runCatching { savePersistedLastGood(toWrite) }
        }
    }

    /** Shutdown: write the inertial point even if the 60 s debounce has not elapsed. */
    private fun persistShadowImmediate() {
        val fix = currentShadowFix() ?: return
        persistDebouncer.note(fix, SystemClock.elapsedRealtime())
        val pending = persistDebouncer.takeFlush() ?: fix
        scope.launch {
            withContext(NonCancellable) {
                runCatching { savePersistedLastGood(pending) }
            }
        }
    }

    private fun currentShadowFix(): MockLastGoodFix? {
        if (!constantHasOrigin) return null
        if (retainLat == 0.0 && retainLon == 0.0) return null
        val bearing = lastKnownBearingDeg?.takeIf { it.isFinite() } ?: 0f
        return MockLastGoodFix.fromShadow(
            latitude = retainLat,
            longitude = retainLon,
            altitude = constantAlt,
            bearingDeg = bearing,
            savedAtEpochMs = System.currentTimeMillis(),
        )
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
        courseCatchUpUntilElapsedMs = 0L
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
        val onlineYawOn = onlineYawCalibEnabled.value
        val considerRev = considerReverseEnabled.value
        val sig =
            "$enabled:${power.name}:$period:${locationSource.value}:$mode:$heading:$filterOn:$autoCalib:$onlineYawOn:$considerRev"

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
        ensureSteerInterest(enabled && mode.enhancesMock && heading.usesSteer)
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
            // Overlay is mock-shadow-only; keep the shared matcher for the OSM widget.
            RoadMatchOverlayRepository.clear()
            return
        }
        job = scope.launch {
            lastInjectElapsedMs = 0L
            while (isActive) {
                val now = SystemClock.elapsedRealtime()
                mockWriteDue = isInjectDue(now, lastInjectElapsedMs, periodMs.value)
                try {
                    pushOnce(power, mode, filterOn)
                } catch (oom: OutOfMemoryError) {
                    // Road-pack load must never kill the mock loop — Yandex Maps / nav widgets
                    // lose the arrow when mock updates stop.
                    Log.e(TAG, "mock push OOM; clearing road graphs", oom)
                    RoadGraphStore.clear()
                    sharedRoadMatch.reset()
                } catch (t: Throwable) {
                    Log.e(TAG, "mock push failed", t)
                }
                if (mockWriteDue) lastInjectElapsedMs = now
                delay(
                    roadMatchTuning.value.long(
                        vad.dashing.tbox.location.roadmatch.RoadMatchTuningKey.MATCH_CADENCE_MS,
                    ),
                )
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
            MockPowerState.WHEN_NO_FIX -> shouldInjectWhenNoFix(gnssTruthful)
        }

        if (mode.isConstantCalc) {
            // Advanced: junk filter (when on) also gates blend / last-good / origin.
            // Stale LocValues (USB unplug) must not blend.
            val gnssFixPresent = gnssFresh && live.locateStatus && hasValidCoordinates(live)
            val gnssPresent = constantAcceptsLiveGnss(
                junkFilterOn = junkFilterOn,
                gnssTruthful = gnssTruthful,
                gnssFixPresent = gnssFixPresent,
            )
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
        val nonConstantDemand = roadMatchDemand.value
        if (!nonConstantDemand.matchNeeded) {
            sharedRoadMatch.reset()
            RoadMatchOverlayRepository.clear()
        } else {
            val livePose = if (hasValidCoordinates(live) && live.trueDirection != 0f) {
                RoadMatchPose(
                    lat = live.latitude,
                    lon = live.longitude,
                    bearingDeg = live.trueDirection,
                )
            } else {
                null
            }
            val speed = when {
                live.speed.isFinite() && live.speed > 0f -> live.speed
                canKmh != null && canKmh.isFinite() -> canKmh
                else -> 0f
            }
            sharedRoadMatch.tick(
                demand = nonConstantDemand,
                pose = livePose,
                speedKmh = speed,
                nowElapsedMs = now,
                allowAgainstOneway = shouldApplyReverse(mode, considerReverseEnabled.value),
                turnHint = roadMatchTurnHint(),
                turnIntent = roadMatchTurnIntent(),
                turnFlashCount = roadMatchTurnFlashCount(),
                gnssPositionTrust = roadMatchGnssPositionTrust(
                    liveGnss = livePose != null && gnssTruthful,
                    live = live,
                    tuning = roadMatchTuning.value,
                ),
                tuning = roadMatchTuning.value.also { applyTurnSignalLatchTuning(it) },
            )
            RoadMatchOverlayRepository.clear()
        }
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
            retentionStartedAtElapsedMs = 0L
            usingPersistedSeed = false
            lastPushElapsedMs = now
            if (liveUsable) {
                lastLiveAccuracyM = LocationMockManager.liveHorizontalAccuracyMeters(
                    hdop = live.hdop,
                    hrms = live.hrms,
                )
                publishLivePassthrough(live, liveUsable = true, gnssTruthful = gnssTruthful, injectToSystem = injectToSystem)
            } else if (isJunkLive(live, junkFilterOn, liveUsable)) {
                val good = lastGoodLoc
                if (good != null && hasValidCoordinates(good)) {
                    publishStaticLastGood(
                        good,
                        liveUsable = false,
                        gnssTruthful = gnssTruthful,
                        injectToSystem = injectToSystem,
                        nowElapsedMs = now,
                    )
                } else {
                    // No last good yet — do not push junk into mock.
                    publishLostDisplay(liveUsable = false, live = live, gnssTruthful = gnssTruthful)
                }
            } else {
                // No enhance, not junk (e.g. no fix) — GNSS as-is.
                publishLivePassthrough(live, liveUsable = false, gnssTruthful = gnssTruthful, injectToSystem = injectToSystem)
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
            retentionStartedAtElapsedMs = 0L
            lastLiveAccuracyM = LocationMockManager.liveHorizontalAccuracyMeters(
                hdop = live.hdop,
                hrms = live.hrms,
            )
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
                injectToSystem = injectToSystem,
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

        var nose = lastKnownBearingDeg
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
                val (nextNose, applied, _) = applyDrMotionStep(
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
        applyMockProvider(
            locValues = out,
            retainingFix = retaining,
            hasReliableSpeed = true,
            hasReliableBearing = outBearing != null,
            injectToSystem = injectToSystem,
            nowElapsedMs = now,
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
     * optional reverse invert of travel bearing. Junk filter (when on) gates blend,
     * origin, and last-good as well as [gnssTruthful] / hard resync / auto-calib.
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
        var originFromManualSeed = false
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
                } else if (applyPendingManualSeed(reverse)) {
                    wasRetaining = true
                    originFromManualSeed = true
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

        var nose = lastKnownBearingDeg
        var bearingSource = GeoBearingSource.RETENTION

        val dtSec = if (lastPushElapsedMs > 0L) {
            ((now - lastPushElapsedMs).coerceAtLeast(0L) / 1000.0)
        } else {
            0.0
        }
        // Heading + distance share one gate (moving / crawl metres / braking tail).
        var drTravelDistanceM = 0.0
        if (nose != null) {
            val (nextNose, applied, travelledM) = applyDrMotionStep(
                noseIn = nose,
                reverse = reverse,
                now = now,
                canKmh = canKmh,
                useCan = useCan,
                speedKmh = speedKmh,
                dtSec = dtSec,
            )
            drTravelDistanceM = travelledM
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
        var didManualSeed = originFromManualSeed

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
            // Moving: CAN↔GNSS speed agree. Parked: both near-stopped + good accuracy
            // (longer trust window — field: shadow stuck 100+ m while GNSS sits still).
            val movingTrust = gnssTruthful &&
                ConstantDrMath.gnssSpeedAgreesForHardResync(live.speed, canKmh)
            val stationaryTrust = gnssTruthful &&
                ConstantDrMath.isStationaryHardResyncCandidate(
                    gnssKmh = live.speed,
                    canKmh = canKmh,
                    horizontalAccuracyM = accuracyM,
                )
            val trustCandidate = movingTrust || stationaryTrust
            if (trustCandidate) {
                if (hardResyncTrustSinceElapsedMs == 0L) {
                    hardResyncTrustSinceElapsedMs = now
                }
            } else {
                hardResyncTrustSinceElapsedMs = 0L
            }
            val requiredTrustMs = ConstantDrMath.hardResyncTrustRequiredMs(
                movingTrust = movingTrust,
                stationaryTrust = stationaryTrust,
            )
            val trustHeld = hardResyncTrustSinceElapsedMs > 0L &&
                (now - hardResyncTrustSinceElapsedMs) >= requiredTrustMs

            val altitudeOk = ConstantDrMath.isHardResyncAltitudePlausible(
                shadowAlt = constantAlt,
                gnssAlt = live.altitude,
            )
            if (ConstantDrMath.shouldHardResync(dist, thresholdM) && trustHeld && altitudeOk) {
                retainLat = live.latitude
                retainLon = live.longitude
                constantAlt = live.altitude
                constantVisibleSats = live.visibleSatellites
                constantUsingSats = live.usingSatellites
                // Position snap to recovered GNSS: also take GNSS course immediately when
                // moving (kinematic travel gate does not apply to this recovery snap).
                if (shouldAcceptGnssCourse(canKmh, live.speed, live.trueDirection)) {
                    nose = ConstantDrMath.noseHeadingFromCourseOverGround(
                        live.trueDirection,
                        reverse,
                    )
                    lastKnownBearingDeg = nose
                    bearingSource = GeoBearingSource.GNSS
                    courseCatchUpUntilElapsedMs = now + HARD_RESYNC_COURSE_CATCHUP_MS
                }
                effectivePosWeight = 1f
                constantMismatchStreak = 0
                hardResyncTrustSinceElapsedMs = 0L
                didHardResync = true
                // Drop sticky road-edge / beam state so the next match re-seeds on the snapped pose.
                sharedRoadMatch.reset()
                RoadMatchRuntimeDebug.clear()
                RoadMatchOverlayRepository.clear()
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
                    // Far shadow awaiting hard-resync, or post-snap catch-up: pull course
                    // without requiring travel this tick (still needs real motion speed).
                    val farRecovery = ConstantDrMath.shouldHardResync(dist, thresholdM) &&
                        shouldAcceptGnssCourse(canKmh, live.speed, live.trueDirection)
                    val courseCatchUp = now < courseCatchUpUntilElapsedMs
                    val allowCourseWithoutTravel = farRecovery || courseCatchUp
                    val courseMScale = if (allowCourseWithoutTravel) 1f else mScale
                    val courseW = ConstantDrMath.courseWeightFromConfidence(confidence, residual) *
                        courseMScale *
                        gnssCourseScaleForTravel(
                            speedMps = speedMps,
                            distanceM = drTravelDistanceM,
                            allowWithoutTravel = allowCourseWithoutTravel,
                        )
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

            // Online yaw L/R scale (turns) from truthful GNSS — no ay/v; no straight bias.
            maybeRunOnlineYawCalib(
                now = now,
                live = live,
                canKmh = canKmh,
                reverse = reverse,
                gnssTruthful = gnssTruthful,
            )
        } else {
            hardResyncTrustSinceElapsedMs = 0L
            courseCatchUpUntilElapsedMs = 0L
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

        if (applyPendingManualSeed(reverse)) {
            nose = lastKnownBearingDeg
            bearingSource = GeoBearingSource.RETENTION
            didManualSeed = true
        }

        lastPushElapsedMs = now
        persistShadow(now)
        var outBearing = nose?.let { ConstantDrMath.travelBearingFromNoseHeading(it, reverse) }
        val demand = roadMatchDemand.value
        val matchPose = buildConstantMatchPose(
            lat = retainLat,
            lon = retainLon,
            travelBearingDeg = outBearing,
            gnssPresent = gnssPresent,
            gnssCourseDeg = live.trueDirection,
            speedKmh = speedKmh,
        )
        val matched = sharedRoadMatch.tick(
            demand = demand,
            pose = matchPose,
            speedKmh = speedKmh,
            nowElapsedMs = now,
            allowAgainstOneway = reverse,
            turnHint = roadMatchTurnHint(),
            turnIntent = roadMatchTurnIntent(),
            turnFlashCount = roadMatchTurnFlashCount(),
            gnssPositionTrust = roadMatchGnssPositionTrust(
                liveGnss = gnssPresent,
                live = live,
                shadowLat = retainLat,
                shadowLon = retainLon,
                tuning = roadMatchTuning.value,
            ),
            tuning = roadMatchTuning.value.also { applyTurnSignalLatchTuning(it) },
        )
        // Published mock / overlay pose (may be rail while retain stays free in Rails).
        var publishLat = retainLat
        var publishLon = retainLon
        val railsMode = demand.mode == vad.dashing.tbox.location.roadmatch.RoadMatchMode.RAILS
        if (demand.correctPose &&
            matched != null &&
            matched.lat.isFinite() && matched.lon.isFinite() &&
            matched.bearingDeg.isFinite() &&
            matched.lat in -90.0..90.0 && matched.lon in -180.0..180.0
        ) {
            // Travel bearing from the edge — including ~0° (north). Always reliable.
            outBearing = matched.bearingDeg
            if (railsMode) {
                // Rails: mock follows the corridor (free DR + lateral pull to the
                // sticky edge); retain+nose stay instrument DR so the free particle
                // can diverge (yards / wrong fork) and break off.
                publishLat = matched.lat
                publishLon = matched.lon
            } else {
                retainLat = matched.lat
                retainLon = matched.lon
                publishLat = retainLat
                publishLon = retainLon
                nose = ConstantDrMath.noseHeadingFromCourseOverGround(matched.bearingDeg, reverse)
                lastKnownBearingDeg = nose
                bearingSource = GeoBearingSource.RETENTION
            }
        }
        if (demand.matchNeeded && constantHasOrigin) {
            // Overlay GNSS: show live or last-good even when the fix is frozen / USB down,
            // but only while the gap to the green shadow is ≤ 1000 m.
            val overlayGnss = when {
                hasValidCoordinates(live) -> live
                else -> lastGoodLoc?.takeIf { hasValidCoordinates(it) }
            }
            val gnssGapM = if (overlayGnss != null) {
                ConstantDrMath.distanceMeters(
                    publishLat,
                    publishLon,
                    overlayGnss.latitude,
                    overlayGnss.longitude,
                )
            } else {
                Double.POSITIVE_INFINITY
            }
            val gnssForOverlay = overlayGnss != null &&
                gnssGapM <= RoadMatchOverlayBuilder.GNSS_MAX_GAP_FROM_SHADOW_M
            RoadMatchOverlayPublisher.publish(
                controller = sharedRoadMatch,
                matchEnabled = true,
                shadowLat = publishLat,
                shadowLon = publishLon,
                shadowBearingDeg = outBearing,
                gnssLat = overlayGnss?.latitude?.takeIf { gnssForOverlay },
                gnssLon = overlayGnss?.longitude?.takeIf { gnssForOverlay },
                gnssBearingDeg = overlayGnss?.trueDirection?.takeIf { gnssForOverlay && it != 0f },
                gnssVisible = gnssForOverlay,
            )
        } else if (!demand.matchNeeded) {
            RoadMatchOverlayRepository.clear()
        }
        // Green when GNSS contributes (soft blend or hard resync); blue when shadow alone.
        val liveUsableOut = gnssPresent && effectivePosWeight > 0.05f
        val retainingOut = !liveUsableOut
        if (liveUsableOut) {
            lastLiveAccuracyM = LocationMockManager.liveHorizontalAccuracyMeters(
                hdop = live.hdop,
                hrms = live.hrms,
            )
            retentionStartedAtElapsedMs = 0L
        }
        ConstantDrRuntimeDebug.publish(
            ConstantDrRuntimeDebug.Snapshot(
                active = true,
                shadowDistM = shadowDistM,
                thresholdM = thresholdMOut,
                posW = effectivePosWeight,
                constantHasOrigin = constantHasOrigin,
                blendLive = liveUsableOut,
                hardResync = didHardResync,
                manualSeed = didManualSeed,
                accuracyM = accuracyMOut,
            ),
        )
        val out = LocValues(
            locateStatus = true,
            latitude = publishLat,
            longitude = publishLon,
            altitude = constantAlt,
            speed = speedKmh,
            trueDirection = outBearing ?: 0f,
            visibleSatellites = constantVisibleSats,
            usingSatellites = constantUsingSats,
        )
        applyMockProvider(
            locValues = out,
            retainingFix = retainingOut,
            hasReliableSpeed = true,
            hasReliableBearing = outBearing != null,
            injectToSystem = injectToSystem,
            nowElapsedMs = now,
        )
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

    /**
     * Write or remove the system mock provider only on inject-cadence ticks.
     * Inner DR ticks keep the last written mock so nav does not stall.
     */
    private fun applyMockProvider(
        locValues: LocValues,
        retainingFix: Boolean,
        hasReliableSpeed: Boolean,
        hasReliableBearing: Boolean,
        injectToSystem: Boolean,
        nowElapsedMs: Long = lastPushElapsedMs,
    ) {
        if (retainingFix) {
            if (retentionStartedAtElapsedMs <= 0L) {
                retentionStartedAtElapsedMs = nowElapsedMs.takeIf { it > 0L }
                    ?: android.os.SystemClock.elapsedRealtime()
                retentionBaseAccuracyM = lastLiveAccuracyM
            }
        } else {
            retentionStartedAtElapsedMs = 0L
            // Prefer GST/HDOP on this sample; LocValues from CONSTANT shadow often omit them.
            if ((locValues.hrms != null && locValues.hrms > 0f) ||
                (locValues.hdop != null && locValues.hdop > 0f)
            ) {
                lastLiveAccuracyM = LocationMockManager.liveHorizontalAccuracyMeters(
                    hdop = locValues.hdop,
                    hrms = locValues.hrms,
                )
            }
        }
        if (!mockWriteDue) return
        if (injectToSystem) {
            val ageMs = MockRetentionAccuracy.ageMs(retentionStartedAtElapsedMs, nowElapsedMs)
            locationMockManager.setMockLocation(
                locValues = locValues,
                retainingFix = retainingFix,
                hasReliableSpeed = hasReliableSpeed,
                hasReliableBearing = hasReliableBearing,
                retentionAgeMs = ageMs,
                retentionBaseAccuracyM = retentionBaseAccuracyM,
            )
        } else {
            locationMockManager.stopMockLocation()
        }
    }

    /** Push live GNSS without CAN / retention / heading-hold / DR. */
    private fun publishLivePassthrough(
        live: LocValues,
        liveUsable: Boolean,
        gnssTruthful: Boolean,
        injectToSystem: Boolean,
    ) {
        val bearing = live.trueDirection.takeIf { it != 0f }
        applyMockProvider(
            locValues = live,
            retainingFix = false,
            hasReliableSpeed = true,
            hasReliableBearing = bearing != null,
            injectToSystem = injectToSystem,
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
        injectToSystem: Boolean,
    ) {
        val accepted = shouldAcceptGnssCourse(canKmh, live.speed, live.trueDirection)
        if (accepted) {
            lastKnownBearingDeg = live.trueDirection
        }
        // Held heading may be 0° (north); only null means missing.
        val bearing = lastKnownBearingDeg
        val bearingSource = when {
            accepted -> GeoBearingSource.GNSS
            bearing != null -> GeoBearingSource.HELD
            else -> GeoBearingSource.HELD
        }
        val out = live.copy(trueDirection = bearing ?: 0f)
        applyMockProvider(
            locValues = out,
            retainingFix = false,
            hasReliableSpeed = true,
            hasReliableBearing = bearing != null,
            injectToSystem = injectToSystem,
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
        injectToSystem: Boolean,
        nowElapsedMs: Long = android.os.SystemClock.elapsedRealtime(),
    ) {
        val bearing = lastKnownBearingDeg
            ?: good.trueDirection.takeIf { it != 0f }
        val out = good.copy(
            trueDirection = bearing ?: 0f,
            locateStatus = true,
        )
        applyMockProvider(
            locValues = out,
            retainingFix = true,
            hasReliableSpeed = true,
            hasReliableBearing = bearing != null,
            injectToSystem = injectToSystem,
            nowElapsedMs = nowElapsedMs,
        )
        GeoDisplayRepository.publish(
            GeoDisplayState(
                liveUsable = liveUsable,
                retaining = true,
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
     * Online yaw L/R scale (turns) while GNSS is truthful.
     * Used by CONSTANT and by ALWAYS / WHEN_FIX_LOST while live.
     * Updates in-memory stores immediately; persists via debounced callbacks.
     * No-op (and resets) when [onlineYawCalibEnabled] is off or heading is not gyro.
     * Straight bias EMA is off ([OnlineYawCalibEstimator] default).
     */
    private fun maybeRunOnlineYawCalib(
        now: Long,
        live: LocValues,
        canKmh: Float?,
        reverse: Boolean,
        gnssTruthful: Boolean,
    ) {
        // Online yaw calib only applies when enabled and gyro is the heading source.
        if (!onlineYawCalibEnabled.value || !headingSource.value.usesGyro) {
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

    /**
     * F3: snap the CONSTANT shadow to the tile draft, then reset the matcher so the
     * same tick re-seeds on the new pose (same idea as hard-resync, not GNSS).
     */
    private fun applyPendingManualSeed(reverse: Boolean): Boolean {
        val seed = RoadMatchManualSeedRepository.take() ?: return false
        retainLat = seed.lat
        retainLon = seed.lon
        constantHasOrigin = true
        lastKnownBearingDeg = ConstantDrMath.noseHeadingFromCourseOverGround(
            seed.travelBearingDeg,
            reverse,
        )
        hardResyncTrustSinceElapsedMs = 0L
        courseCatchUpUntilElapsedMs = 0L
        sharedRoadMatch.reset()
        RoadMatchRuntimeDebug.clear()
        return true
    }
}

/** Outcome of [MockLocationJob.classifyDrMotion] for one inner DR tick. */
enum class DrMotionGate {
    /** Consume path and apply gyro/steer/hybrid heading. */
    STEP,
    /** Crawl: keep path and heading pending until [MockLocationJob.CRAWL_DR_MIN_DISTANCE_M]. */
    HOLD_CRAWL,
    /** Stopped: discard path, gyro, and steer so they cannot replay at pull-away. */
    DISCARD,
}
