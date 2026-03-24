package vad.dashing.tbox

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * In-memory car-related aggregates (motor hours, future trip history, etc.).
 * Persistent copy lives in [AppDataManager]; sync rules are enforced in
 * [BackgroundService] and [AppDataViewModel].
 */
object CarDataRepository {
    private const val PERSIST_EPS = 1e-4f

    private val _motorHours = MutableStateFlow(0f)
    val motorHours: StateFlow<Float> = _motorHours.asStateFlow()

    /** Last value successfully written to [AppDataManager] (DataStore). */
    private var lastPersistedMotorHours: Float = 0f

    fun setMotorHours(value: Float) {
        _motorHours.value = value
    }

    fun addMotorHours(delta: Float) {
        if (delta == 0f) return
        _motorHours.value += delta
    }

    fun markPersisted(value: Float) {
        lastPersistedMotorHours = value
    }

    fun needsPersistence(): Boolean =
        abs(_motorHours.value - lastPersistedMotorHours) > PERSIST_EPS
}
