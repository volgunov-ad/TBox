package vad.dashing.tbox.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared state for CONSTANT-mode auto calibration need + last successful calib timestamp.
 * Persisted via Settings; updated on auto/manual drive or yaw-zero success.
 *
 * Drive calibration clears [needsCalibration]; yaw-zero only refreshes [lastCalibratedAtEpochMs].
 */
object GeoCalibrationState {
    private val _needsCalibration = MutableStateFlow(false)
    val needsCalibration: StateFlow<Boolean> = _needsCalibration.asStateFlow()

    private val _lastCalibratedAtEpochMs = MutableStateFlow(0L)
    val lastCalibratedAtEpochMs: StateFlow<Long> = _lastCalibratedAtEpochMs.asStateFlow()

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
}
