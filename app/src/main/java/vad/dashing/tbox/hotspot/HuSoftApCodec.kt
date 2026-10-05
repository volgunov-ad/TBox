package vad.dashing.tbox.hotspot

import java.util.Base64

internal data class HuSoftApRead(
    val enabled: Boolean,
    val ssid: String,
    val password: String,
    val band: Int,
    val channel: Int,
    val authType: Int,
    val frequencyMhz: Int?,
    /** Frequency hostapd is actually on. 0 when dumpsys did not report it. */
    val liveMhz: Int = 0,
)

internal data class SoftApIface(
    val name: String,
    val ipv4: String,
)

internal object HuSoftApCodec {
    const val MAIN_CLASS = "vad.dashing.tbox.hotspot.HuSoftApMain"

    fun shellCommand(apkPath: String, action: String): String {
        require(action == "get" || action == "up" || action == "down" || action == "band24") {
            "action"
        }
        val quoted = "'" + apkPath.replace("'", "'\\''") + "'"
        return "CLASSPATH=$quoted app_process /system/bin $MAIN_CLASS $action"
    }

    fun parse(stdout: String): HuSoftApRead? {
        val fields = resultFields(stdout) ?: return null
        if (fields["ok"] != "1" || fields["action"] != null) return null
        val band = fields["band"]?.toIntOrNull() ?: -1
        val channel = fields["channel"]?.toIntOrNull() ?: 0
        val liveMhz = fields["live"]?.toIntOrNull() ?: 0
        return HuSoftApRead(
            enabled = fields["enabled"] == "1",
            ssid = unquoteSsid(decodeField(fields["ssidB64"])),
            password = decodeField(fields["pskB64"]),
            band = band,
            channel = channel,
            authType = fields["auth"]?.toIntOrNull() ?: -1,
            frequencyMhz = if (liveMhz > 0) liveMhz else frequencyMhz(band, channel),
            liveMhz = liveMhz,
        )
    }

    fun parseActionOk(stdout: String): Boolean? {
        val fields = resultFields(stdout) ?: return null
        if (fields["action"] == null) return null
        return fields["ok"] == "1"
    }

    fun errorCode(stdout: String): String? {
        val fields = resultFields(stdout) ?: return null
        if (fields["ok"] == "1") return null
        return fields["err"] ?: "failed"
    }

    /** True when hostapd is beaconing on 2.4 GHz. A stored band of 0 does not prove that. */
    fun on24Ghz(liveMhz: Int): Boolean = liveMhz in 2412..2484

    fun channelOfMhz(mhz: Int): Int? {
        if (mhz == 2484) return 14
        if (mhz in 2412..2472) {
            val delta = mhz - 2407
            if (delta % 5 != 0) return null
            return (delta / 5).takeIf { it in 1..13 }
        }
        if (mhz in 5000..5885) {
            val delta = mhz - 5000
            if (delta % 5 != 0) return null
            return (delta / 5).takeIf { it in 1..196 }
        }
        return null
    }

    fun frequencyMhz(band: Int, channel: Int): Int? {
        if (channel <= 0) return null
        return when (band) {
            0 -> if (channel == 14) 2484 else 2407 + channel * 5
            1 -> 5000 + channel * 5
            else -> null
        }
    }

    fun selectHotspotIpv4(ifaces: List<SoftApIface>): String? {
        val preferred = listOf("wlan1", "ap0", "softap0", "swlan0", "wlan2")
        for (name in preferred) {
            val match = ifaces.firstOrNull { it.name.equals(name, ignoreCase = true) && it.ipv4.isNotEmpty() }
            if (match != null) return match.ipv4
        }
        return ifaces.firstOrNull { ipv4InTetherPool(it.ipv4) }?.ipv4
    }

    fun ipv4InTetherPool(ip: String): Boolean {
        val parts = ip.split('.')
        if (parts.size != 4 || parts[0] != "192" || parts[1] != "168") return false
        val third = parts[2].toIntOrNull() ?: return false
        return third in 42..49
    }

    /** Wi-Fi QR (WPA, SAE, or open). Special characters in SSID and password are escaped. */
    fun wifiQrPayload(ssid: String, password: String, authType: Int): String {
        val security = when (authType) {
            8 -> "SAE"
            0, 9 -> "nopass"
            else -> "WPA"
        }
        val passwordField = if (security == "nopass") "" else ";P:${escapeWifiQr(password)}"
        return "WIFI:T:$security;S:${escapeWifiQr(ssid)}$passwordField;;"
    }

    private fun escapeWifiQr(value: String): String = buildString(value.length) {
        for (ch in value) {
            if (ch == '\\' || ch == ';' || ch == ',' || ch == ':' || ch == '"') {
                append('\\')
            }
            append(ch)
        }
    }

    private fun resultFields(stdout: String): Map<String, String>? {
        val line = stdout.lineSequence()
            .map { it.trim() }
            .lastOrNull { it.startsWith("RESULT ") }
            ?: return null
        return line.removePrefix("RESULT ")
            .split(' ')
            .mapNotNull { token ->
                val split = token.indexOf('=')
                if (split <= 0) null else token.substring(0, split) to token.substring(split + 1)
            }
            .toMap()
    }

    private fun decodeField(value: String?): String {
        if (value.isNullOrEmpty() || value == "-") return ""
        return try {
            String(Base64.getDecoder().decode(value), Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            ""
        }
    }

    private fun unquoteSsid(ssid: String): String {
        if (ssid.length >= 2 && ssid.startsWith("\"") && ssid.endsWith("\"")) {
            return ssid.substring(1, ssid.length - 1)
        }
        return ssid
    }
}
