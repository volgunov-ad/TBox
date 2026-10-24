package vad.dashing.tbox.location

import android.hardware.GeomagneticField
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vad.dashing.tbox.LocValues
import vad.dashing.tbox.TboxRepository
import vad.dashing.tbox.esp.LocationSource
import vad.dashing.tbox.phoneble.PhoneBleCodec
import vad.dashing.tbox.phoneble.PhoneSensorMath
import java.util.ArrayDeque
import java.util.Date
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class PhoneGyroCalibration(
    val biasDegPerSec: Float = 0f,
    val scaleLeft: Float = 1f,
    val scaleRight: Float = 1f,
    val sign: Int = 1,
    val calibratedAtEpochMs: Long = 0L,
)

object PhoneGyroCalibrationStore {
    const val MIN_SCALE = 0.5f
    const val MAX_SCALE = 1.6f
    const val MAX_ABS_BIAS_DEG_PER_SEC = 10f

    private val _calibration = MutableStateFlow(PhoneGyroCalibration())
    val calibration: StateFlow<PhoneGyroCalibration> = _calibration.asStateFlow()

    fun update(next: PhoneGyroCalibration) {
        _calibration.value = next.copy(
            biasDegPerSec = next.biasDegPerSec.takeIf { it.isFinite() }
                ?.coerceIn(-MAX_ABS_BIAS_DEG_PER_SEC, MAX_ABS_BIAS_DEG_PER_SEC) ?: 0f,
            scaleLeft = next.scaleLeft.takeIf { it.isFinite() }?.coerceIn(MIN_SCALE, MAX_SCALE) ?: 1f,
            scaleRight = next.scaleRight.takeIf { it.isFinite() }?.coerceIn(MIN_SCALE, MAX_SCALE) ?: 1f,
            sign = if (next.sign < 0) -1 else 1,
        )
    }

    fun reset() {
        _calibration.value = PhoneGyroCalibration()
    }
}

data class PhoneCompassCalibration(
    /** 8 sectors of 45° (0: 0..45°, 1: 45..90°, ..., 7: 315..360°) hard-iron residual (°). */
    val sectorOffsetsDeg: List<Float> = List(SECTOR_COUNT) { 0f },
    val calibratedAtEpochMs: Long = 0L,
) {
    companion object {
        const val SECTOR_COUNT = 8
    }
}

object PhoneCompassCalibrationStore {
    const val MAX_SECTOR_OFFSET_DEG = 25f

    private val _calibration = MutableStateFlow(PhoneCompassCalibration())
    val calibration: StateFlow<PhoneCompassCalibration> = _calibration.asStateFlow()

    fun update(next: PhoneCompassCalibration) {
        val sectors = List(PhoneCompassCalibration.SECTOR_COUNT) { index ->
            next.sectorOffsetsDeg.getOrNull(index)
                ?.takeIf { it.isFinite() }
                ?.coerceIn(-MAX_SECTOR_OFFSET_DEG, MAX_SECTOR_OFFSET_DEG)
                ?: 0f
        }
        _calibration.value = next.copy(sectorOffsetsDeg = sectors)
    }

    fun reset() {
        _calibration.value = PhoneCompassCalibration()
    }
}

data class HeadingFallbackStatus(
    val selectedSource: MockHeadingSource = MockHeadingSource.GYRO,
    val effectiveSource: MockHeadingSource = MockHeadingSource.GYRO,
    val fallbackReason: String? = null,
)

object HeadingFallbackStatusRepository {
    private val _status = MutableStateFlow(HeadingFallbackStatus())
    val status: StateFlow<HeadingFallbackStatus> = _status.asStateFlow()

    fun publish(
        selected: MockHeadingSource,
        effective: MockHeadingSource,
        reason: String?,
    ) {
        val cur = _status.value
        if (cur.selectedSource != selected || cur.effectiveSource != effective || cur.fallbackReason != reason) {
            _status.value = HeadingFallbackStatus(selected, effective, reason)
        }
    }
}

