package vad.dashing.tbox.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Shared state for CONSTANT-mode auto calibration need + last successful calib timestamp.
 * Persisted via Settings; updated on auto/manual drive or yaw-zero success.
 *
 * Drive calibration clears [needsCalibration]; yaw-zero only refreshes [lastCalibratedAtEpochMs].
 *
 * [successSerial] guards against stale `saveGeoCalibNeeds(true)` coroutines that were
 * launched before a successful drive Save and would otherwise re-show the red banner.
 */
object GeoCalibrationState {
    private val _needsCalibration = MutableStateFlow(false)
    val needsCalibration: StateFlow<Boolean> = _needsCalibration.asStateFlow()

    private val _lastCalibratedAtEpochMs = MutableStateFlow(0L)
    val lastCalibratedAtEpochMs: StateFlow<Long> = _lastCalibratedAtEpochMs.asStateFlow()

    private val successSerial = AtomicLong(0L)

    /** Bumps on each successful drive calibration (memory clear). */
    fun currentSuccessSerial(): Long = successSerial.get()

    fun load(needs: Boolean, lastAtEpochMs: Long) {
        _needsCalibration.value = needs
        _lastCalibratedAtEpochMs.value = lastAtEpochMs.coerceAtLeast(0L)
    }

    fun requestCalibration() {
        if (!_needsCalibration.value) {
            _needsCalibration.value = true
        }
    }

    /**
     * Successful drive calibration (auto or manual): clear need flag and record time.
     */
    fun markCalibrated(atEpochMs: Long = System.currentTimeMillis()) {
        successSerial.incrementAndGet()
        _needsCalibration.value = false
        if (atEpochMs > 0L) {
            _lastCalibratedAtEpochMs.value = atEpochMs
        }
    }

    /**
     * Yaw-zero (auto idle or manual): record time only — does not clear [needsCalibration].
     */
    fun noteCalibrationActivity(atEpochMs: Long = System.currentTimeMillis()) {
        if (atEpochMs > 0L) {
            _lastCalibratedAtEpochMs.value = atEpochMs
        }
    }

    fun setNeedsCalibration(needs: Boolean) {
        _needsCalibration.value = needs
    }

    /**
     * Apply persisted/async "needs calib" only if no drive Save happened since [onlyIfSuccessSerial].
     * @return true if the need flag is (still) asserted in memory
     */
    fun applyNeedsIfSerialUnchanged(onlyIfSuccessSerial: Long): Boolean {
        if (successSerial.get() != onlyIfSuccessSerial) return false
        requestCalibration()
        return true
    }
}
