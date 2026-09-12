package vad.dashing.tbox.wifimodem

import vad.dashing.tbox.APNState
import vad.dashing.tbox.NetState
import vad.dashing.tbox.NetValues
import java.util.Date

/**
 * Maps ZTE `/reqproc/proc_get` JSON fields into app modem models.
 *
 * Field names follow the common Demo-Webs / Olax MiFi set (M100, ZLT, etc.).
 * Empty strings mean "not populated on this firmware".
 */
object ZteReqprocStatusMapper {

    /** Preferred multi-read cmd list for a status poll. */
    val STATUS_CMDS: List<String> = listOf(
        "network_type",
        "sub_network_type",
        "rssi",
        "signalbar",
        "lte_rsrp",
        "lte_rsrq",
        "nv_rsrq",
        "lte_snr",
        "nv_sinr",
        "lte_band",
        "cell_id",
        "network_provider",
        "imei",
        "sim_imsi",
        "imsi",
        "ziccid",
        "iccid",
        "ppp_status",
        "modem_main_state",
        "simcard_roam",
        "wan_ipaddr",
        "wan_ip",
        "cr_version",
        "wa_inner_version",
    )

    fun map(fields: Map<String, String>, previous: WifiModemSnapshot? = null): WifiModemSnapshot {
        val networkTypeRaw = firstNonBlank(fields, "network_type", "sub_network_type")
        val netStatus = mapNetworkType(networkTypeRaw)
        val signalBar = fields["signalbar"]?.toIntOrNull()
        val rssi = fields["rssi"]?.toIntOrNull()
        val csq = csqFromRssi(rssi)
        val signalLevel = when {
            signalBar != null -> signalBarToLevel(signalBar)
            csq != null -> csqToSignalLevel(csq)
            else -> 0
        }
        val pppRaw = fields["ppp_status"].orEmpty()
        val apnUp = isPppConnected(pppRaw)
        val modemMain = fields["modem_main_state"].orEmpty()
        val roam = fields["simcard_roam"].orEmpty()
        val regStatus = mapRegStatus(roam, modemMain, netStatus)
        val simStatus = mapSimStatus(modemMain, fields)
        val operator = fields["network_provider"]?.trim().orEmpty().ifBlank { "-" }
        val imei = fields["imei"]?.trim().orEmpty().ifBlank { "-" }
        val imsi = firstNonBlank(fields, "sim_imsi", "imsi").ifBlank { "-" }
        val iccid = firstNonBlank(fields, "ziccid", "iccid").ifBlank { "-" }
        val wanIp = firstNonBlank(fields, "wan_ipaddr", "wan_ip")
        val firmware = firstNonBlank(fields, "cr_version", "wa_inner_version")

        val prevNet = previous?.netState
        val connectionChangeTime = when {
            prevNet == null -> Date()
            prevNet.regStatus != regStatus -> Date()
            else -> prevNet.connectionChangeTime
        }

        val netState = NetState(
            csq = csq ?: 99,
            signalLevel = signalLevel,
            netStatus = netStatus,
            regStatus = regStatus,
            simStatus = simStatus,
            connectionChangeTime = connectionChangeTime,
        )
        val netValues = NetValues(
            imei = imei,
            iccid = iccid,
            imsi = imsi,
            operator = operator,
        )
        val apnState = APNState(
            apnStatus = apnUp,
            apnType = if (apnUp) "LTE" else "",
            apnIP = wanIp,
            changeTime = when {
                previous == null -> Date()
                previous.apnStatus != apnUp -> Date()
                else -> previous.apnState.changeTime
            },
        )

        return WifiModemSnapshot(
            netState = netState,
            netValues = netValues,
            apnState = apnState,
            apnStatus = apnUp,
            firmware = firmware,
            rssiDbm = rssi,
            rsrpDbm = fields["lte_rsrp"]?.toIntOrNull(),
            rsrqDb = firstNonBlank(fields, "lte_rsrq", "nv_rsrq").toIntOrNull(),
            sinrDb = firstNonBlank(fields, "lte_snr", "nv_sinr").toIntOrNull(),
            lteBand = fields["lte_band"].orEmpty(),
            cellId = fields["cell_id"].orEmpty(),
            networkTypeRaw = networkTypeRaw,
            pppStatusRaw = pppRaw,
            modemMainStateRaw = modemMain,
        )
    }

    /**
     * Parses a flat JSON object of string values (typical `proc_get` multi_data response).
     * Non-string JSON values are stringified; nested objects are ignored.
     */
    fun fieldsFromJsonObject(json: String): Map<String, String> {
        // Lightweight parse without bringing org.json into tests of pure maps:
        // callers with Android/OkHttp can pass already-decoded maps; this helper
        // accepts a minimal {"k":"v",...} shape used in fixtures.
        return parseFlatStringJson(json)
    }

