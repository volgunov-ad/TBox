package vad.dashing.mqtt.ha

object Topics {
    const val DEFAULT_DEVICE_ID = "dashing"
    const val DEFAULT_TOPIC_PREFIX = "tbox"
    const val DEFAULT_DISCOVERY_PREFIX = "homeassistant"
    const val DEFAULT_DEVICE_NAME = "Jetour Dashing"
    private const val MAX_OBJECT_ID = 96
    private val OBJECT_ID = Regex("[A-Za-z0-9_-]+")

    fun normalizeDeviceId(raw: String): String {
        val cleaned = raw.trim().lowercase()
            .replace(Regex("[^a-z0-9_]"), "_")
            .trim('_')
        return cleaned.ifEmpty { DEFAULT_DEVICE_ID }.take(32)
    }

    fun normalizePrefix(raw: String, fallback: String): String {
        val cleaned = raw.trim().trim('/').lowercase()
            .replace(Regex("[^a-z0-9_/]"), "_")
            .replace(Regex("/+"), "/")
            .trim('/')
        return cleaned.ifEmpty { fallback }
    }

    /** Keeps valid ids as is, so saved selections survive; others get a slug plus a hash. */
    fun safeObjectId(raw: String): String {
        if (raw.isNotEmpty() && raw.length <= MAX_OBJECT_ID && OBJECT_ID.matches(raw)) return raw
        val slug = raw.replace(Regex("[^A-Za-z0-9_-]"), "_").trim('_').take(MAX_OBJECT_ID - 9)
        val hash = "%08x".format(raw.hashCode())
        return if (slug.isEmpty()) "id_$hash" else "${slug}_$hash"
    }

    fun deviceName(raw: String): String =
        raw.trim().ifEmpty { DEFAULT_DEVICE_NAME }.take(64)

    fun base(topicPrefix: String, deviceId: String): String =
        "${normalizePrefix(topicPrefix, DEFAULT_TOPIC_PREFIX)}/${normalizeDeviceId(deviceId)}"

    fun status(base: String): String = "$base/status"

    fun commands(base: String): String = "$base/commands"

    fun state(base: String, objectId: String): String = "$base/$objectId/state"

    fun set(base: String, objectId: String): String = "$base/$objectId/set"

    fun attributes(base: String, objectId: String): String = "$base/$objectId/attributes"

    fun lastSeen(base: String): String = "$base/last_seen/state"

    fun discoveryConfig(
        discoveryPrefix: String,
        component: String,
        deviceId: String,
        objectId: String,
    ): String {
        val prefix = normalizePrefix(discoveryPrefix, DEFAULT_DISCOVERY_PREFIX)
        val id = normalizeDeviceId(deviceId)
        return "$prefix/$component/tbox_$id/$objectId/config"
    }

    fun haStatus(discoveryPrefix: String): String =
        "${normalizePrefix(discoveryPrefix, DEFAULT_DISCOVERY_PREFIX)}/status"

    fun deviceIdentifier(deviceId: String): String = "tbox_${normalizeDeviceId(deviceId)}"

    fun uniqueId(deviceId: String, objectId: String): String =
        "tbox_${normalizeDeviceId(deviceId)}_$objectId"

    fun mqttClientId(explicit: String, deviceId: String): String {
        val raw = explicit.trim().ifEmpty { "tbox-mqtt-${normalizeDeviceId(deviceId)}" }
        val cleaned = raw.replace(Regex("[^A-Za-z0-9_-]"), "_").take(64)
        return cleaned.ifEmpty { "tbox-mqtt-${normalizeDeviceId(deviceId)}" }
    }
}
