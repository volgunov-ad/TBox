package vad.dashing.tbox.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import vad.dashing.tbox.BuildConfig
import vad.dashing.tbox.CanDataRepository
import vad.dashing.tbox.R
import vad.dashing.tbox.TboxRepository
import vad.dashing.tbox.TripTelemetryRepository
import vad.dashing.tbox.drsensor.DrSensorRepository
import vad.dashing.tbox.esp.LocationSource
import vad.dashing.tbox.location.roadmatch.formatRankedCandidatesLog
import vad.dashing.tbox.mbcan.MbCanSignal
import vad.dashing.tbox.mbcan.UniversalCanRepository
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Geo / mock / IMU debug log to Downloads (buffered append).
 * Tick period matches the DR+match inner loop ([TICK_MS] = [MockLocationJob.INNER_CALC_MS]).
 * Max duration [MAX_DURATION_MS]; flush when buffer ≥ [FLUSH_BYTES] or on stop.
 */
object GeoDebugLogRecorder {
    const val MAX_DURATION_MS = 20L * 60L * 1_000L
    const val TICK_MS = MockLocationJob.INNER_CALC_MS
    const val FLUSH_BYTES = 24 * 1024
    private const val STEERING_INTEREST_SOURCE_ID = "geo-debug-steering"

    data class UiState(
        val recording: Boolean = false,
        val filePath: String? = null,
        val startedAtWallMs: Long = 0L,
        val ticks: Int = 0,
        val lastError: String? = null,
        val autoStopped: Boolean = false,
    )

    data class Deps(
        val locationSource: () -> LocationSource,
        val mockEnabled: () -> Boolean,
        /** Effective DR mode ([MockPowerState.effectiveCanSpeedMode]), not stored Direct under WHEN_NO_FIX. */
        val mockMode: () -> MockCanSpeedMode,
        val mockPower: () -> MockPowerState = { MockPowerState.OFF },
        val headingSource: () -> MockHeadingSource = { MockHeadingSource.GYRO },
        val considerReverse: () -> Boolean = { true },
    )

    private val _ui = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _ui.asStateFlow()

    private var deps: Deps? = null
    private var appContext: Context? = null
    private var scope: CoroutineScope? = null
    private var job: Job? = null
    private var sampleJobs: List<Job> = emptyList()
    private val writeMutex = Mutex()
    private val pending = StringBuilder(FLUSH_BYTES + 4_096)
    private var outFile: File? = null
    private var startedElapsedMs: Long = 0L
    private val integrals = GeoDebugIntegralAccumulator()
    private var cachedTruth: GeoDebugHiddenTruth.Fix? = null
    private var cachedTruthAtElapsedMs: Long? = null

    fun attach(context: Context, scope: CoroutineScope, deps: Deps) {
        this.appContext = context.applicationContext
        this.scope = scope
        this.deps = deps
    }

    fun isRecording(): Boolean = _ui.value.recording

    fun start() {
        if (_ui.value.recording) return
        val ctx = appContext ?: return
        val sc = scope ?: return
        val file = createLogFile(ctx) ?: run {
            _ui.value = UiState(lastError = "cannot create file")
            return
        }
        outFile = file
        pending.clear()
        GeoDebugNmeaBuffer.clear()
        integrals.reset()
        cachedTruth = null
        cachedTruthAtElapsedMs = null
        startedElapsedMs = SystemClock.elapsedRealtime()
        _ui.value = UiState(
            recording = true,
            filePath = file.absolutePath,
            startedAtWallMs = System.currentTimeMillis(),
            ticks = 0,
        )
        val mapsLabel = GeoDebugSessionHeader.installedMapsLabel(
            File(ctx.filesDir, "road_maps"),
        )
        appendUnlocked(
            "# tbox geo debug log\n" +
                "# started=${formatWall(System.currentTimeMillis())}\n" +
                GeoDebugSessionHeader.commentLines(
                    appVer = BuildConfig.VERSION_NAME,
                    mapsLabel = mapsLabel,
                    matchPeriodMs = MockLocationJob.INNER_CALC_MS,
                    logPeriodMs = TICK_MS,
                ) +
                "# maxDurationMin=${MAX_DURATION_MS / 60_000L}\n" +
                "# integ=session raw CAN dist + gyro yaw/pitch/roll + steer unit-path " +
                "(independent of mock DR integrators)\n\n",
        )
        sc.launch {
            runCatching {
                UniversalCanRepository.setSourceSignals(
                    STEERING_INTEREST_SOURCE_ID,
                    setOf(MbCanSignal.SteeringAngle, MbCanSignal.TurnSignals),
                )
            }.onFailure {
                TboxRepository.addLog(
                    "WARN",
                    "GeoDebug",
                    "steering interest: ${it.message}",
                )
            }
        }
        startSampleCollectors(sc)
        sc.launch(Dispatchers.IO) { flushPending() }
        job?.cancel()
        job = sc.launch {
            while (isActive) {
                val elapsed = SystemClock.elapsedRealtime() - startedElapsedMs
                if (elapsed >= MAX_DURATION_MS) {
                    stop(auto = true)
                    break
                }
                val block = buildTickBlock()
                writeMutex.withLock {
                    pending.append(block)
                    if (pending.length >= FLUSH_BYTES) {
                        flushPendingLocked()
                    }
                }
                _ui.value = _ui.value.copy(ticks = _ui.value.ticks + 1)
                delay(TICK_MS)
            }
        }
        TboxRepository.addLog("INFO", "GeoDebug", "recording started: ${file.name}")
    }

