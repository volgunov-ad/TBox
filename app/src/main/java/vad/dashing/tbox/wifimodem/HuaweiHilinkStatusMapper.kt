package vad.dashing.tbox.wifimodem

import vad.dashing.tbox.APNState
import vad.dashing.tbox.NetState
import vad.dashing.tbox.NetValues
import java.util.Date

/**
 * Maps Huawei HiLink XML status payloads into [WifiModemSnapshot].
 *
 * `ConnectionStatus`: 901 = data online, 902 = data offline (E3372 HAR).
 */
object HuaweiHilinkStatusMapper {

    fun map(
        information: Map<String, String>,
        monitoring: Map<String, String>,
        signal: Map<String, String>,
        previous: WifiModemSnapshot? = null,
    ): WifiModemSnapshot {
        val connectionStatus = firstNonBlank(
            monitoring["ConnectionStatus"],
            monitoring["connectionstatus"],
        )
        val apnUp = connectionStatus == "901"
        val signalIcon = firstNonBlank(
            monitoring["SignalIcon"],
            monitoring["SignalStrength"],
        ).toIntOrNull()
        val rssi = signal["rssi"]?.toIntOrNull()
        val rsrp = signal["rsrp"]?.toIntOrNull()
        val rsrq = signal["rsrq"]?.toIntOrNull()
        val sinr = signal["sinr"]?.toIntOrNull()
        val signalDbm = rssi ?: rsrp ?: previous?.netState?.signalDbm
        val csq = signalDbm?.let { ((it + 113) / 2).coerceIn(0, 31) }
        val signalLevel = when {
            signalIcon != null && signalIcon > 0 -> signalIcon.coerceIn(0, 5).let { if (it > 4) 4 else it }
            signalIcon == 0 -> 0
            csq != null -> when {
                csq >= 20 -> 4
                csq >= 15 -> 3
                csq >= 10 -> 2
                csq >= 5 -> 1
                else -> 0
            }
            previous?.netState?.signalLevel?.takeIf { it > 0 } != null -> previous.netState.signalLevel
            else -> 0
        }
        val mode = signal["mode"].orEmpty()
        val netStatus = when (mode) {
            "7" -> "4G"
            "6", "5", "4", "3" -> "3G"
            "2", "1", "0" -> "2G"
            else -> if (apnUp) "4G" else "нет сети"
        }
        val simStatus = when (firstNonBlank(monitoring["SimStatus"], monitoring["simstatus"])) {
            "1" -> "SIM готова"
            "0" -> "нет SIM"
            else -> firstNonBlank(monitoring["SimStatus"]).ifBlank { "-" }
        }
        val regStatus = when {
            firstNonBlank(monitoring["RoamingStatus"], monitoring["roamingstatus"]) == "1" -> "роуминг"
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

        val prevNet = previous?.netState
        val connectionChangeTime = when {
            prevNet == null -> Date()
            prevNet.regStatus != regStatus -> Date()
            else -> prevNet.connectionChangeTime
        }
        val netState = NetState(
            csq = csq ?: 99,
            signalLevel = signalLevel,
            signalDbm = signalDbm,
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
            rssiDbm = rssi,
            rsrpDbm = rsrp,
            rsrqDb = rsrq,
            sinrDb = sinr,
            cellId = firstNonBlank(signal["cell_id"], signal["cellid"]),
            networkTypeRaw = mode,
            pppStatusRaw = connectionStatus,
        )
    }

    private fun firstNonBlank(vararg values: String?): String =
        values.firstOrNull { !it.isNullOrBlank() }.orEmpty()
}
