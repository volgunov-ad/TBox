package vad.dashing.tbox.wifimodem

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
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
    private var olaxClient: OlaxReqprocClient? = null
    private var activeModel: WifiModemModel? = null
    private var previousSnapshot: WifiModemSnapshot? = null
    private var consecutiveFailures: Int = 0
    private var lastHost: String = ""
    private var lastPassword: String = ""
    private var lastPollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS
    /** Elapsed realtime until which post-reboot downtime should not clear mirrored net data. */
    private var recoveryUntilElapsedMs: Long = 0L

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
                        olaxClient = null
                    }
                    WifiModemModel.HUAWEI_E3372 -> {
                        huaweiClient = HuaweiHilinkClient(host = effectiveHost)
                        zteClient = null
                        olaxClient = null
                    }
                    WifiModemModel.OLAX_F95 -> {
                        olaxClient = OlaxReqprocClient(host = effectiveHost, password = password)
                        zteClient = null
                        huaweiClient = null
                    }
                }
                activeModel = model
                previousSnapshot = null
                consecutiveFailures = 0
                recoveryUntilElapsedMs = 0L
                val interval = pollIntervalMs.coerceIn(MIN_POLL_INTERVAL_MS, MAX_POLL_INTERVAL_MS)
                lastHost = effectiveHost
                lastPassword = password
                lastPollIntervalMs = interval
                job = scope.launch {
                    TboxRepository.updateWifiModemLinkStatus(WifiModemLinkStatus.IDLE)
                    TboxRepository.addLog(
                        "INFO",
                        "Wi‑Fi modem",
                        "Опрос ${model.displayName} @ $effectiveHost",
                    )
                    while (isActive) {
                        // Serialize with reboot/data control so client recreation is race-free.
                        mutex.withLock { pollOnceLocked() }
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
                        WifiModemModel.OLAX_F95 -> {
                            val client = olaxClient
                                ?: throw IllegalStateException("Olax client not running")
                            client.setMobileDataEnabled(enabled)
                        }
                        null -> throw IllegalStateException("No controllable Wi‑Fi modem active")
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
                var rebootIssued = false
                try {
                    when (activeModel) {
                        WifiModemModel.ZTE_MF79U -> {
                            val client = zteClient
                                ?: throw IllegalStateException("ZTE client not running")
                            client.rebootDevice()
                            rebootIssued = true
                        }
                        WifiModemModel.HUAWEI_E3372 -> {
                            val client = huaweiClient
                                ?: throw IllegalStateException("Huawei client not running")
                            client.rebootDevice()
                            rebootIssued = true
                        }
                        WifiModemModel.OLAX_F95 -> {
                            val client = olaxClient
                                ?: throw IllegalStateException("Olax client not running")
                            client.rebootDevice()
                            rebootIssued = true
                        }
                        null -> throw IllegalStateException("No controllable Wi‑Fi modem active")
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
                    if (rebootIssued) {
                        prepareForModemRebootLocked()
                    }
                }
            }
        }
    }

    /**
     * After a reboot command the HTTP session and OkHttp sockets are dead until the
     * modem AP comes back. Recreate clients (drop connection pool), clear session,
     * and suppress mirror-clearing during the grace window so recovery can republish.
     */
    private fun prepareForModemRebootLocked() {
        recreateClientsLocked()
        previousSnapshot = null
        consecutiveFailures = 0
        recoveryUntilElapsedMs = SystemClock.elapsedRealtime() + REBOOT_RECOVERY_GRACE_MS
        TboxRepository.updateWifiModemLinkStatus(WifiModemLinkStatus.UNREACHABLE)
        TboxRepository.addLog(
            "INFO",
            "Wi‑Fi modem",
            "Ожидание возврата модема после перезагрузки",
        )
    }

    private fun recreateClientsLocked() {
        val model = activeModel ?: return
        val host = lastHost.ifBlank { model.defaultHost }
        zteClient?.invalidateSession()
        huaweiClient?.invalidateSession()
        olaxClient?.invalidateSession()
        when (model) {
            WifiModemModel.ZTE_MF79U -> {
                zteClient = ZteGoformClient(host = host, password = lastPassword)
                huaweiClient = null
                olaxClient = null
            }
            WifiModemModel.HUAWEI_E3372 -> {
                huaweiClient = HuaweiHilinkClient(host = host)
                zteClient = null
                olaxClient = null
            }
            WifiModemModel.OLAX_F95 -> {
                olaxClient = OlaxReqprocClient(host = host, password = lastPassword)
                zteClient = null
                huaweiClient = null
            }
        }
    }

    private fun inRebootRecovery(): Boolean =
        SystemClock.elapsedRealtime() < recoveryUntilElapsedMs

    private fun stopLocked() {
        job?.cancel()
        job = null
        zteClient?.invalidateSession()
        zteClient = null
        huaweiClient?.invalidateSession()
        huaweiClient = null
        olaxClient?.invalidateSession()
        olaxClient = null
        activeModel = null
        previousSnapshot = null
        recoveryUntilElapsedMs = 0L
        TboxRepository.updateWifiModemLinkStatus(WifiModemLinkStatus.IDLE)
    }

    private fun pollOnceLocked() {
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
                WifiModemModel.OLAX_F95 -> {
                    val active = olaxClient ?: return
                    val fields = try {
                        active.fetchStatus()
                    } catch (first: OlaxReqprocException) {
                        active.invalidateSession()
                        active.fetchStatus()
                    }
                    ZteReqprocStatusMapper.map(fields, previousSnapshot)
                }
            }
            previousSnapshot = snap
            consecutiveFailures = 0
            if (inRebootRecovery()) {
                recoveryUntilElapsedMs = 0L
                TboxRepository.addLog(
                    "INFO",
                    "Wi‑Fi modem",
                    "Связь с модемом восстановлена после перезагрузки",
                )
            }
            TboxRepository.updateWifiModemLinkStatus(WifiModemLinkStatus.OK)
            publish(snap)
        } catch (e: Exception) {
            consecutiveFailures += 1
            Log.w(TAG, "poll failed ($consecutiveFailures): ${e.message}")
            // Drop stale sessions after any failure (reboot / AP flap); ConnectException
            // otherwise leaves tokens that block recovery until a full source switch.
            zteClient?.invalidateSession()
            huaweiClient?.invalidateSession()
            olaxClient?.invalidateSession()
            if (inRebootRecovery() || consecutiveFailures <= 2) {
                // Recreate OkHttp clients to flush dead keep-alive sockets after reboot.
                recreateClientsLocked()
            }
            TboxRepository.updateWifiModemLinkStatus(classifyFailure(e))
            val clearAllowed = !inRebootRecovery()
            if (clearAllowed && consecutiveFailures >= CLEAR_AFTER_FAILURES) {
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
        /** Typical MiFi reboot + Wi‑Fi reassociation window. */
        private const val REBOOT_RECOVERY_GRACE_MS = 120_000L
    }
}