    internal fun mapNetworkType(raw: String): String {
        val u = raw.trim().uppercase()
        if (u.isEmpty()) return "-"
        return when {
            u.contains("LTE") || u.contains("4G") || u == "FDD_LTE" || u == "TDD_LTE" -> "4G"
            u.contains("WCDMA") || u.contains("HSPA") || u.contains("UMTS") ||
                u.contains("3G") || u == "TD_W" -> "3G"
            u.contains("GSM") || u.contains("GPRS") || u.contains("EDGE") ||
                u.contains("2G") || u.contains("CDMA") -> "2G"
            u.contains("LIMITED") || u.contains("NO_") || u == "NO SERVICE" -> "нет сети"
            else -> "-"
        }
    }

    /** Device UI bars are 0–5; dashboard widgets use 0–4. */
    internal fun signalBarToLevel(bar: Int): Int = when {
        bar <= 0 -> 0
        bar == 1 -> 1
        bar == 2 -> 2
        bar == 3 -> 3
        else -> 4 // 4 or 5
    }

    /** Same buckets as [vad.dashing.tbox.BackgroundService] MDC path. */
    internal fun csqToSignalLevel(csq: Int): Int = when (csq) {
        in 1..10 -> 1
        in 11..16 -> 2
        in 17..24 -> 3
        in 25..32 -> 4
        else -> 0
    }

    /** Rough AT+CSQ estimate from RSSI dBm. */
    internal fun csqFromRssi(rssiDbm: Int?): Int? {
        if (rssiDbm == null) return null
        if (rssiDbm >= 0) return null // invalid / unknown sentinel on some builds
        val csq = (rssiDbm + 113) / 2
        return csq.coerceIn(0, 31)
    }

    internal fun isPppConnected(pppStatus: String): Boolean {
        val u = pppStatus.trim().lowercase()
        return u == "ppp_connected" || u == "connected" || u.contains("ppp_connected")
    }

    internal fun mapRegStatus(roam: String, modemMain: String, netStatus: String): String {
        when (roam.trim().lowercase()) {
            "home" -> return "домашняя сеть"
            "roaming" -> return "роуминг"
        }
        if (netStatus == "нет сети" || netStatus == "-") {
            val m = modemMain.lowercase()
            return when {
                m.contains("search") -> "поиск сети"
                m.contains("ready") || m.contains("modem_init_complete") -> "нет"
                else -> "нет"
            }
        }
        return "домашняя сеть"
    }

    internal fun mapSimStatus(modemMain: String, fields: Map<String, String>): String {
        val pin = fields["pin_status"]?.trim().orEmpty()
        if (pin == "1" || pin.equals("pin_required", ignoreCase = true)) {
            return "требуется PIN"
        }
        val m = modemMain.lowercase()
        return when {
            m.contains("modem_sim_undetected") || m.contains("no_sim") -> "нет SIM"
            m.contains("modem_sim_destroy") || m.contains("error") -> "ошибка SIM"
            m.contains("modem_init_complete") || m.contains("ready") || m.isEmpty() -> {
                val imsi = firstNonBlank(fields, "sim_imsi", "imsi")
                if (imsi.isNotEmpty()) "SIM готова" else "нет SIM"
            }
            else -> "SIM готова"
        }
    }

    private fun firstNonBlank(fields: Map<String, String>, vararg keys: String): String {
        for (k in keys) {
            val v = fields[k]?.trim().orEmpty()
            if (v.isNotEmpty()) return v
        }
        return ""
    }

    /**
     * Minimal flat JSON object parser for test fixtures (`{"a":"b","c":"d"}`).
     * Not a general-purpose JSON parser.
     */
    private fun parseFlatStringJson(json: String): Map<String, String> {
        val trimmed = json.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw IllegalArgumentException("Expected flat JSON object")
        }
        val body = trimmed.substring(1, trimmed.length - 1).trim()
        if (body.isEmpty()) return emptyMap()
        val out = linkedMapOf<String, String>()
        // Split on "," that are not inside quotes — fixtures are simple.
        val parts = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var escape = false
        for (ch in body) {
            when {
                escape -> {
                    sb.append(ch)
                    escape = false
                }
                ch == '\\' && inQuotes -> {
                    sb.append(ch)
                    escape = true
                }
                ch == '"' -> {
                    inQuotes = !inQuotes
                    sb.append(ch)
                }
                ch == ',' && !inQuotes -> {
                    parts.add(sb.toString())
                    sb.setLength(0)
                }
                else -> sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) parts.add(sb.toString())
        for (part in parts) {
            val idx = part.indexOf(':')
            if (idx <= 0) continue
            val key = unquote(part.substring(0, idx).trim())
            val value = unquote(part.substring(idx + 1).trim())
            out[key] = value
        }
        return out
    }

    private fun unquote(raw: String): String {
        var s = raw.trim()
        if (s.length >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length - 1)
        }
        return s.replace("\\\"", "\"").replace("\\\\", "\\")
    }
}