/**
 * Head-unit receiver and processor for phone telemetry (`LOC_POS` + `LOC_MOTION`, `YAW`, `MAG`)
 * over the direct RFCOMM channel (`PhoneDirectServer`).
 */
object PhoneTelemetryRepository {
    const val MAX_LOC_AGE_MS = 3_000L
    const val MAX_YAW_AGE_MS = 1_200L
    const val MAX_MAG_AGE_MS = 3_500L
    const val ACTIVE_PHONE_SILENT_TAKEOVER_MS = 10_000L

    private const val COMPASS_MEDIAN_WINDOW = 5
    private const val MAX_MAG_FIELD_JUMP_UT = 12f
    private const val MAX_UNEXPLAINED_AZIMUTH_JUMP_DEG = 35f
    private const val MOUNT_CALIB_MIN_SPEED_KMH = 15f
    private const val MOUNT_CALIB_MAX_STEER_DEG = 12f
    private const val MOUNT_CALIB_MAX_YAW_DEG_PER_SEC = 2.5f
    private const val MOUNT_CALIB_CONFIRM_DISTANCE_M = 50.0

    data class PhoneTelemetryUiState(
        val preferredPhoneIdHex: String? = null,
        val activePhoneIdHex: String? = null,
        val activePhoneName: String? = null,
        val lastLocation: PhoneBleCodec.PhoneLocation? = null,
        val lastLocAtElapsedMs: Long = 0L,
        val lastYawRateDegPerSec: Float? = null,
        val lastYawAtElapsedMs: Long = 0L,
        val lastYawRejectReason: String? = null,
        val lastMagAzimuthDeg: Float? = null,
        val lastCalibratedCompassDeg: Float? = null,
        val lastFieldStrengthUt: Float? = null,
        val lastMagAccuracy: Int? = null,
        val lastMagAtElapsedMs: Long = 0L,
        val lastMagRejectReason: String? = null,
        val mountYawOffsetDeg: Float? = null,
        val mountOffsetConfirmed: Boolean = false,
        val monoClockOffsetMs: Long? = null,
    )

    private val lock = Any()
    private val _uiState = MutableStateFlow(PhoneTelemetryUiState())
    val uiState: StateFlow<PhoneTelemetryUiState> = _uiState.asStateFlow()

    private var preferredPhoneIdHex: String? = null
    private var activePhoneIdHex: String? = null
    private var activePhoneName: String? = null
    private var lastActivePhonePacketAtElapsedMs: Long = 0L

    private var lastLocation: PhoneBleCodec.PhoneLocation? = null
    private var lastLocAtElapsedMs: Long = 0L
    private var lastLocValues: LocValues? = null

    private var pendingYawNavDeltaDeg: Double = 0.0
    private var lastYawRateDegPerSec: Float? = null
    private var lastYawAtElapsedMs: Long = 0L
    private var lastYawRejectReason: String? = null

    // Online phone gyro calibration accumulators
    private var gyroArcPhoneDeg: Float = 0f
    private var gyroArcGnssDeg: Float = 0f
    private var gyroArcLastGnssCourse: Float? = null

    // Phone compass state
    private val magHistory = ArrayDeque<Float>()
    private var prevFieldStrengthUt: Float? = null
    private var prevRawAzimuthDeg: Float? = null
    private var lastMagAzimuthDeg: Float? = null
    private var lastCalibratedCompassDeg: Float? = null
    private var lastFieldStrengthUt: Float? = null
    private var lastMagAccuracy: Int? = null
    private var lastMagAtElapsedMs: Long = 0L
    private var lastMagRejectReason: String? = null
    private var lastDeclinationDeg: Float = 0f

    // Session-scoped mount offset (reset when phone orientation in holder changes)
    private var mountYawOffsetDeg: Float? = null
    private var mountOffsetConfirmed: Boolean = false
    private var mountCalibDistanceM: Double = 0.0
    private var mountCalibSinSum: Double = 0.0
    private var mountCalibCosSum: Double = 0.0
    private var mountCalibSamples: Int = 0
    private var lastMountCalibElapsedMs: Long = 0L

