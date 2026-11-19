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
import kotlinx.coroutines.withContext

/**
 * Shared camd `STATUS` poll for automation signals and dashboard AVM/REC tiles.
 * Does not wake camd — same rule as the Information tab.
 */
object CamdStatusRepository {
    private val _status = MutableStateFlow<CamdServiceStatus?>(null)
    val status: StateFlow<CamdServiceStatus?> = _status.asStateFlow()

    private val startMutex = Mutex()
    private val refreshMutex = Mutex()
    @Volatile
    private var pollJob: Job? = null
    @Volatile
    private var appContext: Context? = null

    fun start(context: Context, scope: CoroutineScope) {
        val app = context.applicationContext
        appContext = app
        scope.launch {
            startMutex.withLock {
                if (pollJob?.isActive == true) return@withLock
                pollJob = scope.launch(Dispatchers.IO) {
                    while (isActive) {
                        refreshMutex.withLock { refreshOnceLocked(app) }
                        delay(POLL_INTERVAL_MS)
                    }
                }
            }
        }
    }

    /** One STATUS round-trip on IO; updates [status]. Safe to call from UI coroutines. */
    suspend fun refreshNow() {
        val app = appContext ?: return
        refreshMutex.withLock {
            withContext(Dispatchers.IO) {
                refreshOnceLocked(app)
            }
        }
    }

    private fun refreshOnceLocked(app: Context) {
        if (isCamdPackageInstalled(app.packageManager)) {
            _status.value = CamdWindowClient.fetchStatus()
        } else {
            _status.value = null
        }
    }

    /** Shared interval for tiles + automation signals (not per-tile timers). */
    const val POLL_INTERVAL_MS = 5_000L
}