    fun stop(auto: Boolean = false) {
        val was = _ui.value.recording
        job?.cancel()
        job = null
        stopSampleCollectors()
        UniversalCanRepository.enqueueClearSource(STEERING_INTEREST_SOURCE_ID)
        if (!was && outFile == null) return
        val sc = scope
        val path = outFile?.absolutePath
        if (sc != null) {
            sc.launch(Dispatchers.IO) {
                writeMutex.withLock {
                    pending.append(
                        "\n# stopped=${formatWall(System.currentTimeMillis())}" +
                            " auto=$auto ticks=${_ui.value.ticks}\n",
                    )
                    flushPendingLocked()
                }
                val ctx = appContext
                if (ctx != null && path != null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            ctx,
                            ctx.getString(R.string.toast_saved_to, path),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
        _ui.value = _ui.value.copy(
            recording = false,
            autoStopped = auto,
            filePath = path,
        )
        outFile = null
        TboxRepository.addLog(
            "INFO",
            "GeoDebug",
            if (auto) "recording auto-stopped (20 min): $path" else "recording stopped: $path",
        )
    }

    private fun startSampleCollectors(sc: CoroutineScope) {
        stopSampleCollectors()
        val jobs = mutableListOf<Job>()
        jobs += sc.launch {
            DrSensorRepository.snapshot.collect { snap ->
                if (!_ui.value.recording) return@collect
                integrals.onGyro(
                    yawRaw = snap.gyroYaw,
                    pitch = snap.gyroPitch,
                    roll = snap.gyroRoll,
                    elapsedMs = snap.lastUpdateElapsedMs.takeIf { it > 0L }
                        ?: SystemClock.elapsedRealtime(),
                )
            }
        }
        jobs += sc.launch {
            TripTelemetryRepository.carSpeed.collect { speed ->
                if (!_ui.value.recording) return@collect
                val now = SystemClock.elapsedRealtime()
                integrals.onSpeedKmh(speed, now)
            }
        }
        jobs += sc.launch {
            UniversalCanRepository.steerAngleState.collect { angle ->
                if (!_ui.value.recording) return@collect
                integrals.onSteerAngle(angle, SystemClock.elapsedRealtime())
            }
        }
        sampleJobs = jobs
    }

    private fun stopSampleCollectors() {
        sampleJobs.forEach { it.cancel() }
        sampleJobs = emptyList()
    }

    private fun createLogFile(context: Context): File? {
        return try {
            val savePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
            } else {
                Environment.getExternalStorageDirectory().absolutePath + "/Download"
            }
            val dir = File(savePath)
            if (!dir.exists()) dir.mkdirs()
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            File(dir, "tbox_geo_debug_$stamp.txt").also { f ->
                FileOutputStream(f, false).use { /* create empty */ }
            }
        } catch (e: Exception) {
            TboxRepository.addLog("ERROR", "GeoDebug", "create file: ${e.message}")
            null
        }
    }

    private fun appendUnlocked(text: String) {
        pending.append(text)
    }

    private suspend fun flushPending() {
        writeMutex.withLock { flushPendingLocked() }
    }

    private fun flushPendingLocked() {
        if (pending.isEmpty()) return
        val file = outFile ?: return
        val chunk = pending.toString()
        pending.clear()
        try {
            FileOutputStream(file, true).use { fos ->
                fos.write(chunk.toByteArray(StandardCharsets.UTF_8))
            }
        } catch (e: Exception) {
            TboxRepository.addLog("ERROR", "GeoDebug", "flush: ${e.message}")
            _ui.value = _ui.value.copy(lastError = e.message)
        }
    }

    private fun buildTickBlock(): String {
        val d = deps
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val live = TboxRepository.locValues.value
        val geo = GeoDisplayRepository.state.value
        val canAcct = TripTelemetryRepository.accountingCarSpeed(nowElapsed)
        val canHu = UniversalCanRepository.carSpeedState.value
        val canFlow = TripTelemetryRepository.carSpeed.value
        val dr = DrSensorRepository.snapshot.value
        val bias = GyroBiasStore.offsets
        val drive = DriveCalibrationStore.offsets
        val source = d?.locationSource?.invoke() ?: LocationSource.TBOX
        val mockOn = d?.mockEnabled?.invoke() == true
        val mockMode = d?.mockMode?.invoke() ?: MockCanSpeedMode.NONE
        val mockPower = d?.mockPower?.invoke() ?: MockPowerState.OFF
        val headingSrc = d?.headingSource?.invoke() ?: MockHeadingSource.GYRO
        val considerReverse = d?.considerReverse?.invoke() == true
        val huSwitch = UniversalCanRepository.reverseGearSwitchState.value
        val huPrnd = UniversalCanRepository.gearBoxModeState.value
        val turnSignals = UniversalCanRepository.turnSignalsState.value
        val turnSide = vad.dashing.tbox.mbcan.TurnSignalsDomain.effectiveSide(turnSignals)
        val turnLatched = vad.dashing.tbox.mbcan.UniversalCanRepository.latchedTurnSignalSide()
        val steeringAngle = UniversalCanRepository.steerAngleState.value
        val huCanMode = UniversalCanRepository.mode.value
        val tboxPrnd = CanDataRepository.gearBoxMode.value
        val bps = LocationIncomingBitRate.bitsPerSec(source, nowElapsed)
        val nmea = GeoDebugNmeaBuffer.drainSinceLastTick()
        val yawRaw = dr.gyroYaw
        val yawDebiased = GyroBiasStore.applyYaw(yawRaw)
        val yawCal = yawDebiased?.let { DriveCalibrationStore.applyYawRate(it) }

        val sb = StringBuilder(2_048)
        sb.append("--- ").append(formatWall(nowWall))
            .append(" elapsedMs=").append(nowElapsed).append(" ---\n")
        sb.append("source=").append(source.name)
            .append(" mockOn=").append(mockOn)
            .append(" mockPower=").append(mockPower.name)
            .append(" mockMode=").append(mockMode.name)
            .append(" headingSrc=").append(headingSrc.name)
            .append(" simulatedLoss=").append(SimulatedLocationSourceLoss.enabled.value)
            .append(" bitrate_bps=").append(bps ?: "-")
            .append('\n')
        sb.append("gnss.fix=").append(live.locateStatus)
            .append(" truth=").append(TboxRepository.isLocValuesTrue.value)
            .append(" displayTruth=").append(geo.isTruthful)
            .append(" lat=").append(live.latitude)
            .append(" lon=").append(live.longitude)
            .append(" alt=").append(live.altitude)
            .append(" speedKmh=").append(live.speed)
            .append(" course=").append(live.trueDirection)
            .append(" mag=").append(live.magneticDirection)
            .append(" sats=").append(live.visibleSatellites).append('/').append(live.usingSatellites)
            .append(" hdop=").append(live.hdop ?: "-")
            .append(" pdop=").append(live.pdop ?: "-")
            .append(" vdop=").append(live.vdop ?: "-")
            .append(" hrms=").append(live.hrms ?: "-")
            .append(" vrms=").append(live.vrms ?: "-")
            .append(" fixQ=").append(live.fixQuality ?: "-")
            .append(" diffAge=").append(live.diffAgeSec ?: "-")
            .append(" accuracyM=").append(
                LocationMockManager.horizontalAccuracyMeters(
                    hdop = live.hdop,
                    retainingFix = false,
                    hrms = live.hrms,
                ),
            )
            .append('\n')
        sb.append("gnss.raw=").append(sanitizeOneLine(live.rawValue)).append('\n')
        appendHiddenTruth(sb, nmea, live, source, nowElapsed, nowWall)
        sb.append("can.accountingKmh=").append(canAcct ?: "-")
            .append(" can.huKmh=").append(canHu ?: "-")
            .append(" can.telemetryKmh=").append(canFlow ?: "-")
            .append('\n')
        sb.append("steering.angleDeg=").append(steeringAngle ?: "-")
            .append(" backend=").append(huCanMode.name)
            .append('\n')
        val prevInteg = integrals.previousSnapshot()
        val integ = integrals.snapshotForLog(nowElapsed)
        sb.append("integ.distM=").append(fmt(integ.distM))
            .append(" dDistM=").append(fmt(integ.distM - prevInteg.distM))
            .append(" yawRawDeg=").append(fmt(integ.yawRawDeg))
            .append(" dYawRawDeg=").append(fmt(integ.yawRawDeg - prevInteg.yawRawDeg))
            .append(" yawDebDeg=").append(fmt(integ.yawDebDeg))
            .append(" dYawDebDeg=").append(fmt(integ.yawDebDeg - prevInteg.yawDebDeg))
            .append(" pitchDeg=").append(fmt(integ.pitchDeg))
            .append(" dPitchDeg=").append(fmt(integ.pitchDeg - prevInteg.pitchDeg))
            .append(" rollDeg=").append(fmt(integ.rollDeg))
            .append(" dRollDeg=").append(fmt(integ.rollDeg - prevInteg.rollDeg))
            .append(" steerPathDeg=").append(fmt(integ.steerPathDeg))
            .append(" dSteerPathDeg=").append(fmt(integ.steerPathDeg - prevInteg.steerPathDeg))
            .append(" nSpeed=").append(integ.speedSamples)
            .append(" nGyro=").append(integ.gyroSamples)
            .append(" nSteer=").append(integ.steerSamples)
            .append('\n')
        sb.append("mock.lat=").append(geo.latitude)
            .append(" lon=").append(geo.longitude)
            .append(" alt=").append(geo.altitude)
            .append(" speedKmh=").append(geo.speedKmh)
            .append(" speedSrc=").append(geo.speedSource)
            .append(" bearing=").append(geo.bearingDeg ?: "-")
            .append(" bearingSrc=").append(geo.bearingSource)
            .append(" liveUsable=").append(geo.liveUsable)
            .append(" retaining=").append(geo.retaining)
            .append(" locate=").append(geo.locateStatus)
            .append(" mockActive=").append(geo.mockActive)
            .append(" indicator=").append(geo.indicator)
            .append('\n')
        val cdr = ConstantDrRuntimeDebug.snapshot
        if (cdr.active || mockMode == MockCanSpeedMode.CONSTANT) {
            sb.append("constant.shadowDistM=").append(cdr.shadowDistM ?: "-")
                .append(" thresholdM=").append(cdr.thresholdM ?: "-")
                .append(" posW=").append(cdr.posW ?: "-")
                .append(" hasOrigin=").append(cdr.constantHasOrigin)
                .append(" blendLive=").append(cdr.blendLive)
                .append(" hardResync=").append(cdr.hardResync)
                .append(" accuracyM=").append(cdr.accuracyM ?: "-")
                .append('\n')
        }
        val mm = vad.dashing.tbox.location.roadmatch.RoadMatchRuntimeDebug.snapshot
        if (mm.active || mm.skippedReason != null || mm.edgeId != null || mm.confidence != null) {
            sb.append("mapMatch.active=").append(mm.active)
                .append(" edgeId=").append(mm.edgeId ?: "-")
                .append(" regionId=").append(mm.regionId ?: "-")
                .append(" crossTrackM=").append(mm.crossTrackM ?: "-")
                .append(" alongTrackM=").append(mm.alongTrackM ?: "-")
                .append(" switchedEdge=").append(mm.switchedEdge)
                .append(" confidence=").append(mm.confidence ?: "-")
                .append(" candidateCount=").append(mm.candidateCount)
                .append(" runnerUpScore=").append(mm.runnerUpScore ?: "-")
                .append(" connected=").append(mm.connected ?: "-")
                .append(" highway=").append(mm.highwayClass ?: "-")
                .append(" oneway=").append(mm.oneway ?: "-")
                .append(" againstOneway=").append(mm.againstOneway ?: "-")
                .append(" candEdgeId=").append(mm.candidateEdgeId ?: "-")
                .append(" candHighway=").append(mm.candidateHighwayClass ?: "-")
                .append(" candConnected=").append(mm.candidateConnected ?: "-")
                .append(" candXtM=").append(mm.candidateCrossTrackM ?: "-")
                .append(" cands=").append(formatRankedCandidatesLog(mm.rankedCandidates))
                .append(" inputBearingDeg=").append(mm.inputBearingDeg ?: "-")
                .append(" edgeBearingDeg=").append(mm.edgeBearingDeg ?: "-")
                .append(" bearingDeltaDeg=").append(mm.bearingDeltaDeg ?: "-")
                .append(" turnActive=").append(mm.turnActive ?: "-")
                .append(" matchLagM=").append(mm.matchLagM ?: "-")
                .append(" turnHint=").append(mm.turnHint ?: "-")
                .append(" leash=").append(mm.leash ?: "-")
                .append(" free=").append(if (mm.freeActive) "1" else "0")
                .append(" freePromote=").append(mm.freePromoted)
                .append(" junction=").append(mm.junction)
                .append(" skippedReason=").append(mm.skippedReason ?: "-")
                .append(" rejectReason=").append(mm.rejectReason ?: "-")
                .append('\n')
            if (mm.preMatchLat != null && mm.preMatchLon != null) {
                sb.append("preMatch.lat=").append(mm.preMatchLat)
                    .append(" preMatch.lon=").append(mm.preMatchLon)
                    .append(" preMatch.bearing=").append(mm.preMatchBearingDeg ?: "-")
                    .append(" preMatch.applied=").append(mm.matchApplied)
                    .append('\n')
            }
            if (mm.freeActive && mm.freeLat != null && mm.freeLon != null) {
                sb.append("free.lat=").append(mm.freeLat)
                    .append(" free.lon=").append(mm.freeLon)
                    .append(" free.bearing=").append(mm.freeBearingDeg ?: "-")
                    .append('\n')
            }
        }
        sb.append("gyro.src=").append(dr.source.name)
            .append(" status=").append(sanitizeOneLine(dr.statusText))
            .append(" yawRaw=").append(yawRaw ?: "-")
            .append(" yawDebiased=").append(yawDebiased ?: "-")
            .append(" yawCal=").append(yawCal ?: "-")
            .append(" pitch=").append(dr.gyroPitch ?: "-")
            .append(" roll=").append(dr.gyroRoll ?: "-")
            .append(" z=").append(dr.gyroRoll ?: "-")
            .append(" temp=").append(dr.gyroTemp ?: "-")
            .append(" accel=")
            .append(dr.accelX ?: "-").append(',')
            .append(dr.accelY ?: "-").append(',')
            .append(dr.accelZ ?: "-")
            .append('\n')
        sb.append("calib.biasYaw=").append(bias.yawDegPerSec)
            .append(" biasTempC=").append(bias.yawCalibTempC ?: "-")
            .append(" drive.speedScale=").append(drive.speedScale)
            .append(" yawScaleL=").append(drive.yawScaleLeft)
            .append(" yawScaleR=").append(drive.yawScaleRight)
            .append(" yawScale=").append(drive.yawScale)
            .append(" yawSign=").append(drive.yawSign)
            .append(" lagMs=").append(drive.lagMs)
            .append(" calibAt=").append(drive.calibratedAtEpochMs)
            .append('\n')
        val online = OnlineYawCalibRuntimeDebug.snapshot
        sb.append("online.phase=").append(online.phase.name)
            .append(" straightHoldMs=").append(online.straightHoldMs)
            .append(" turnGyroAbsDeg=").append(online.turnGyroAbsDeg)
            .append(" lastBiasStep=").append(online.lastBiasStep ?: "-")
            .append(" lastScaleCand=").append(online.lastScaleCandidate ?: "-")
            .append(" lastScaleSide=").append(online.lastScaleSide ?: "-")
            .append('\n')
        sb.append("reverse.consider=").append(considerReverse)
            .append(" huSwitch=").append(huSwitch ?: "-")
            .append(" huPrnd=").append(huPrnd ?: "-")
            .append(" tboxPrnd=").append(tboxPrnd.ifBlank { "-" })
            .append('\n')
        sb.append("turn.left=").append(turnSignals.leftActive ?: "-")
            .append(" turn.right=").append(turnSignals.rightActive ?: "-")
            .append(" turn.hazard=").append(turnSignals.hazardActive ?: "-")
            .append(" turn.side=").append(
                when (turnSide) {
                    vad.dashing.tbox.mbcan.TurnSignalSide.Left -> "L"
                    vad.dashing.tbox.mbcan.TurnSignalSide.Right -> "R"
                    vad.dashing.tbox.mbcan.TurnSignalSide.Hazard -> "H"
                    null -> "-"
                },
            )
            .append(" turn.latched=").append(
                when (turnLatched) {
                    vad.dashing.tbox.mbcan.TurnSignalSide.Left -> "L"
                    vad.dashing.tbox.mbcan.TurnSignalSide.Right -> "R"
                    else -> "-"
                },
            )
            .append('\n')
        if (nmea.isEmpty()) {
            sb.append("nmea.tick=(none)\n")
        } else {
            sb.append("nmea.tick.count=").append(nmea.size).append('\n')
            for (line in nmea) {
                sb.append("nmea|").append(line).append('\n')
            }
        }
        sb.append('\n')
        return sb.toString()
    }

    private fun appendHiddenTruth(
        sb: StringBuilder,
        nmea: List<String>,
        live: vad.dashing.tbox.LocValues,
        source: LocationSource,
        nowElapsed: Long,
        nowWall: Long,
    ) {
        val nmeaFix = GeoDebugHiddenTruth.firstValidRmc(nmea)
        val accM = LocationMockManager.horizontalAccuracyMeters(
            hdop = live.hdop,
            retainingFix = false,
            hrms = live.hrms,
        )
        val locAgeMs = live.updateTime?.time?.let { nowWall - it }?.coerceAtLeast(0L) ?: 0L
        val locFix = GeoDebugHiddenTruth.fromPublished(
            lat = live.latitude,
            lon = live.longitude,
            courseDeg = live.trueDirection,
            src = source.name.lowercase(Locale.US),
            accM = accM,
            ageMs = locAgeMs,
        )
        val lastKnown = androidLastKnown(nowElapsed)
        val selected = GeoDebugHiddenTruth.select(
            nmea = nmeaFix,
            locValues = locFix,
            lastKnown = lastKnown,
            cached = cachedTruth,
            nowElapsedMs = nowElapsed,
            cachedAtElapsedMs = cachedTruthAtElapsedMs,
        )
        if (selected != null && (nmeaFix != null || locFix != null || lastKnown != null)) {
            cachedTruth = selected
            cachedTruthAtElapsedMs = nowElapsed - selected.ageMs
        }
        sb.append("truth.lat=").append(selected?.lat ?: "-")
            .append(" truth.lon=").append(selected?.lon ?: "-")
            .append(" truth.course=").append(selected?.courseDeg ?: "-")
            .append(" truth.src=").append(selected?.src ?: "-")
            .append(" truth.accM=").append(selected?.accM ?: "-")
            .append(" truth.ageMs=").append(selected?.ageMs ?: "-")
            .append('\n')
    }

    @SuppressLint("MissingPermission")
    private fun androidLastKnown(nowElapsed: Long): GeoDebugHiddenTruth.Fix? {
        val ctx = appContext ?: return null
        return runCatching {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return null
            val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
                ?: return null
            fixFromAndroidLocation(loc, nowElapsed)
        }.getOrNull()
    }

    private fun fixFromAndroidLocation(
        loc: Location,
        nowElapsed: Long,
    ): GeoDebugHiddenTruth.Fix? {
        val ageMs = if (loc.elapsedRealtimeNanos > 0L) {
            nowElapsed - loc.elapsedRealtimeNanos / 1_000_000L
        } else {
            0L
        }
        return GeoDebugHiddenTruth.fromPublished(
            lat = loc.latitude,
            lon = loc.longitude,
            courseDeg = if (loc.hasBearing()) loc.bearing else null,
            src = "android",
            accM = if (loc.hasAccuracy()) loc.accuracy else null,
            ageMs = ageMs.coerceAtLeast(0L),
        )
    }

    private fun fmt(v: Double): String =
        if (!v.isFinite()) "-" else String.format(Locale.US, "%.4f", v)

    private fun sanitizeOneLine(s: String): String =
        s.replace('\n', ' ').replace('\r', ' ').take(500)

    private fun formatWall(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(ms))
}