    // Clock offset sliding window
    private val monoOffsetSamples = ArrayDeque<Long>()
    private var monoClockOffsetMs: Long? = null

    fun setPreferredPhoneIdHex(idHex: String?) {
        synchronized(lock) {
            preferredPhoneIdHex = idHex?.trim()?.lowercase()?.ifEmpty { null }
            val pref = preferredPhoneIdHex
            // The preferred phone becomes active on its first accepted packet, not here:
            // pinning an offline phone would block every other phone.
            if (pref != null && activePhoneIdHex != null && activePhoneIdHex != pref) {
                releaseActivePhoneLocked("preferred_phone_changed")
            }
            publishUiStateLocked()
        }
    }

    fun onPhoneDisconnected(idHex: String) {
        synchronized(lock) {
            if (activePhoneIdHex == idHex) {
                releaseActivePhoneLocked("phone_disconnected")
                publishUiStateLocked()
            }
        }
    }

    private fun releaseActivePhoneLocked(reason: String) {
        activePhoneIdHex = null
        activePhoneName = null
        lastActivePhonePacketAtElapsedMs = 0L
        pendingYawNavDeltaDeg = 0.0
        resetMountOffsetLocked(reason)
    }

    fun resetSessionMountOffset() {
        synchronized(lock) {
            resetMountOffsetLocked("manual_reset")
            publishUiStateLocked()
        }
    }

    /**
     * Computes the `telemetryDemand` bitmask for `TELEMETRY_ACK` so the phone only activates
     * the sensors currently needed by the head unit.
     */
    fun computeTelemetryDemandMask(
        locationSource: LocationSource,
        headingSource: MockHeadingSource,
        geoDebugRecording: Boolean,
    ): Int {
        var mask = PhoneBleCodec.DEMAND_NONE
        if (locationSource == LocationSource.PHONE || geoDebugRecording) {
            mask = mask or PhoneBleCodec.DEMAND_LOC
        }
        if (headingSource.usesPhoneGyro || geoDebugRecording) {
            mask = mask or PhoneBleCodec.DEMAND_YAW
        }
        if (headingSource.usesPhoneCompass || geoDebugRecording) {
            mask = mask or PhoneBleCodec.DEMAND_MAG
        }
        return mask
    }

    private fun acceptsPhoneLocked(idHex: String, name: String?, nowElapsedMs: Long): Boolean {
        val pref = preferredPhoneIdHex
        if (pref != null && idHex != pref) return false
        val active = activePhoneIdHex
        if (active != null && active != idHex) {
            // Auto: a phone that went silent without a clean disconnect must not block others.
            val silentMs = nowElapsedMs - lastActivePhonePacketAtElapsedMs
            if (pref != null || lastActivePhonePacketAtElapsedMs <= 0L || silentMs > ACTIVE_PHONE_SILENT_TAKEOVER_MS) {
                releaseActivePhoneLocked("active_phone_switched")
            } else {
                return false
            }
        }
        activePhoneIdHex = idHex
        lastActivePhonePacketAtElapsedMs = nowElapsedMs
        if (!name.isNullOrBlank()) activePhoneName = name
        return true
    }

