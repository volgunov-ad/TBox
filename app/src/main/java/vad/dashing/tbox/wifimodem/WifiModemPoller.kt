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
import vad.dashing.tbox.NetValues
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
    private var zteClient: ZteGoformClient? = null
    private var huaweiClient: HuaweiHilinkClient? = null
    private var activeModel: WifiModemModel? = null
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
                val effectiveHost = host.trim().ifBlank { model.defaultHost }
                when (model) {
                    WifiModemModel.ZTE_MF79U -> {
                        zteClient = ZteGoformClient(host = effectiveHost, password = password)
                        huaweiClient = null
                    }
                    WifiModemModel.HUAWEI_E3372 -> {
                        huaweiClient = HuaweiHilinkClient(host = effectiveHost)
                        zteClient = null
                    }
                    WifiModemModel.OLAX_F95 -> {
                        Log.w(TAG, "Model ${model.storageId} has no HTTP driver yet; poller not started")
                        TboxRepository.addLog(
                            "WARN",
                            "Wi‑Fi modem",
                            "Модель ${model.displayName} пока без драйвера HTTP",
                        )
                        return@withLock
                    }
                }
                activeModel = model
                previousSnapshot = null
                consecutiveFailures = 0
                val interval = pollIntervalMs.coerceIn(MIN_POLL_INTERVAL_MS, MAX_POLL_INTERVAL_MS)
                job = scope.launch {
                    TboxRepository.updateWifiModemLinkStatus(WifiModemLinkStatus.IDLE)
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

    /** Enable / disable mobile data on the active Wi‑Fi modem (best-effort). */
    fun setMobileDataEnabled(enabled: Boolean) {
        scope.launch {
            mutex.withLock {
                val unbind = bindProcessToWifiNetwork()
                try {
                    when (activeModel) {
                        WifiModemModel.ZTE_MF79U -> {
                            val client = zteClient
                                ?: throw IllegalStateException("ZTE client not running")
                            client.setMobileDataEnabled(enabled)
                        }
                        WifiModemModel.HUAWEI_E3372 -> {
                            val client = huaweiClient
                                ?: throw IllegalStateException("Huawei client not running")
                            client.setMobileDataEnabled(enabled)
                        }
                        else -> throw IllegalStateException("No controllable Wi‑Fi modem active")
                    }
                    TboxRepository.addLog(
                        "INFO",
                        "Wi‑Fi modem",
                        if (enabled) "Включение передачи данных" else "Отключение передачи данных",
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "setMobileDataEnabled failed: ${e.message}")
                    TboxRepository.addLog(
                        "WARN",
                        "Wi‑Fi modem",
                        "Ошибка управления данными: ${e.message ?: e.javaClass.simpleName}",
                    )
                } finally {
                    unbind()
                }
            }
        }
    }

    /** Reboot the active Wi‑Fi modem (best-effort). */
    fun rebootModem() {
        scope.launch {
            mutex.withLock {
                val unbind = bindProcessToWifiNetwork()
                try {
                    when (activeModel) {
                        WifiModemModel.ZTE_MF79U -> {
                            val client = zteClient
                                ?: throw IllegalStateException("ZTE client not running")
                            client.rebootDevice()
                        }
                        WifiModemModel.HUAWEI_E3372 -> {
                            val client = huaweiClient
                                ?: throw IllegalStateException("Huawei client not running")
                            client.rebootDevice()
                        }
                        else -> throw IllegalStateException("No controllable Wi‑Fi modem active")
                    }
                    TboxRepository.addLog("INFO", "Wi‑Fi modem", "Перезагрузка модема")
                } catch (e: Exception) {
                    Log.w(TAG, "rebootModem failed: ${e.message}")
                    TboxRepository.addLog(
                        "WARN",
                        "Wi‑Fi modem",
                        "Ошибка перезагрузки: ${e.message ?: e.javaClass.simpleName}",
                    )
                } finally {
                    unbind()
                }
            }
        }
    }

    private fun stopLocked() {
        job?.cancel()
        job = null
        zteClient?.invalidateSession()
        zteClient = null
        huaweiClient?.invalidateSession()
        huaweiClient = null
        activeModel = null
        previousSnapshot = null
        TboxRepository.updateWifiModemLinkStatus(WifiModemLinkStatus.IDLE)
    }

    private fun pollOnce() {
        val model = activeModel ?: return
        val unbind = bindProcessToWifiNetwork()
        try {
            val snap = when (model) {
                WifiModemModel.ZTE_MF79U -> {
                    val active = zteClient ?: return
                    val fields = try {
                        active.fetchStatus()
                    } catch (first: ZteGoformException) {
                        active.invalidateSession()
                        active.fetchStatus()
                    }
                    ZteReqprocStatusMapper.map(fields, previousSnapshot)
                }
                WifiModemModel.HUAWEI_E3372 -> {
                    val active = huaweiClient ?: return
                    try {
                        active.fetchStatus(previousSnapshot)
                    } catch (first: HuaweiHilinkException) {
                        active.invalidateSession()
                        active.fetchStatus(previousSnapshot)
                    }
                }
                WifiModemModel.OLAX_F95 -> return
            }
            previousSnapshot = snap
            consecutiveFailures = 0
            TboxRepository.updateWifiModemLinkStatus(WifiModemLinkStatus.OK)
            publish(snap)
        } catch (e: Exception) {
            consecutiveFailures += 1
            Log.w(TAG, "poll failed ($consecutiveFailures): ${e.message}")
            TboxRepository.updateWifiModemLinkStatus(classifyFailure(e))
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

    private fun classifyFailure(e: Exception): WifiModemLinkStatus {
        val msg = e.message.orEmpty().lowercase()
        return when {
            "login rejected" in msg ||
                "wrong password" in msg ||
                ("auth" in msg && "fail" in msg) -> WifiModemLinkStatus.AUTH_FAILED
            msg.contains("failed to connect") ||
                msg.contains("timeout") ||
                msg.contains("unreachable") ||
                msg.contains("unable to resolve") ||
                msg.contains("network is unreachable") ||
                msg.contains("econnrefused") ||
                msg.contains("socket") -> WifiModemLinkStatus.UNREACHABLE
            e is java.net.UnknownHostException ||
                e is java.net.ConnectException ||
                e is java.net.SocketTimeoutException ||
                e is java.io.InterruptedIOException -> WifiModemLinkStatus.UNREACHABLE
            else -> WifiModemLinkStatus.ERROR
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
        TboxRepository.updateNetValues(NetValues())
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
