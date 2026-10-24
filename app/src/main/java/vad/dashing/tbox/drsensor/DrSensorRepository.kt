package vad.dashing.tbox.drsensor

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vad.dashing.tbox.HeadUnitCanMode
import vad.dashing.tbox.TboxRepository
import vad.dashing.tbox.location.YawIntegrator
import vad.dashing.tbox.mbcan.UniversalCanRepository

/**
 * Probes OEM / Android IMU-DR sources for Geoposition diagnostics.
 * Prefer A9 uds-sensor or A10 NaviDR when the HU mode matches; keep SensorManager as fallback.
 */
object DrSensorRepository {
    private const val TAG = "DrSensorRepository"

    private val _snapshot = MutableStateFlow(DrSensorSnapshot.EMPTY)
    val snapshot: StateFlow<DrSensorSnapshot> = _snapshot.asStateFlow()

    private var appContext: Context? = null
    private var primary: DrSensorBackend? = null
    private var fallback: DrSensorBackend? = null
    private var started = false
    private var primaryGotSample = false

    fun start(context: Context) {
        if (started) return
        appContext = context.applicationContext
        started = true
        restartBackends()
        TboxRepository.addLog("INFO", TAG, "DR sensor probe started")
    }

    fun stop() {
        if (!started) return
        started = false
        stopBackends()
        YawIntegrator.reset()
        _snapshot.value = DrSensorSnapshot.EMPTY
        TboxRepository.addLog("INFO", TAG, "DR sensor probe stopped")
    }

    /** Call when [UniversalCanRepository.mode] changes. */
    fun onHeadUnitModeChanged() {
        if (!started) return
        restartBackends()
    }

    private fun restartBackends() {
        stopBackends()
        primaryGotSample = false
        val ctx = appContext ?: return
        val mode = UniversalCanRepository.mode.value
        primary = when (mode) {
            HeadUnitCanMode.Android9MbCan -> A9UdsSensorBackend()
            HeadUnitCanMode.Android10Vhal -> A10NaviDrBackend()
        }
        fallback = AndroidSensorDrBackend(ctx)
        _snapshot.value = DrSensorSnapshot(
            source = DrSensorSource.NONE,
            statusText = "starting ${primary?.source?.displayLabel()}…",
            lastUpdateElapsedMs = SystemClock.elapsedRealtime(),
        )
        primary?.start { snap ->
            if (hasUsefulData(snap)) {
                primaryGotSample = true
                _snapshot.value = snap
                noteYawSample(snap)
            } else {
                // Keep status from primary even without numbers yet.
                if (!primaryGotSample) {
                    _snapshot.value = snap
                }
            }
        }
        fallback?.start { snap ->
            if (!primaryGotSample && hasUsefulData(snap) && !snap.halRefused) {
                _snapshot.value = snap
                noteYawSample(snap)
            } else if (!primaryGotSample && snap.halRefused) {
                val primaryStatus = _snapshot.value.statusText
                _snapshot.value = snap.copy(
                    statusText = if (primaryStatus.isNotBlank() && !primaryStatus.contains(snap.statusText)) {
                        "${snap.statusText} (primary: $primaryStatus)"
                    } else {
                        snap.statusText
                    },
                    halRefused = true,
                )
            } else if (!primaryGotSample &&
                _snapshot.value.source == DrSensorSource.NONE &&
                snap.statusText.isNotBlank()
            ) {
                // Show SensorManager status if primary never published useful data.
                val primaryStatus = _snapshot.value.statusText
                if (primaryStatus.startsWith("error") ||
                    primaryStatus.contains("null") ||
                    primaryStatus.contains("failed")
                ) {
                    _snapshot.value = snap.copy(
                        statusText = "${snap.statusText} (primary: $primaryStatus)",
                    )
                }
            }
        }
        Log.d(TAG, "backends: primary=${primary?.source} mode=$mode")
    }

    /**
     * True when the head-unit gyro (`GYRO` / `GYRO_STEER`) can be used on this unit.
     * On A10 there is no working gyro (`binder null` + SensorService HAL refusal) unless
     * a real `gyroYaw` sample actually arrived.
     */
    fun isHuGyroAvailable(
        mode: HeadUnitCanMode = UniversalCanRepository.mode.value,
        snap: DrSensorSnapshot = _snapshot.value,
    ): Boolean {
        if (snap.gyroYaw != null) return true
        if (snap.halRefused) return false
        return mode != HeadUnitCanMode.Android10Vhal
    }

    private fun stopBackends() {
        runCatching { primary?.stop() }
        runCatching { fallback?.stop() }
        primary = null
        fallback = null
        primaryGotSample = false
    }

    private fun hasUsefulData(snap: DrSensorSnapshot): Boolean {
        return snap.gyroYaw != null ||
            snap.gyroPitch != null ||
            snap.accelX != null ||
            snap.pulseValue != null ||
            snap.mountExist != null
    }

    private fun noteYawSample(snap: DrSensorSnapshot) {
        YawIntegrator.onRawSample(snap.gyroYaw, snap.lastUpdateElapsedMs)
    }
}
