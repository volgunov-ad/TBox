package vad.dashing.tbox.internet

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
import okhttp3.OkHttpClient
import vad.dashing.tbox.TboxRepository

/**
 * Periodically probes a user-configured URL to decide whether the head unit has working internet.
 * Publishes [HuInternetStatus] into [TboxRepository].
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

    val isRunning: Boolean
        get() = job?.isActive == true

    fun start(url: String, intervalSec: Int) {
        scope.launch {
            mutex.withLock {
                cancelJobLocked()
                val effectiveUrl = HuInternetProbe.normalizeUrl(url)
                val intervalMs = HuInternetProbe.coerceIntervalSec(intervalSec) * 1000L
                job = scope.launch {
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
                TboxRepository.updateHuInternetStatus(HuInternetStatus.UNKNOWN)
            }
        }
    }

    private fun cancelJobLocked() {
        job?.cancel()
        job = null
    }

    private fun probeOnce(url: String) {
        TboxRepository.updateHuInternetStatus(HuInternetStatus.CHECKING)
        try {
            val client = clientForBestNetwork()
            val online = HuInternetProbe.probe(url, client)
            TboxRepository.updateHuInternetStatus(
                if (online) HuInternetStatus.ONLINE else HuInternetStatus.OFFLINE,
            )
        } catch (e: Exception) {
            Log.w(TAG, "probe failed: ${e.message}")
            TboxRepository.updateHuInternetStatus(HuInternetStatus.OFFLINE)
        }
    }

    /**
     * Prefer a network that claims internet (validated first) so HU
     * "Wi‑Fi without internet" does not silently divert the probe.
     */
    private fun clientForBestNetwork(): OkHttpClient {
        val base = clientFactory()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return base
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return base
        val best = pickBestNetwork(cm) ?: return base
        return try {
            base.newBuilder()
                .socketFactory(best.socketFactory)
                .build()
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