    fun onPhoneLocation(
        idHex: String,
        name: String?,
        loc: PhoneBleCodec.PhoneLocation,
        activeLocationSource: LocationSource,
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
        nowEpochMs: Long = System.currentTimeMillis(),
    ) {
        var toPublish: LocValues? = null
        synchronized(lock) {
            if (!acceptsPhoneLocked(idHex, name, nowElapsedMs)) return
            lastLocation = loc
            lastLocAtElapsedMs = nowElapsedMs
            val fixEpochMs = (nowEpochMs - loc.fixAgeMs.toLong().coerceAtLeast(0L)).coerceAtLeast(0L)
            val hAcc = loc.horizontalAccuracyM?.takeIf { it.isFinite() && it > 0f }
            val hdop = hAcc?.let { (it / 4.5f).coerceIn(0.5f, 50f) } ?: 1.0f
            val locValues = LocValues(
                rawValue = "PHONE:$idHex#${loc.gen}",
                updateTime = Date(fixEpochMs),
                locateStatus = loc.locateStatus,
                latitude = loc.latitude,
                longitude = loc.longitude,
                altitude = (loc.altitudeM ?: 0f).toDouble(),
                speed = loc.speedKmh.coerceAtLeast(0f),
                trueDirection = loc.bearingDeg ?: 0f,
                magneticDirection = 0f,
                visibleSatellites = loc.visibleSatellites,
                usingSatellites = loc.usedSatellites,
                hdop = hdop,
                hrms = hAcc,
            )
            lastLocValues = locValues
            if (loc.locateStatus && loc.latitude in -90.0..90.0 && loc.longitude in -180.0..180.0 &&
                (loc.latitude != 0.0 || loc.longitude != 0.0)
            ) {
                updateDeclinationLocked(loc.latitude, loc.longitude, loc.altitudeM ?: 0f, nowEpochMs)
            }
            if (activeLocationSource == LocationSource.PHONE) {
                toPublish = locValues
            }
            publishUiStateLocked()
        }
        LocationIncomingBitRate.noteBytes(LocationSource.PHONE, 48, nowElapsedMs)
        toPublish?.let { values ->
            TboxRepository.updateLocationUpdateTime()
            TboxRepository.updateLocValues(values)
        }
    }

    fun onPhoneYaw(
        idHex: String,
        name: String?,
        sample: PhoneBleCodec.PhoneYawSample,
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
    ) {
        synchronized(lock) {
            if (!acceptsPhoneLocked(idHex, name, nowElapsedMs)) return
            updateClockOffsetLocked(nowElapsedMs, sample.monoLowMs)
            lastYawAtElapsedMs = nowElapsedMs
            if (sample.mountMoved) {
                resetMountOffsetLocked("mount_moved")
            }
            if (!sample.mountStable || sample.mountMoved) {
                lastYawRejectReason = if (sample.mountMoved) "mount_moved" else "mount_unstable"
                lastYawRateDegPerSec = null
                publishUiStateLocked()
                return
            }
            val dtSec = (sample.dtMs.coerceIn(10, 1_000)) / 1000f
            val rawRate = sample.dPsiDeg / dtSec
            if (!rawRate.isFinite() || abs(rawRate) > YawIntegrator.MAX_ABS_YAW_RATE_DEG_PER_SEC) {
                lastYawRejectReason = "absurd_rate"
                lastYawRateDegPerSec = null
                publishUiStateLocked()
                return
            }
            val calib = PhoneGyroCalibrationStore.calibration.value
            val debiasedRate = rawRate - calib.biasDegPerSec
            if (abs(debiasedRate) < YawIntegrator.YAW_DEADBAND_DEG_PER_SEC) {
                lastYawRateDegPerSec = 0f
                lastYawRejectReason = null
                publishUiStateLocked()
                return
            }
            val scale = if (debiasedRate >= 0f) calib.scaleLeft else calib.scaleRight
            val calibratedRate = debiasedRate * scale * calib.sign
            val calibratedDPsi = calibratedRate * dtSec
            // Nav bearing convention: left turn (+) decreases bearing
            pendingYawNavDeltaDeg += (-calibratedDPsi).toDouble()
            gyroArcPhoneDeg += (-debiasedRate * dtSec * calib.sign)
            lastYawRateDegPerSec = calibratedRate
            lastYawRejectReason = null
            publishUiStateLocked()
        }
    }

