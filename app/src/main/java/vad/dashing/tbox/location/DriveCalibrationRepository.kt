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
    }

    private val _flashMessage = MutableStateFlow<Message?>(null)
    val flashMessage: StateFlow<Message?> = _flashMessage.asStateFlow()

    @Volatile
    private var junkFilterOn: Boolean = true

    private var scope: CoroutineScope? = null
    private var tickJob: Job? = null
    private var session: DriveCalibrationSession? = null
    private var autoPreviewDone: Boolean = false

    fun attach(scope: CoroutineScope) {
        this.scope = scope
    }

    fun setJunkFilterEnabled(enabled: Boolean) {
        junkFilterOn = enabled
    }

    fun beginSession() {
        autoPreviewDone = false
        val s = DriveCalibrationSession()
        s.start()
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
        if (announce) {
            _flashMessage.value = Message.CANCELLED
        }
        publish()
    }

    /** Manual Enough → preview (may be low-quality if little data). */
    fun finishEnough() {
        val s = session ?: return
        s.finishToPreview(System.currentTimeMillis(), DriveCalibrationStore.offsets)
        publish()
    }

    fun takePreviewForSave(): DriveCalibrationOffsets? {
        val ui = session?.uiState() ?: return null
        val preview = ui.preview ?: return null
        if (!preview.speedEstimated && !preview.yawEstimated) {
            _flashMessage.value = Message.NOTHING_TO_SAVE
            return null
        }
        cancelSession(announce = false)
        _flashMessage.value = Message.SAVED
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
                    s.finishToPreview(System.currentTimeMillis(), DriveCalibrationStore.offsets)
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
