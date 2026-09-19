package vad.dashing.tbox.automation

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

/**
 * Client-mode (STA) Wi-Fi for automations. SoftAP is out of scope as a feature,
 * but an active SoftAP often blocks [WifiManager.setWifiEnabled] on API 28.
 *
 * On API 29+ AOSP rejects [WifiManager.setWifiEnabled] for apps with targetSdk ≥ 29;
 * we still try OEM paths and a [Settings.Global.WIFI_ON] write when
 * [Manifest.permission.WRITE_SECURE_SETTINGS] is granted.
 */
object WifiStaController {
    private const val TAG = "WifiSta"
    private const val RADIO_WAIT_MS = 12_000L
    private const val ASSOCIATE_WAIT_MS = 15_000L
    private const val POLL_MS = 200L
    private const val SOFT_AP_SETTLE_MS = 700L

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
        val app = context.applicationContext
        val wifi = wifiManager(app)
            ?: return AutomationActionResult.failure("Wi-Fi недоступен на этом устройстве")
        if (isRadioEnabled(wifi) == enabled) {
            return AutomationActionResult.ok(if (enabled) "Wi-Fi уже включён" else "Wi-Fi уже выключен")
        }

        var softApStopped = false
        var accepted = setWifiEnabledCompat(wifi, enabled)
        if (!accepted && isWifiApEnabledCompat(wifi)) {
            Log.i(TAG, "setWifiEnabled($enabled) rejected; stopping SoftAP and retrying")
            softApStopped = stopSoftApCompat(app, wifi)
            if (softApStopped) {
                delay(SOFT_AP_SETTLE_MS)
                accepted = setWifiEnabledCompat(wifi, enabled)
            }
        }
        if (!accepted) {
            // Some HUs honour WIFI_ON when WRITE_SECURE_SETTINGS is granted.
            if (writeWifiOnSetting(app, enabled)) {
                Log.i(TAG, "wrote Settings.Global.WIFI_ON=${if (enabled) 1 else 0}; retry setWifiEnabled")
                setWifiEnabledCompat(wifi, enabled)
                accepted = true // wait for radio state even if the API still returns false
            }
        }

        if (!accepted) {
            return AutomationActionResult.failure(
                WifiStaToggleDiagnostics.failureMessage(
                    airplane = isAirplaneModeOn(app),
                    softApEnabled = isWifiApEnabledCompat(wifi),
                    sdkInt = Build.VERSION.SDK_INT,
                ),
            )
        }

        val ok = waitUntil(RADIO_WAIT_MS) { isRadioEnabled(wifi) == enabled }
        return if (ok) {
            val base = if (enabled) "Wi-Fi включён" else "Wi-Fi выключен"
            val suffix = if (softApStopped) " (точка доступа ГУ выключена)" else ""
            AutomationActionResult.ok(base + suffix)
        } else {
            AutomationActionResult.failure(
                "Таймаут переключения Wi-Fi" +
                    if (isAirplaneModeOn(app)) " (режим полёта)" else "",
            )
        }
    }

    suspend fun connectToSaved(context: Context, ssid: String): AutomationActionResult {
        val wanted = WifiStaSsid.normalize(ssid)
            ?: return AutomationActionResult.failure("Выберите сохранённую сеть")
        val wifi = wifiManager(context)
            ?: return AutomationActionResult.failure("Wi-Fi недоступен на этом устройстве")
        if (!isRadioEnabled(wifi)) {
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
        val wifi = wifiManager(context)
            ?: return AutomationActionResult.failure("Wi-Fi недоступен на этом устройстве")
        if (!isRadioEnabled(wifi)) {
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
        val enabled = isRadioEnabled(wifi)
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

    @Suppress("DEPRECATION")
    private fun isRadioEnabled(wifi: WifiManager): Boolean =
        runCatching { wifi.isWifiEnabled }.getOrDefault(false)

    private fun isAirplaneModeOn(context: Context): Boolean =
        runCatching {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.AIRPLANE_MODE_ON,
                0,
            ) != 0
        }.getOrDefault(false)

    private fun hasWriteSecureSettings(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_SECURE_SETTINGS,
        ) == PackageManager.PERMISSION_GRANTED

    private fun writeWifiOnSetting(context: Context, enabled: Boolean): Boolean {
        if (!hasWriteSecureSettings(context)) return false
        return runCatching {
            Settings.Global.putInt(
                context.contentResolver,
                Settings.Global.WIFI_ON,
                if (enabled) 1 else 0,
            )
            true
        }.getOrDefault(false)
    }

    @Suppress("DEPRECATION")
    private fun setWifiEnabledCompat(wifi: WifiManager, enabled: Boolean): Boolean {
        return runCatching { wifi.setWifiEnabled(enabled) }
            .onFailure { e -> Log.w(TAG, "setWifiEnabled($enabled) threw", e) }
            .getOrDefault(false)
            .also { ok ->
                if (!ok) Log.w(TAG, "setWifiEnabled($enabled) returned false")
            }
    }

    @Suppress("DEPRECATION")
    private fun isWifiApEnabledCompat(wifi: WifiManager): Boolean {
        return runCatching {
            val method = wifi.javaClass.getMethod("isWifiApEnabled")
            method.invoke(wifi) as? Boolean ?: false
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun stopSoftApCompat(context: Context, wifi: WifiManager): Boolean {
        val viaWifiManager = runCatching {
            val method = wifi.javaClass.getMethod(
                "setWifiApEnabled",
                WifiConfiguration::class.java,
                Boolean::class.javaPrimitiveType,
            )
            method.invoke(wifi, null, false) as? Boolean ?: false
        }.onFailure { e -> Log.w(TAG, "setWifiApEnabled(false) failed", e) }
            .getOrDefault(false)
        if (viaWifiManager) return true

        // ConnectivityManager.stopTethering is @SystemApi on recent SDKs — call via reflection.
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        return runCatching {
            val tetherWifi = ConnectivityManager::class.java.getField("TETHERING_WIFI").getInt(null)
            val stop = ConnectivityManager::class.java.getMethod("stopTethering", Int::class.javaPrimitiveType)
            stop.invoke(cm, tetherWifi)
            true
        }.onFailure { e -> Log.w(TAG, "stopTethering(WIFI) failed", e) }
            .getOrDefault(false)
    }

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