    fun onPhoneMag(
        idHex: String,
        name: String?,
        sample: PhoneBleCodec.PhoneMagSample,
        steerDeg: Float?,
        wheelDeltaDeg: Float,
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
    ) {
        synchronized(lock) {
            if (!acceptsPhoneLocked(idHex, name, nowElapsedMs)) return
            updateClockOffsetLocked(nowElapsedMs, sample.monoLowMs)
            lastMagAtElapsedMs = nowElapsedMs
            lastMagAzimuthDeg = sample.magneticAzimuthDeg
            lastFieldStrengthUt = sample.fieldStrengthUt
            lastMagAccuracy = sample.accuracy

            if (sample.mountMoved) {
                resetMountOffsetLocked("mount_moved")
                lastMagRejectReason = "mount_moved"
                lastCalibratedCompassDeg = null
                publishUiStateLocked()
                return
            }
            if (sample.accuracy < 2) {
                lastMagRejectReason = "low_accuracy(${sample.accuracy})"
                lastCalibratedCompassDeg = null
                publishUiStateLocked()
                return
            }
            if (!sample.fieldStrengthUt.isFinite() ||
                sample.fieldStrengthUt !in PhoneSensorMath.MIN_VALID_MAG_FIELD_UT..PhoneSensorMath.MAX_VALID_MAG_FIELD_UT
            ) {
                lastMagRejectReason = "field_range(${sample.fieldStrengthUt.roundToInt()}uT)"
                lastCalibratedCompassDeg = null
                publishUiStateLocked()
                return
            }
            val prevB = prevFieldStrengthUt
            prevFieldStrengthUt = sample.fieldStrengthUt
            if (prevB != null && abs(sample.fieldStrengthUt - prevB) > MAX_MAG_FIELD_JUMP_UT) {
                lastMagRejectReason = "field_jump"
                lastCalibratedCompassDeg = null
                publishUiStateLocked()
                return
            }
            val prevAz = prevRawAzimuthDeg
            prevRawAzimuthDeg = sample.magneticAzimuthDeg
            if (prevAz != null) {
                val azJump = abs(DriveCalibrationMath.wrapDeltaDeg(prevAz, sample.magneticAzimuthDeg))
                val vehicleTurning = (steerDeg != null && abs(steerDeg) > 25f) || abs(wheelDeltaDeg) > 6f
                if (azJump > MAX_UNEXPLAINED_AZIMUTH_JUMP_DEG && !vehicleTurning) {
                    lastMagRejectReason = "azimuth_jump"
                    lastCalibratedCompassDeg = null
                    publishUiStateLocked()
                    return
                }
            }

            magHistory.addLast(sample.magneticAzimuthDeg)
            while (magHistory.size > COMPASS_MEDIAN_WINDOW) {
                magHistory.removeFirst()
            }
            val smoothedMagAz = circularMedianDeg(magHistory.toList())
            val mountOff = mountYawOffsetDeg
            if (mountOff == null) {
                lastMagRejectReason = "uncalibrated_mount"
                lastCalibratedCompassDeg = null
                publishUiStateLocked()
                return
            }
            val baseTrue = MockLocationJob.wrapBearingDeg(smoothedMagAz + lastDeclinationDeg + mountOff)
            val sectorIdx = ((baseTrue / 45f).toInt()) and 7
            val sectorCorr = if (mountOffsetConfirmed) {
                PhoneCompassCalibrationStore.calibration.value.sectorOffsetsDeg.getOrElse(sectorIdx) { 0f }
            } else {
                0f
            }
            lastCalibratedCompassDeg = MockLocationJob.wrapBearingDeg(baseTrue + sectorCorr)
            lastMagRejectReason = null
            publishUiStateLocked()
        }
    }

