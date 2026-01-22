package vad.dashing.tbox.client.data

import java.util.Date

data class NetState(
    val csq: Int = 99,
    val signalLevel: Int = 0,
    val netStatus: String = "",
    val regStatus: String = "",
    val simStatus: String = "",
    val connectionChangeTime: Date? = null,
)

data class NetValues(
    val imei: String = "",
    val iccid: String = "",
    val imsi: String = "",
    val operator: String = "",
)

data class APNState(
    val apnStatus: Boolean? = null,
    val apnType: String = "",
    val apnIP: String = "",
    val apnGate: String = "",
    val apnDNS1: String = "",
    val apnDNS2: String = "",
    val changeTime: Date? = null,
)

data class UtcTime(
    val year: Int = 0,
    val month: Int = 0,
    val day: Int = 0,
    val hour: Int = 0,
    val minute: Int = 0,
    val second: Int = 0,
)

data class LocValues(
    val rawValue: String = "",
    val locateStatus: Boolean = false,
    val utcTime: UtcTime? = null,
    val longitude: Double = 0.0,
    val latitude: Double = 0.0,
    val altitude: Double = 0.0,
    val visibleSatellites: Int = 0,
    val usingSatellites: Int = 0,
    val speed: Float = 0f,
    val trueDirection: Float = 0f,
    val magneticDirection: Float = 0f,
    val updateTime: Date? = null,
)

data class VoltagesState(
    val voltage1: Float? = null,
    val voltage2: Float? = null,
    val voltage3: Float? = null,
    val updateTime: Date? = null,
)

data class HdmData(
    val isPower: Boolean = false,
    val isIgnition: Boolean = false,
    val isCan: Boolean = false,
)

data class Wheels(
    val wheel1: Float? = null,
    val wheel2: Float? = null,
    val wheel3: Float? = null,
    val wheel4: Float? = null,
)
