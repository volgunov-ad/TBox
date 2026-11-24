package vad.dashing.mqtt.ha

/**
 * A selected id that disappears from one catalog load is kept. Home Assistant deletes an
 * entity, including its name and area, when the retained config is cleared.
 */
object MissingEntityPolicy {
    const val RELOADS = 3
    const val GRACE_MS = 30L * 60L * 1000L

    fun keep(consecutiveMisses: Int, missingForMs: Long): Boolean =
        consecutiveMisses < RELOADS && missingForMs < GRACE_MS
}

/** `homeassistant/sensor/tbox_dashing/drive_mode/config` → `drive_mode`. */
internal fun discoveryObjectId(topic: String): String? {
    val marker = "/config"
    if (!topic.endsWith(marker)) return null
    val slash = topic.lastIndexOf('/', topic.length - marker.length - 1)
    if (slash < 0 || slash >= topic.length - marker.length - 1) return null
    return topic.substring(slash + 1, topic.length - marker.length)
}
