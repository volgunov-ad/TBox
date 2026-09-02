package vad.dashing.tbox.automation

/**
 * STA SSID helpers for automations. Android quotes SSIDs (`"home"`);
 * [NONE] is the published state when the radio is off or there is no association.
 */
object WifiStaSsid {
    const val NONE = "none"
    private const val UNKNOWN = "<unknown ssid>"

    fun normalize(raw: String?): String? {
        var value = raw?.trim().orEmpty()
        if (value.length >= 2 && value.startsWith('"') && value.endsWith('"')) {
            value = value.substring(1, value.length - 1).trim()
        }
        if (value.isEmpty()) return null
        if (value.equals(UNKNOWN, ignoreCase = true)) return null
        if (value.equals(NONE, ignoreCase = true)) return null
        return value
    }

    fun matches(left: String?, right: String?): Boolean {
        val a = normalize(left) ?: return false
        val b = normalize(right) ?: return false
        return a.equals(b, ignoreCase = true)
    }

    fun findSavedNetworkId(networks: List<Pair<Int, String?>>, wanted: String): Int? {
        val target = normalize(wanted) ?: return null
        return networks.firstOrNull { entry ->
            entry.first >= 0 && matches(entry.second, target)
        }?.first
    }

    fun uniqueSsids(networks: List<String?>): List<String> =
        networks.mapNotNull(::normalize).distinctBy { it.lowercase() }
}

data class WifiStaSnapshot(
    val radioEnabled: Boolean,
    val associated: Boolean,
    val ssid: String?,
) {
    fun radioState(): String = if (radioEnabled) "on" else "off"

    fun associatedState(): String = if (associated) "on" else "off"

    fun ssidState(): String = ssid ?: WifiStaSsid.NONE
}
