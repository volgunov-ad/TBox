package vad.dashing.tbox.wifimodem

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vad.dashing.tbox.APNState
import vad.dashing.tbox.NetState
import vad.dashing.tbox.TboxRepository

/**
 * Polls a Wi‑Fi modem HTTP API and mirrors status into [TboxRepository] net/APN flows
 * (same sinks the Modem tab and net widgets already read).
 */
class WifiModemPoller(
    private val appContext: Context,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private var job: Job? = null
    private var client: ZteGoformClient? = null
    private var previousSnapshot: WifiModemSnapshot? = null
    private var consecutiveFailures: Int = 0

    val isRunning: Boolean
        get() = job?.isActive == true

    fun start(
        host: String,
        password: String,
        model: WifiModemModel,
        pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
    ) {
        scope.launch {
            mutex.withLock {
                stopLocked()
                if (model != WifiModemModel.ZTE_MF79U) {
                    Log.w(TAG, "Model ${model.storageId} has no HTTP driver yet; poller not started")
                    TboxRepository.addLog(
                        "WARN",
                        "Wi‑Fi modem",
                        "Модель ${model.displayName} пока без драйвера HTTP",
                    )
                    return@withLock
                }
                val effectiveHost = host.trim().ifBlank { model.defaultHost }
                client = ZteGoformClient(host = effectiveHost, password = password)
                previousSnapshot = null
                consecutiveFailures = 0
                val interval = pollIntervalMs.coerceIn(MIN_POLL_INTERVAL_MS, MAX_POLL_INTERVAL_MS)
                job = scope.launch {
                    TboxRepository.addLog(
                        "INFO",
                        "Wi‑Fi modem",
                        "Опрос ${model.displayName} @ $effectiveHost",
                    )
                    while (isActive) {
                        pollOnce()
                        delay(interval)
                    }
                }
            }
        }
    }

    fun stop() {
        scope.launch {
            mutex.withLock { stopLocked() }
        }
    }

    private fun stopLocked() {
        job?.cancel()
        job = null
        client?.invalidateSession()
        client = null
        previousSnapshot = null
    }

    private fun pollOnce() {
        val active = client ?: return
        val unbind = bindProcessToWifiNetwork()
        try {
            val fields = try {
                active.fetchStatus()
            } catch (first: ZteGoformException) {
                active.invalidateSession()
                active.fetchStatus()
            }
            val snap = ZteReqprocStatusMapper.map(fields, previousSnapshot)
            previousSnapshot = snap
            consecutiveFailures = 0
            publish(snap)
        } catch (e: Exception) {
            consecutiveFailures += 1
            Log.w(TAG, "poll failed ($consecutiveFailures): ${e.message}")
            if (consecutiveFailures >= CLEAR_AFTER_FAILURES) {
                clearNetMirror()
            }
            if (consecutiveFailures == 1 || consecutiveFailures % 6 == 0) {
                TboxRepository.addLog(
                    "WARN",
                    "Wi‑Fi modem",
                    "Ошибка опроса: ${e.message ?: e.javaClass.simpleName}",
                )
            }
        } finally {
            unbind()
        }
    }

    private fun publish(snap: WifiModemSnapshot) {
        TboxRepository.updateNetState(snap.netState)
        TboxRepository.updateNetValues(snap.netValues)
        TboxRepository.updateAPNState(snap.apnState)
        TboxRepository.updateAPN2State(APNState())
        TboxRepository.updateAPNStatus(snap.apnStatus)
    }

    private fun clearNetMirror() {
        TboxRepository.updateNetState(NetState())
        TboxRepository.updateAPNState(APNState())
        TboxRepository.updateAPN2State(APNState())
        TboxRepository.updateAPNStatus(false)
        previousSnapshot = null
    }

    /**
     * Prefer the Wi‑Fi Network for process sockets so HU "Wi‑Fi without internet"
     * does not divert LAN HTTP to mobile. Returns an undo lambda.
     */
    private fun bindProcessToWifiNetwork(): () -> Unit {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return {}
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return {}
        val wifi = cm.allNetworks.firstOrNull { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@firstOrNull false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } ?: return {}
        val previous: Network? = try {
            cm.boundNetworkForProcess
        } catch (_: Throwable) {
            null
        }
        return try {
            cm.bindProcessToNetwork(wifi)
            val undo: () -> Unit = {
                try {
                    cm.bindProcessToNetwork(previous)
                } catch (_: Throwable) {
                    try {
                        cm.bindProcessToNetwork(null)
                    } catch (_: Throwable) {
                    }
                }
            }
            undo
        } catch (_: Throwable) {
            {}
        }
    }

    companion object {
        private const val TAG = "WifiModemPoller"
        const val DEFAULT_POLL_INTERVAL_MS = 5_000L
        const val MIN_POLL_INTERVAL_MS = 2_000L
        const val MAX_POLL_INTERVAL_MS = 60_000L
        private const val CLEAR_AFTER_FAILURES = 3
    }
}
