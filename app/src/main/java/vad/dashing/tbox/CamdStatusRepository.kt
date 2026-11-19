package vad.dashing.tbox

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Polls camd `STATUS` for automation signals (`camd_recorder_enabled`, `camd_recording_active`).
 * Does not wake camd — same rule as the Information tab.
 */
object CamdStatusRepository {
    private val _status = MutableStateFlow<CamdServiceStatus?>(null)
    val status: StateFlow<CamdServiceStatus?> = _status.asStateFlow()

    private val startMutex = Mutex()
    @Volatile
    private var pollJob: Job? = null

    fun start(context: Context, scope: CoroutineScope) {
        val app = context.applicationContext
        scope.launch {
            startMutex.withLock {
                if (pollJob?.isActive == true) return@withLock
                pollJob = scope.launch(Dispatchers.IO) {
                    while (isActive) {
                        if (isCamdPackageInstalled(app.packageManager)) {
                            _status.value = CamdWindowClient.fetchStatus()
                        } else {
                            _status.value = null
                        }
                        delay(POLL_INTERVAL_MS)
                    }
                }
            }
        }
    }

    private const val POLL_INTERVAL_MS = 2_000L
}
