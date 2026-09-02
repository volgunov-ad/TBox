package vad.dashing.tbox.automation

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

/**
 * Client-mode (STA) Wi-Fi for automations. SoftAP is out of scope.
 * Enable/disable and enableNetwork are the API 28 WifiManager surface;
 * they are rejected on API 29+.
 */
object WifiStaController {
    private const val RADIO_WAIT_MS = 8_000L
    private const val ASSOCIATE_WAIT_MS = 15_000L
    private const val POLL_MS = 200L

    fun snapshots(context: Context): Flow<WifiStaSnapshot> {
        val app = context.applicationContext
        val wifi = wifiManager(app) ?: return flowOf(radioOffSnapshot())
        return callbackFlow {
            fun emitNow() {
                trySend(readSnapshot(wifi))
            }
            emitNow()
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    emitNow()
                }
            }
            val filter = IntentFilter().apply {
                addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)
            }
            app.registerReceiver(receiver, filter)
            awaitClose {
                runCatching { app.unregisterReceiver(receiver) }
            }
        }.distinctUntilChanged()
    }

    fun snapshot(context: Context): WifiStaSnapshot {
        val wifi = wifiManager(context) ?: return radioOffSnapshot()
        return readSnapshot(wifi)
    }

    fun savedSsids(context: Context): List<String> =
        WifiStaSsid.uniqueSsids(configuredNetworks(context).map { it.second })

    suspend fun setRadioEnabled(context: Context, enabled: Boolean): AutomationActionResult {
        val blocked = api29Message("включение и выключение Wi-Fi")
        if (blocked != null) return AutomationActionResult.failure(blocked)
        val wifi = wifiManager(context)
            ?: return AutomationActionResult.failure("Wi-Fi недоступен на этом устройстве")
        val current = wifi.isWifiEnabled
        if (current == enabled) {
            return AutomationActionResult.ok(if (enabled) "Wi-Fi уже включён" else "Wi-Fi уже выключен")
        }
        if (!setWifiEnabledCompat(wifi, enabled)) {
            return AutomationActionResult.failure("Не удалось переключить Wi-Fi")
        }
        val ok = waitUntil(RADIO_WAIT_MS) { wifi.isWifiEnabled == enabled }
        return if (ok) {
            AutomationActionResult.ok(if (enabled) "Wi-Fi включён" else "Wi-Fi выключен")
        } else {
            AutomationActionResult.failure("Таймаут переключения Wi-Fi")
        }
    }

    suspend fun connectToSaved(context: Context, ssid: String): AutomationActionResult {
        val blocked = api29Message("подключение к сохранённой сети")
        if (blocked != null) return AutomationActionResult.failure(blocked)
        val wanted = WifiStaSsid.normalize(ssid)
            ?: return AutomationActionResult.failure("Выберите сохранённую сеть")
        val wifi = wifiManager(context)
            ?: return AutomationActionResult.failure("Wi-Fi недоступен на этом устройстве")
        if (!wifi.isWifiEnabled) {
            val enabled = setRadioEnabled(context, true)
            if (!enabled.success) return enabled
        }
        val already = readSnapshot(wifi)
        if (already.associated && WifiStaSsid.matches(already.ssid, wanted)) {
            return AutomationActionResult.ok("Уже подключено к $wanted")
        }
        val netId = WifiStaSsid.findSavedNetworkId(configuredNetworks(context), wanted)
            ?: return AutomationActionResult.failure("Сеть «$wanted» не сохранена на ГУ")
        if (!enableNetworkCompat(wifi, netId) || !reconnectCompat(wifi)) {
            return AutomationActionResult.failure("Не удалось запросить подключение к $wanted")
        }
        val ok = waitUntil(ASSOCIATE_WAIT_MS) {
            val snap = readSnapshot(wifi)
            snap.associated && WifiStaSsid.matches(snap.ssid, wanted)
        }
        return if (ok) {
            AutomationActionResult.ok("Подключено к $wanted")
        } else {
            AutomationActionResult.failure("Таймаут подключения к $wanted")
        }
    }

    fun disconnectCurrent(context: Context): AutomationActionResult {
        val blocked = api29Message("отключение от сети")
        if (blocked != null) return AutomationActionResult.failure(blocked)
        val wifi = wifiManager(context)
            ?: return AutomationActionResult.failure("Wi-Fi недоступен на этом устройстве")
        if (!wifi.isWifiEnabled) {
            return AutomationActionResult.ok("Wi-Fi выключен")
        }
        val snap = readSnapshot(wifi)
        if (!snap.associated) {
            return AutomationActionResult.ok("Уже нет подключения к точке доступа")
        }
        val netId = currentNetworkId(wifi)
        // disconnect() alone lets the stack immediately rejoin the same saved network.
        if (netId >= 0) {
            disableNetworkCompat(wifi, netId)
        }
        disconnectCompat(wifi)
        return AutomationActionResult.ok("Отключено от точки доступа")
    }

    private fun radioOffSnapshot(): WifiStaSnapshot =
        WifiStaSnapshot(radioEnabled = false, associated = false, ssid = null)

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun readSnapshot(wifi: WifiManager): WifiStaSnapshot {
        val enabled = runCatching { wifi.isWifiEnabled }.getOrDefault(false)
        if (!enabled) return radioOffSnapshot()
        val info = runCatching { wifi.connectionInfo }.getOrNull()
        val ssid = WifiStaSsid.normalize(info?.ssid)
        val networkId = info?.networkId ?: -1
        val associated = networkId >= 0 && ssid != null
        return WifiStaSnapshot(
            radioEnabled = true,
            associated = associated,
            ssid = ssid.takeIf { associated },
        )
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun configuredNetworks(context: Context): List<Pair<Int, String?>> {
        val wifi = wifiManager(context) ?: return emptyList()
        val configs = runCatching { wifi.configuredNetworks }.getOrNull().orEmpty()
        return configs.map { config -> config.networkId to config.SSID }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun currentNetworkId(wifi: WifiManager): Int =
        runCatching { wifi.connectionInfo?.networkId ?: -1 }.getOrDefault(-1)

    private fun wifiManager(context: Context): WifiManager? =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private fun api29Message(what: String): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "На Android 10+ $what из приложения недоступно"
        } else {
            null
        }

    @Suppress("DEPRECATION")
    private fun setWifiEnabledCompat(wifi: WifiManager, enabled: Boolean): Boolean =
        runCatching { wifi.setWifiEnabled(enabled) }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun enableNetworkCompat(wifi: WifiManager, netId: Int): Boolean =
        runCatching { wifi.enableNetwork(netId, true) }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun disableNetworkCompat(wifi: WifiManager, netId: Int): Boolean =
        runCatching { wifi.disableNetwork(netId) }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun reconnectCompat(wifi: WifiManager): Boolean =
        runCatching { wifi.reconnect() }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun disconnectCompat(wifi: WifiManager): Boolean =
        runCatching { wifi.disconnect() }.getOrDefault(false)

    private suspend fun waitUntil(timeoutMs: Long, predicate: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) return true
            delay(POLL_MS)
        }
        return predicate()
    }
}
