package vad.dashing.tbox.mqtt

import android.content.pm.PackageManager
import android.os.Build
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Localhost status from TBox MQTT (`vad.dashing.mqtt`).
 *
 * The MQTT bridge serves GET [PATH] on [BIND_HOST]:[PORT] only while its foreground
 * service is running. Connection refused ⇒ package may be installed but service down.
 * Port must match `:mqtt` [vad.dashing.mqtt.service.MqttLocalStatus.PORT].
 */
object MqttApkStatus {
    const val PACKAGE_NAME = "vad.dashing.mqtt"
    const val PORT = 8766
    const val BIND_HOST = "127.0.0.1"
    const val PATH = "/status"
    const val POLL_MS = 2_000L

    fun isInstalled(packageManager: PackageManager): Boolean =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(PACKAGE_NAME, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(PACKAGE_NAME, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    fun statusUrl(port: Int = PORT): String = "http://$BIND_HOST:$port$PATH"

    fun decode(body: String): MqttBridgeStatusSnapshot {
        val json = JSONObject(body)
        return MqttBridgeStatusSnapshot(
            serviceRunning = json.optBoolean("serviceRunning", true),
            monitorUp = json.optBoolean("monitorUp"),
            brokerUp = json.optBoolean("brokerUp"),
            wireguardEnabled = json.optBoolean("wireguardEnabled"),
            tunnelError = json.optString("tunnelError"),
            brokerError = json.optString("brokerError"),
            monitorError = json.optString("monitorError"),
            lastError = json.optString("lastError"),
            availability = json.optString("availability", "offline"),
        )
    }

    fun encode(snapshot: MqttBridgeStatusSnapshot): String =
        JSONObject()
            .put("serviceRunning", snapshot.serviceRunning)
            .put("monitorUp", snapshot.monitorUp)
            .put("brokerUp", snapshot.brokerUp)
            .put("wireguardEnabled", snapshot.wireguardEnabled)
            .put("tunnelError", snapshot.tunnelError)
            .put("brokerError", snapshot.brokerError)
            .put("monitorError", snapshot.monitorError)
            .put("lastError", snapshot.lastError)
            .put("availability", snapshot.availability)
            .toString()

    fun fetch(
        client: OkHttpClient = defaultClient(),
        port: Int = PORT,
    ): MqttBridgePollResult {
        return try {
            val request = Request.Builder()
                .url(statusUrl(port))
                .header("Accept", "application/json")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return MqttBridgePollResult.ServiceDown(
                        "HTTP ${response.code}",
                    )
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    return MqttBridgePollResult.ServiceDown("empty body")
                }
                MqttBridgePollResult.Ok(decode(body).copy(serviceRunning = true))
            }
        } catch (error: Exception) {
            MqttBridgePollResult.ServiceDown(error.message ?: error.javaClass.simpleName)
        }
    }

    fun defaultClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(800, TimeUnit.MILLISECONDS)
            .readTimeout(1_200, TimeUnit.MILLISECONDS)
            .writeTimeout(800, TimeUnit.MILLISECONDS)
            .callTimeout(2_000, TimeUnit.MILLISECONDS)
            .build()
}

data class MqttBridgeStatusSnapshot(
    val serviceRunning: Boolean = false,
    val monitorUp: Boolean = false,
    val brokerUp: Boolean = false,
    val wireguardEnabled: Boolean = false,
    val tunnelError: String = "",
    val brokerError: String = "",
    val monitorError: String = "",
    val lastError: String = "",
    val availability: String = "offline",
)

sealed class MqttBridgePollResult {
    data class Ok(val status: MqttBridgeStatusSnapshot) : MqttBridgePollResult()
    data class ServiceDown(val detail: String = "") : MqttBridgePollResult()
}

enum class MqttWireguardUi {
    OFF,
    UP,
    ERROR,
}

fun mqttWireguardUi(
    wireguardEnabled: Boolean,
    tunnelError: String,
): MqttWireguardUi = when {
    !wireguardEnabled -> MqttWireguardUi.OFF
    tunnelError.isNotBlank() -> MqttWireguardUi.ERROR
    else -> MqttWireguardUi.UP
}

/** Short error text under a fail row; empty when nothing useful to show. */
fun mqttRowDetail(
    up: Boolean,
    primaryError: String,
    fallback: String = "",
): String {
    if (up) return ""
    return primaryError.trim().ifBlank { fallback.trim() }
}
