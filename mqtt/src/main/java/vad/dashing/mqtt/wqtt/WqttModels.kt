package vad.dashing.mqtt.wqtt

/** One entry from `GET /api/devices/types` (device / range / sensor_float / …). */
data class WqttNamedType(
    val id: Int,
    val name: String,
)

data class WqttTypeCatalog(
    val devices: List<WqttNamedType> = emptyList(),
    val ranges: List<WqttNamedType> = emptyList(),
    val sensorFloats: List<WqttNamedType> = emptyList(),
    val sensorEvents: List<WqttNamedType> = emptyList(),
    val modes: List<WqttNamedType> = emptyList(),
    val toggles: List<WqttNamedType> = emptyList(),
    val colors: List<WqttNamedType> = emptyList(),
)

data class WqttBrokerInfo(
    val server: String,
    val port: Int,
    val portTls: Int,
    val websocket: Int,
    val user: String,
    val password: String,
)

data class WqttDeviceSummary(
    val id: Int,
    val type: Int,
    val name: String,
    val room: String,
)

data class WqttDeviceDetail(
    val id: Int,
    val type: Int,
    val name: String,
    val room: String,
    val topicStates: Set<String>,
    val topicCmds: Set<String>,
)

data class WqttOnOff(
    val topicCmd: String = "",
    val topicState: String = "",
    val cmdOn: String = "ON",
    val cmdOff: String = "OFF",
)

data class WqttRange(
    val type: Int,
    val topicCmd: String = "",
    val topicState: String = "",
    val min: Double,
    val max: Double,
    val precision: Double = 1.0,
    val multiplier: Double = 1.0,
)

data class WqttSensorFloat(
    val type: Int,
    val topicState: String,
)

data class WqttSensorEvent(
    val type: Int,
    val topicState: String,
)

/**
 * Body for POST/PUT `/api/devices`.
 * [stableKey] is local only (not sent): used for idempotent match and progress rows.
 */
data class WqttDevicePayload(
    val stableKey: String,
    val name: String,
    val type: Int,
    val room: String,
    val onOff: List<WqttOnOff> = emptyList(),
    val range: List<WqttRange> = emptyList(),
    val sensorFloat: List<WqttSensorFloat> = emptyList(),
    val sensorEvent: List<WqttSensorEvent> = emptyList(),
    /** TBox MQTT objectIds that must be selected so state/commands actually publish. */
    val objectIds: Set<String> = emptySet(),
)

enum class WqttDeviceAction {
    CREATED,
    UPDATED,
    SKIPPED,
    FAILED,
}

data class WqttDeviceResult(
    val stableKey: String,
    val name: String,
    val action: WqttDeviceAction,
    val deviceId: Int? = null,
    val message: String = "",
)

data class WqttProvisionReport(
    val devices: List<WqttDeviceResult>,
    val refreshed: Boolean,
    val refreshMessage: String = "",
    val enabledObjectIds: Set<String> = emptySet(),
    val skipped: List<WqttSkippedEntity> = emptyList(),
)

data class WqttCleanupCandidate(
    val id: Int,
    val name: String,
    val room: String,
)

data class WqttCleanupReport(
    val deleted: Int,
    val failed: Int,
    val candidates: List<WqttCleanupCandidate> = emptyList(),
    val refreshed: Boolean = false,
    val refreshMessage: String = "",
    val errors: List<String> = emptyList(),
)

class WqttApiException(
    val httpStatus: Int,
    message: String,
) : Exception(message)
