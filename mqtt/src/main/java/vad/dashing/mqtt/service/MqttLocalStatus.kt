package vad.dashing.mqtt.service

import org.json.JSONObject

/**
 * Localhost status HTTP for TBox Monitor.
 *
 * Convention: the listener exists only while [MqttBridgeService] is running.
 * Connection refused / no listener ⇒ APK installed but bridge service down.
 */
object MqttLocalStatus {
    /** Fixed loopback port; must stay in sync with Monitor (`MqttApkStatus.PORT`). */
    const val PORT = 8766
    const val BIND_HOST = "127.0.0.1"
    const val PATH = "/status"

    fun encode(status: BridgeStatus, serviceRunning: Boolean = true): String =
        JSONObject()
            .put("serviceRunning", serviceRunning)
            .put("monitorUp", status.monitorUp)
            .put("brokerUp", status.brokerUp)
            .put("wireguardEnabled", status.wireguardEnabled)
            .put("tunnelError", status.tunnelError)
            .put("brokerError", status.brokerError)
            .put("monitorError", status.monitorError)
            .put("lastError", status.lastError)
            .put("availability", status.availability)
            .toString()

    fun decode(body: String): LocalStatusSnapshot {
        val json = JSONObject(body)
        return LocalStatusSnapshot(
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
}

data class LocalStatusSnapshot(
    val serviceRunning: Boolean = true,
    val monitorUp: Boolean = false,
    val brokerUp: Boolean = false,
    val wireguardEnabled: Boolean = false,
    val tunnelError: String = "",
    val brokerError: String = "",
    val monitorError: String = "",
    val lastError: String = "",
    val availability: String = "offline",
)

enum class WireguardBridgeUi {
    OFF,
    UP,
    ERROR,
}

fun wireguardBridgeUi(
    wireguardEnabled: Boolean,
    tunnelError: String,
): WireguardBridgeUi = when {
    !wireguardEnabled -> WireguardBridgeUi.OFF
    tunnelError.isNotBlank() -> WireguardBridgeUi.ERROR
    else -> WireguardBridgeUi.UP
}
