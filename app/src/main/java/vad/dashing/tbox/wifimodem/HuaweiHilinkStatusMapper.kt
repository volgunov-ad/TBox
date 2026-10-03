package vad.dashing.tbox.wifimodem

import vad.dashing.tbox.APNState
import vad.dashing.tbox.NetState
import vad.dashing.tbox.NetValues
import java.util.Date

/**
 * Maps Huawei HiLink XML status payloads into [WifiModemSnapshot].
 *
 * `ConnectionStatus`: 901 = data online, 902 = data offline, 900 = connecting (E3372 HAR).
 * A brief 900 keeps the previous online snapshot. Missing radio/throughput fields
 * (optional endpoints failed) also keep the previous sample instead of zeros.
 */
object HuaweiHilinkStatusMapper {

    fun map(
        information: Map<String, String>,
        monitoring: Map<String, String>,
        signal: Map<String, String>,
        previous: WifiModemSnapshot? = null,
        traffic: Map<String, String> = emptyMap(),
    ): WifiModemSnapshot {
        val connectionStatus = firstNonBlank(
            monitoring["ConnectionStatus"],
            monitoring["connectionstatus"],
        )
        val prevNet = previous?.netState
        // 900 is "connecting" between PDP sessions. Hold the last online sample
        // so a one-poll blip does not publish "нет сети" / data off.
        val heldConnection = connectionStatus == "900" && previous?.apnStatus == true
        val apnUp = connectionStatus == "901" || heldConnection
        val signalIcon = firstNonBlank(
            monitoring["SignalIcon"],
            monitoring["SignalStrength"],
        ).toIntOrNull()
        val rssi = HuaweiSignalMetric.parseInt(
            firstNonBlank(signal["rssi"], signal["RSSI"]),
        )
        val rsrp = HuaweiSignalMetric.parseInt(
            firstNonBlank(signal["rsrp"], signal["RSRP"]),
        )
        val rsrq = HuaweiSignalMetric.parseInt(
            firstNonBlank(signal["rsrq"], signal["RSRQ"]),
        )
        val sinr = HuaweiSignalMetric.parseInt(
            firstNonBlank(signal["sinr"], signal["SINR"]),
        )
        // Prefer RSSI for the shared dBm sink; fall back to RSRP (LTE) then previous.
        val signalDbm = rssi ?: rsrp ?: prevNet?.signalDbm
        val csq = signalDbm?.let { ((it + 113) / 2).coerceIn(0, 31) }
        val previousLevel = prevNet?.signalLevel?.takeIf { it > 0 }
        val signalLevel = when {
            heldConnection && (signalIcon == null || signalIcon == 0) && previousLevel != null ->
                previousLevel
            signalIcon != null && signalIcon > 0 -> signalIcon.coerceIn(0, 5).let { if (it > 4) 4 else it }
            signalIcon == 0 -> 0
            csq != null -> when {
                csq >= 20 -> 4
                csq >= 15 -> 3
                csq >= 10 -> 2
                csq >= 5 -> 1
                else -> 0
            }
            previousLevel != null -> previousLevel
            else -> 0
        }
        val mode = signal["mode"].orEmpty().ifBlank { previous?.networkTypeRaw.orEmpty() }
        val reportedGeneration = radioGeneration(signal["mode"].orEmpty())
        val netStatus = reportedGeneration
            ?: prevNet?.netStatus?.takeIf { (heldConnection || apnUp) && it.isRadioGeneration() }
            ?: if (apnUp) "4G" else "нет сети"
        val simRaw = firstNonBlank(monitoring["SimStatus"], monitoring["simstatus"])
        val simStatus = when (simRaw) {
            "1" -> "SIM готова"
            "0" -> "нет SIM"
            else -> simRaw.ifBlank {
                prevNet?.simStatus?.takeIf { heldConnection && it.isNotBlank() && it != "-" } ?: "-"
            }
        }
        val regStatus = when {
            firstNonBlank(monitoring["RoamingStatus"], monitoring["roamingstatus"]) == "1" -> "роуминг"
            heldConnection -> prevNet?.regStatus?.takeIf {
                it == "домашняя сеть" || it == "роуминг"
            } ?: "домашняя сеть"
            apnUp -> "домашняя сеть"
            else -> "нет сети"
        }
        val operator = firstNonBlank(
            information["FullName"],
            information["ShortName"],
            information["fullnamedialogue"],
        ).ifBlank { "-" }
        val imei = firstNonBlank(information["Imei"], information["IMEI"]).ifBlank { "-" }
        val imsi = firstNonBlank(information["Imsi"], information["IMSI"]).ifBlank { "-" }
        val iccid = firstNonBlank(information["Iccid"], information["ICCID"]).ifBlank { "-" }
        val wanIp = firstNonBlank(
            information["WanIPAddress"],
            information["WanIPv4Address"],
            information["WanIPv6Address"],
        )
        val firmware = firstNonBlank(
            information["SoftwareVersion"],
            information["HardwareVersion"],
        )

        val connectionChangeTime = when {
            prevNet == null -> Date()
            prevNet.regStatus != regStatus -> Date()
            else -> prevNet.connectionChangeTime
        }
        // CurrentDownload/CurrentUpload are cumulative session bytes — rates only.
        val downloadBps = ModemThroughputFormat.parseBps(
            firstNonBlank(
                traffic["CurrentDownloadRate"],
                traffic["currentdownloadrate"],
            ),
        ) ?: prevNet?.downloadSpeedBps
        val uploadBps = ModemThroughputFormat.parseBps(
            firstNonBlank(
                traffic["CurrentUploadRate"],
                traffic["currentuploadrate"],
            ),
        ) ?: prevNet?.uploadSpeedBps
        val netState = NetState(
            csq = csq ?: 99,
            signalLevel = signalLevel,
            signalDbm = signalDbm,
            netStatus = netStatus,
            regStatus = regStatus,
            simStatus = simStatus,
            connectionChangeTime = connectionChangeTime,
            downloadSpeedBps = downloadBps,
            uploadSpeedBps = uploadBps,
        )
        val netValues = NetValues(
            imei = imei,
            iccid = iccid,
            imsi = imsi,
            operator = operator,
        )
        val prevApn = previous?.apnState
        val apnChangeTime = when {
            previous == null -> Date()
            previous.apnStatus != apnUp -> Date()
            else -> previous.apnState.changeTime
        }
        val apnState = APNState(
            apnStatus = apnUp,
            apnType = if (apnUp) "default" else "",
            apnIP = wanIp.ifBlank { prevApn?.apnIP.orEmpty() },
            changeTime = apnChangeTime,
        )
        return WifiModemSnapshot(
            netState = netState,
            netValues = netValues,
            apnState = apnState,
            apnStatus = apnUp,
            firmware = firmware,
            rssiDbm = rssi ?: previous?.rssiDbm,
            rsrpDbm = rsrp ?: previous?.rsrpDbm,
            rsrqDb = rsrq ?: previous?.rsrqDb,
            sinrDb = sinr ?: previous?.sinrDb,
            cellId = firstNonBlank(signal["cell_id"], signal["cellid"])
                .ifBlank { previous?.cellId.orEmpty() },
            networkTypeRaw = mode,
            pppStatusRaw = connectionStatus,
        )
    }

    /** HiLink `signal.mode`: 7 = LTE, 3–6 = 3G, 0–2 = 2G. */
    private fun radioGeneration(mode: String): String? = when (mode) {
        "7" -> "4G"
        "6", "5", "4", "3" -> "3G"
        "2", "1", "0" -> "2G"
        else -> null
    }

    private fun String.isRadioGeneration(): Boolean =
        this == "4G" || this == "3G" || this == "2G"

    private fun firstNonBlank(vararg values: String?): String =
        values.firstOrNull { !it.isNullOrBlank() }.orEmpty()
}
