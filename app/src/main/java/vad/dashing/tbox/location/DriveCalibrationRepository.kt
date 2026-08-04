package vad.dashing.tbox.location

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vad.dashing.tbox.TboxRepository
import vad.dashing.tbox.TripTelemetryRepository
import vad.dashing.tbox.drsensor.DrSensorRepository

/**
 * Owns the drive-calibration session ticker (raw GNSS + CAN + debiased yaw).
 * Persist via Settings after user confirms Save on preview.
 */
object DriveCalibrationRepository {
    private val _uiState = MutableStateFlow(DriveCalibrationSession.UiState())
    val uiState: StateFlow<DriveCalibrationSession.UiState> = _uiState.asStateFlow()

    enum class Message {
        SAVED,
        RESET,
        CANCELLED,
        NOTHING_TO_SAVE,
        TIMED_OUT,
    }

    private val _flashMessage = MutableStateFlow<Message?>(null)
    val flashMessage: StateFlow<Message?> = _flashMessage.asStateFlow()

    @Volatile
    private var junkFilterOn: Boolean = true

    private var scope: CoroutineScope? = null
    private var tickJob: Job? = null
    private var session: DriveCalibrationSession? = null
    private var autoPreviewDone: Boolean = false
    private var timeoutPreviewDone: Boolean = false

    fun attach(scope: CoroutineScope) {
        this.scope = scope
    }

    fun setJunkFilterEnabled(enabled: Boolean) {
        junkFilterOn = enabled
    }

    fun beginSession() {
        autoPreviewDone = false
        timeoutPreviewDone = false
        val s = DriveCalibrationSession()
        s.start(SystemClock.elapsedRealtime())
        session = s
        publish()
        ensureTicker()
    }

    fun cancelSession(announce: Boolean = true) {
        tickJob?.cancel()
        tickJob = null
        session?.cancel()
        session = null
        autoPreviewDone = false
        timeoutPreviewDone = false
        if (announce) {
            _flashMessage.value = Message.CANCELLED
        }
        publish()
    }

    /** Manual Enough → preview (may be low-quality if little data). */
    fun finishEnough() {
        val s = session ?: return
        val sc = scope
        if (sc != null) {
            // finishToPreview recomputes estimates; keep off the Compose main thread.
            sc.launch {
                s.finishToPreview(System.currentTimeMillis(), DriveCalibrationStore.offsets)
                publish()
            }
        } else {
            s.finishToPreview(System.currentTimeMillis(), DriveCalibrationStore.offsets)
            publish()
        }
    }

    fun isSessionAutoReady(): Boolean = session?.isAutoReady() == true

    /**
     * True when a session is running and was started for background auto-calib
     * (UI should treat it as a normal session if user opens the tab).
     */
    fun hasActiveSession(): Boolean =
        session != null &&
            uiState.value.phase != DriveCalibrationSession.Phase.IDLE

    fun takePreviewForSave(announce: Boolean = true): DriveCalibrationOffsets? {
        val ui = session?.uiState() ?: return null
        val preview = ui.preview ?: return null
        if (!preview.speedEstimated && !preview.yawEstimated) {
            if (announce) {
                _flashMessage.value = Message.NOTHING_TO_SAVE
            }
            return null
        }
        cancelSession(announce = false)
        if (announce) {
            _flashMessage.value = Message.SAVED
        }
        return preview
    }

    fun consumeFlashMessage(): Message? {
        val m = _flashMessage.value
        _flashMessage.value = null
        return m
    }

    fun announceReset() {
        _flashMessage.value = Message.RESET
    }

    private fun ensureTicker() {
        if (tickJob?.isActive == true) return
        val sc = scope ?: return
        tickJob = sc.launch {
            while (isActive) {
                val s = session
                if (s == null) break
                val phase = s.uiState().phase
                if (phase == DriveCalibrationSession.Phase.IDLE ||
                    phase == DriveCalibrationSession.Phase.PREVIEW
                ) {
                    publish()
                    delay(200)
                    continue
                }
                val now = SystemClock.elapsedRealtime()
                val live = TboxRepository.locValues.value
                val can = TripTelemetryRepository.accountingCarSpeed(now)
                val liveUsable = MockLocationJob.isLiveUsable(live, junkFilterOn, can, now)
                val accuracyM = LocationMockManager.horizontalAccuracyMeters(
                    hdop = live.hdop,
                    retainingFix = false,
                    hrms = live.hrms,
                )
                val snap = DrSensorRepository.snapshot.value
                val gyroAvailable = snap.gyroYaw != null && snap.gyroYaw.isFinite()
                val yawDebiased = GyroBiasStore.applyYaw(snap.gyroYaw)
                s.onTick(
                    elapsedMs = now,
                    liveUsable = liveUsable,
                    live = live,
                    canKmh = can,
                    yawDebiasedDegPerSec = yawDebiased,
                    horizontalAccuracyM = accuracyM,
                    gyroAvailable = gyroAvailable,
                )
                if (!autoPreviewDone && s.isAutoReady()) {
                    autoPreviewDone = true
                    timeoutPreviewDone = true
                    s.finishToPreview(System.currentTimeMillis(), DriveCalibrationStore.offsets)
                } else if (!timeoutPreviewDone && s.isTimedOut(now)) {
                    timeoutPreviewDone = true
                    autoPreviewDone = true
                    s.finishToPreview(System.currentTimeMillis(), DriveCalibrationStore.offsets)
                    _flashMessage.value = Message.TIMED_OUT
                }
                publish()
                delay(100)
            }
        }
    }

    private fun publish() {
        _uiState.value = session?.uiState() ?: DriveCalibrationSession.UiState()
    }
}
