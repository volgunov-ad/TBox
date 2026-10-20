package vad.dashing.tbox.internet

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import vad.dashing.tbox.TboxRepository

/**
 * Periodically probes a user-configured URL to decide whether the head unit has working internet.
 * Publishes [HuInternetStatus] into [TboxRepository].
 *
 * Online is published on the first success; offline only after
 * [HuInternetStatusLogic.FAILURES_BEFORE_OFFLINE] consecutive failures.
 * Intermediate [HuInternetStatus.CHECKING] is not published on every poll so automation
 * state edges do not flap.
 *
 * Uses a per-network OkHttp [socketFactory] instead of [ConnectivityManager.bindProcessToNetwork]
 * so it does not fight [vad.dashing.tbox.wifimodem.WifiModemPoller]'s process bind.
 */
class HuInternetMonitor(
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val clientFactory: () -> OkHttpClient = { HuInternetProbe.defaultClient() },
) {
    private val mutex = Mutex()
    private var job: Job? = null
    private var snapshot = HuInternetStatusLogic.Snapshot()
    /**
     * One client for the monitor's lifetime: a new client per probe leaks its pool, a TLS
     * connection and an HTTP/2 reader thread for 5 minutes each (19 threads seen on A10).
     */
    private val baseClientLazy = lazy { clientFactory() }
    private val baseClient: OkHttpClient by baseClientLazy
    @Volatile private var networkClient: Pair<Network, OkHttpClient>? = null

    val isRunning: Boolean
        get() = job?.isActive == true

    fun start(url: String, intervalSec: Int) {
        scope.launch {
            mutex.withLock {
                cancelJobLocked()
                snapshot = HuInternetStatusLogic.Snapshot()
                val effectiveUrl = HuInternetProbe.normalizeUrl(url)
                val intervalMs = HuInternetProbe.coerceIntervalSec(intervalSec) * 1000L
                job = scope.launch(Dispatchers.IO) {
                    while (isActive) {
                        probeOnce(effectiveUrl)
                        delay(intervalMs)
                    }
                }
            }
        }
    }

    fun stop() {
        scope.launch {
            mutex.withLock {
                cancelJobLocked()
                snapshot = HuInternetStatusLogic.Snapshot()
                TboxRepository.updateHuInternetStatus(HuInternetStatus.UNKNOWN)
            }
        }
    }

    private fun cancelJobLocked() {
        job?.cancel()
        job = null
        networkClient = null
        if (baseClientLazy.isInitialized()) runCatching { baseClient.connectionPool.evictAll() }
    }

    private fun probeOnce(url: String) {
        try {
            val client = clientForBestNetwork()
            val online = HuInternetProbe.probe(url, client)
            applyResult(online)
        } catch (e: Exception) {
            Log.w(TAG, "probe failed: ${e.message}")
            applyResult(online = false)
        }
    }

    private fun applyResult(online: Boolean) {
        snapshot = if (online) {
            HuInternetStatusLogic.onSuccess(snapshot)
        } else {
            HuInternetStatusLogic.onFailure(snapshot)
        }
        TboxRepository.updateHuInternetStatus(snapshot.status)
    }

    /**
     * Prefer a network that claims internet (validated first) so HU
     * "Wi‑Fi without internet" does not silently divert the probe.
     */
    private fun clientForBestNetwork(): OkHttpClient {
        val base = baseClient
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return base
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return base
        val best = pickBestNetwork(cm) ?: return base
        networkClient?.let { (network, client) -> if (network == best) return client }
        // Connections of the previous network would only time out.
        runCatching { base.connectionPool.evictAll() }
        return try {
            // newBuilder() shares the pool and dispatcher threads with the base client.
            base.newBuilder()
                .socketFactory(best.socketFactory)
                .build()
                .also { networkClient = best to it }
        } catch (_: Throwable) {
            base
        }
    }

    private fun pickBestNetwork(cm: ConnectivityManager): Network? {
        val networks = cm.allNetworks.toList()
        fun score(network: Network): Int {
            val caps = cm.getNetworkCapabilities(network) ?: return -1
            var s = 0
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) s += 2
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) s += 4
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) s += 1
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) s += 1
            return s
        }
        return networks.maxByOrNull { score(it) }?.takeIf { score(it) >= 0 }
    }

    companion object {
        private const val TAG = "HuInternetMonitor"
    }
}