    /**
     * Feeds truthful forward GNSS course to calibrate:
     * 1. Session `mountYawOffsetDeg` (after 50–100 m straight forward driving, with immediate
     *    provisional seed on the first straight forward GNSS sample).
     * 2. 8-sector hard-iron residual table (`PhoneCompassCalibrationStore`).
     * 3. Online phone gyro scale (`PhoneGyroCalibrationStore`).
     *
     * Never calibrates in reverse (`!reverse` gate).
     */
    fun onGnssReference(
        gnssCourseDeg: Float?,
        lat: Double,
        lon: Double,
        altM: Float,
        speedKmh: Float?,
        steerDeg: Float?,
        reverse: Boolean,
        gnssTruthful: Boolean,
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Pair<PhoneGyroCalibration?, PhoneCompassCalibration?> {
        var updatedGyro: PhoneGyroCalibration? = null
        var updatedCompass: PhoneCompassCalibration? = null
        synchronized(lock) {
            if (lat in -90.0..90.0 && lon in -180.0..180.0 && (lat != 0.0 || lon != 0.0)) {
                updateDeclinationLocked(lat, lon, altM, nowEpochMs)
            }
            val spd = speedKmh ?: 0f
            if (!gnssTruthful || reverse || !spd.isFinite() || spd < MOUNT_CALIB_MIN_SPEED_KMH ||
                gnssCourseDeg == null || !gnssCourseDeg.isFinite() || gnssCourseDeg == 0f
            ) {
                lastMountCalibElapsedMs = 0L
                gyroArcLastGnssCourse = null
                gyroArcPhoneDeg = 0f
                gyroArcGnssDeg = 0f
                return null to null
            }

            // 1. Online phone gyro arc scale calibration (forward turns)
            if (nowElapsedMs - lastYawAtElapsedMs <= MAX_YAW_AGE_MS && lastYawRejectReason == null) {
                val prevG = gyroArcLastGnssCourse
                gyroArcLastGnssCourse = gnssCourseDeg
                if (prevG != null) {
                    val stepG = DriveCalibrationMath.wrapDeltaDeg(prevG, gnssCourseDeg)
                    if (abs(stepG) <= 45f) {
                        gyroArcGnssDeg += stepG
                        if (abs(gyroArcGnssDeg) >= 22f && abs(gyroArcPhoneDeg) >= 12f &&
                            gyroArcGnssDeg * gyroArcPhoneDeg > 0f
                        ) {
                            val ratio = (gyroArcGnssDeg / gyroArcPhoneDeg).coerceIn(
                                PhoneGyroCalibrationStore.MIN_SCALE,
                                PhoneGyroCalibrationStore.MAX_SCALE,
                            )
                            val cur = PhoneGyroCalibrationStore.calibration.value
                            val leftTurn = gyroArcGnssDeg < 0f
                            val next = if (leftTurn) {
                                cur.copy(
                                    scaleLeft = cur.scaleLeft * 0.75f + ratio * 0.25f,
                                    calibratedAtEpochMs = nowEpochMs,
                                )
                            } else {
                                cur.copy(
                                    scaleRight = cur.scaleRight * 0.75f + ratio * 0.25f,
                                    calibratedAtEpochMs = nowEpochMs,
                                )
                            }
                            PhoneGyroCalibrationStore.update(next)
                            updatedGyro = PhoneGyroCalibrationStore.calibration.value
                            gyroArcPhoneDeg = 0f
                            gyroArcGnssDeg = 0f
                        }
                    } else {
                        gyroArcPhoneDeg = 0f
                        gyroArcGnssDeg = 0f
                    }
                }
            }

            // 2. Straight-line session mountYawOffset & sector calibration for phone compass
            val straightSteer = steerDeg == null || !steerDeg.isFinite() || abs(steerDeg) <= MOUNT_CALIB_MAX_STEER_DEG
            val quietYaw = lastYawRateDegPerSec == null || abs(lastYawRateDegPerSec!!) <= MOUNT_CALIB_MAX_YAW_DEG_PER_SEC
            val magFresh = lastMagAtElapsedMs > 0L && nowElapsedMs - lastMagAtElapsedMs <= MAX_MAG_AGE_MS
            val rawMag = if (magHistory.isNotEmpty()) circularMedianDeg(magHistory.toList()) else lastMagAzimuthDeg
            if (straightSteer && quietYaw && magFresh && rawMag != null &&
                (lastMagRejectReason == null || lastMagRejectReason == "uncalibrated_mount")
            ) {
                val sampleOffset = DriveCalibrationMath.wrapDeltaDeg(
                    MockLocationJob.wrapBearingDeg(rawMag + lastDeclinationDeg),
                    gnssCourseDeg,
                )
                // Provisional seed on first straight forward sample so compass works immediately
                if (mountYawOffsetDeg == null) {
                    mountYawOffsetDeg = sampleOffset
                    lastMagRejectReason = null
                }
                val dtSec = if (lastMountCalibElapsedMs > 0L) {
                    ((nowElapsedMs - lastMountCalibElapsedMs) / 1000.0).coerceIn(0.0, 2.0)
                } else {
                    0.5
                }
                lastMountCalibElapsedMs = nowElapsedMs
                val stepM = (spd / 3.6) * dtSec
                val rad = Math.toRadians(sampleOffset.toDouble())
                mountCalibSinSum += sin(rad) * stepM
                mountCalibCosSum += cos(rad) * stepM
                mountCalibDistanceM += stepM
                mountCalibSamples++

                val meanOffset = Math.toDegrees(atan2(mountCalibSinSum, mountCalibCosSum)).toFloat()
                mountYawOffsetDeg = meanOffset
                if (mountCalibDistanceM >= MOUNT_CALIB_CONFIRM_DISTANCE_M) {
                    mountOffsetConfirmed = true
                    // Update 8-sector residual table
                    val baseTrue = MockLocationJob.wrapBearingDeg(rawMag + lastDeclinationDeg + meanOffset)
                    val residual = DriveCalibrationMath.wrapDeltaDeg(baseTrue, gnssCourseDeg)
                    if (abs(residual) <= PhoneCompassCalibrationStore.MAX_SECTOR_OFFSET_DEG) {
                        val sectorIdx = ((gnssCourseDeg / 45f).toInt()) and 7
                        val cur = PhoneCompassCalibrationStore.calibration.value
                        val mutable = cur.sectorOffsetsDeg.toMutableList()
                        val prevSec = mutable.getOrElse(sectorIdx) { 0f }
                        mutable[sectorIdx] = (prevSec * 0.8f + residual * 0.2f)
                            .coerceIn(
                                -PhoneCompassCalibrationStore.MAX_SECTOR_OFFSET_DEG,
                                PhoneCompassCalibrationStore.MAX_SECTOR_OFFSET_DEG,
                            )
                        val nextCompass = PhoneCompassCalibration(
                            sectorOffsetsDeg = mutable,
                            calibratedAtEpochMs = nowEpochMs,
                        )
                        PhoneCompassCalibrationStore.update(nextCompass)
                        updatedCompass = PhoneCompassCalibrationStore.calibration.value
                    }
                }
                publishUiStateLocked()
            } else {
                lastMountCalibElapsedMs = 0L
            }
        }
        return updatedGyro to updatedCompass
    }

    fun isGyroFresh(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Boolean = synchronized(lock) {
        nowElapsedMs > 0L &&
            lastYawAtElapsedMs > 0L &&
            nowElapsedMs - lastYawAtElapsedMs <= MAX_YAW_AGE_MS &&
            lastYawRejectReason == null
    }

    fun consumeGyroNavDeltaDeg(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Float = synchronized(lock) {
        if (!isGyroFresh(nowElapsedMs)) {
            pendingYawNavDeltaDeg = 0.0
            return 0f
        }
        val out = pendingYawNavDeltaDeg.toFloat()
        pendingYawNavDeltaDeg = 0.0
        out
    }

    fun discardGyro() {
        synchronized(lock) {
            pendingYawNavDeltaDeg = 0.0
        }
    }

    fun isCompassUsable(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Boolean = synchronized(lock) {
        nowElapsedMs > 0L &&
            lastMagAtElapsedMs > 0L &&
            nowElapsedMs - lastMagAtElapsedMs <= MAX_MAG_AGE_MS &&
            lastMagRejectReason == null &&
            lastCalibratedCompassDeg != null
    }

    fun calibratedCompassHeadingDeg(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Float? = synchronized(lock) {
        if (!isCompassUsable(nowElapsedMs)) return null
        lastCalibratedCompassDeg
    }

    fun peekLastLocationValues(): Pair<LocValues, Long>? = synchronized(lock) {
        val v = lastLocValues ?: return null
        if (lastLocAtElapsedMs <= 0L) return null
        v to lastLocAtElapsedMs
    }

    internal fun resetForTest() {
        synchronized(lock) {
            preferredPhoneIdHex = null
            activePhoneIdHex = null
            activePhoneName = null
            lastActivePhonePacketAtElapsedMs = 0L
            lastLocation = null
            lastLocAtElapsedMs = 0L
            lastLocValues = null
            pendingYawNavDeltaDeg = 0.0
            lastYawRateDegPerSec = null
            lastYawAtElapsedMs = 0L
            lastYawRejectReason = null
            gyroArcPhoneDeg = 0f
            gyroArcGnssDeg = 0f
            gyroArcLastGnssCourse = null
            magHistory.clear()
            prevFieldStrengthUt = null
            prevRawAzimuthDeg = null
            lastMagAzimuthDeg = null
            lastCalibratedCompassDeg = null
            lastFieldStrengthUt = null
            lastMagAccuracy = null
            lastMagAtElapsedMs = 0L
            lastMagRejectReason = null
            lastDeclinationDeg = 0f
            resetMountOffsetLocked(null)
            monoOffsetSamples.clear()
            monoClockOffsetMs = null
            publishUiStateLocked()
        }
    }

    private fun resetMountOffsetLocked(reason: String?) {
        mountYawOffsetDeg = null
        mountOffsetConfirmed = false
        mountCalibDistanceM = 0.0
        mountCalibSinSum = 0.0
        mountCalibCosSum = 0.0
        mountCalibSamples = 0
        lastMountCalibElapsedMs = 0L
        lastCalibratedCompassDeg = null
        if (reason != null) {
            lastMagRejectReason = reason
        }
    }

    private fun updateDeclinationLocked(lat: Double, lon: Double, altM: Float, nowEpochMs: Long) {
        runCatching {
            val field = GeomagneticField(lat.toFloat(), lon.toFloat(), altM, nowEpochMs)
            if (field.declination.isFinite()) {
                lastDeclinationDeg = field.declination
            }
        }
    }

    private fun updateClockOffsetLocked(huElapsedMs: Long, phoneMonoLowMs: Int) {
        val low = (phoneMonoLowMs and 0xFFFF).toLong()
        val huLow = huElapsedMs and 0xFFFFL
        var diff = huLow - low
        if (diff < -32768L) diff += 65536L
        if (diff > 32767L) diff -= 65536L
        monoOffsetSamples.addLast(diff)
        while (monoOffsetSamples.size > 16) {
            monoOffsetSamples.removeFirst()
        }
        monoClockOffsetMs = monoOffsetSamples.minOrNull()
    }

    private fun publishUiStateLocked() {
        _uiState.value = PhoneTelemetryUiState(
            preferredPhoneIdHex = preferredPhoneIdHex,
            activePhoneIdHex = activePhoneIdHex,
            activePhoneName = activePhoneName,
            lastLocation = lastLocation,
            lastLocAtElapsedMs = lastLocAtElapsedMs,
            lastYawRateDegPerSec = lastYawRateDegPerSec,
            lastYawAtElapsedMs = lastYawAtElapsedMs,
            lastYawRejectReason = lastYawRejectReason,
            lastMagAzimuthDeg = lastMagAzimuthDeg,
            lastCalibratedCompassDeg = lastCalibratedCompassDeg,
            lastFieldStrengthUt = lastFieldStrengthUt,
            lastMagAccuracy = lastMagAccuracy,
            lastMagAtElapsedMs = lastMagAtElapsedMs,
            lastMagRejectReason = lastMagRejectReason,
            mountYawOffsetDeg = mountYawOffsetDeg,
            mountOffsetConfirmed = mountOffsetConfirmed,
            monoClockOffsetMs = monoClockOffsetMs,
        )
    }

    internal fun circularMedianDeg(angles: List<Float>): Float {
        if (angles.isEmpty()) return 0f
        if (angles.size == 1) return MockLocationJob.wrapBearingDeg(angles[0])
        var best = angles[0]
        var bestCost = Float.MAX_VALUE
        for (candidate in angles) {
            var cost = 0f
            for (other in angles) {
                cost += abs(DriveCalibrationMath.wrapDeltaDeg(candidate, other))
            }
            if (cost < bestCost) {
                bestCost = cost
                best = candidate
            }
        }
        return MockLocationJob.wrapBearingDeg(best)
    }
}
