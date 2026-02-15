package vad.dashing.tbox.client.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import vad.dashing.tbox.client.data.CanDataRepository
import vad.dashing.tbox.client.data.CycleDataRepository
import vad.dashing.tbox.client.data.LocValues
import vad.dashing.tbox.client.data.TboxRepository
import vad.dashing.tbox.client.data.UtcTime

data class DisplayItem(
    val label: String,
    val value: String,
    val isHeader: Boolean = false,
)

class ClientDataViewModel : ViewModel() {
    val items: StateFlow<List<DisplayItem>> = combine(
        TboxRepository.updateTicker,
        CanDataRepository.updateTicker,
        CycleDataRepository.updateTicker,
    ) { _, _, _ ->
        buildItems()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), buildItems())

    private fun buildItems(): List<DisplayItem> {
        val list = mutableListOf<DisplayItem>()

        list.addHeader("TBOX")
        val netState = TboxRepository.netState.value
        list.addItem("net.csq", netState.csq.toString())
        list.addItem("net.signalLevel", netState.signalLevel.toString())
        list.addItem("net.netStatus", netState.netStatus)
        list.addItem("net.regStatus", netState.regStatus)
        list.addItem("net.simStatus", netState.simStatus)
        list.addItem("net.connectionChangeTime", formatDate(netState.connectionChangeTime))

        val netValues = TboxRepository.netValues.value
        list.addItem("net.imei", netValues.imei)
        list.addItem("net.iccid", netValues.iccid)
        list.addItem("net.imsi", netValues.imsi)
        list.addItem("net.operator", netValues.operator)

        val apnState = TboxRepository.apnState.value
        list.addItem("apn.status", valueOrDash(apnState.apnStatus))
        list.addItem("apn.type", apnState.apnType)
        list.addItem("apn.ip", apnState.apnIP)
        list.addItem("apn.gate", apnState.apnGate)
        list.addItem("apn.dns1", apnState.apnDNS1)
        list.addItem("apn.dns2", apnState.apnDNS2)
        list.addItem("apn.changeTime", formatDate(apnState.changeTime))

        val apn2State = TboxRepository.apn2State.value
        list.addItem("apn2.status", valueOrDash(apn2State.apnStatus))
        list.addItem("apn2.type", apn2State.apnType)
        list.addItem("apn2.ip", apn2State.apnIP)
        list.addItem("apn2.gate", apn2State.apnGate)
        list.addItem("apn2.dns1", apn2State.apnDNS1)
        list.addItem("apn2.dns2", apn2State.apnDNS2)
        list.addItem("apn2.changeTime", formatDate(apn2State.changeTime))

        list.addItem("apn.statusFlag", TboxRepository.apnStatus.value.toString())

        val voltages = TboxRepository.voltages.value
        list.addItem("voltages.v1", valueOrDash(voltages.voltage1))
        list.addItem("voltages.v2", valueOrDash(voltages.voltage2))
        list.addItem("voltages.v3", valueOrDash(voltages.voltage3))
        list.addItem("voltages.updateTime", formatDate(voltages.updateTime))

        val hdm = TboxRepository.hdm.value
        list.addItem("hdm.isPower", hdm.isPower.toString())
        list.addItem("hdm.isIgnition", hdm.isIgnition.toString())
        list.addItem("hdm.isCan", hdm.isCan.toString())

        list.addItem("tbox.connected", TboxRepository.tboxConnected.value.toString())
        list.addItem("tbox.preventRestartSend", TboxRepository.preventRestartSend.value.toString())
        list.addItem("tbox.suspendTboxAppSend", TboxRepository.suspendTboxAppSend.value.toString())
        list.addItem("tbox.tboxAppStoped", TboxRepository.tboxAppStoped.value.toString())
        list.addItem("tbox.tboxAppVersionAnswer", TboxRepository.tboxAppVersionAnswer.value.toString())
        list.addItem("tbox.connectionTime", formatDate(TboxRepository.tboxConnectionTime.value))
        list.addItem("tbox.serviceStartTime", formatDate(TboxRepository.serviceStartTime.value))
        list.addItem("tbox.locationUpdateTime", formatDate(TboxRepository.locationUpdateTime.value))
        list.addItem("tbox.modemStatus", TboxRepository.modemStatus.value.toString())

        val locValues = TboxRepository.locValues.value
        list.addLocValues(locValues)

        list.addItem("tbox.isLocValuesTrue", TboxRepository.isLocValuesTrue.value.toString())
        list.addItem("tbox.currentTheme", TboxRepository.currentTheme.value.toString())
        list.addItem("tbox.canFrameTime", formatDate(TboxRepository.canFrameTime.value))
        list.addItem("tbox.cycleSignalTime", formatDate(TboxRepository.cycleSignalTime.value))
        list.addItem("tbox.ipList", joinList(TboxRepository.ipList.value))
        list.addItem("tbox.didDataCSV", joinList(TboxRepository.didDataCSV.value.takeLast(5)))
        list.addItem(
            "tbox.floatingDashboardShownIds",
            joinList(TboxRepository.floatingDashboardShownIds.value.toList())
        )
        list.addItem("tbox.logs.count", TboxRepository.logs.value.size.toString())
        list.addItem("tbox.logs.last", joinList(TboxRepository.logs.value.takeLast(5)))
        list.addItem("tbox.atLogs.count", TboxRepository.atLogs.value.size.toString())
        list.addItem("tbox.atLogs.last", joinList(TboxRepository.atLogs.value.takeLast(5)))

        list.addHeader("CAN")
        val can = CanDataRepository
        list.addItem("can.cruiseSetSpeed", valueOrDash(can.cruiseSetSpeed.value))
        list.addItem("can.wheel1Speed", valueOrDash(can.wheelsSpeed.value.wheel1))
        list.addItem("can.wheel2Speed", valueOrDash(can.wheelsSpeed.value.wheel2))
        list.addItem("can.wheel3Speed", valueOrDash(can.wheelsSpeed.value.wheel3))
        list.addItem("can.wheel4Speed", valueOrDash(can.wheelsSpeed.value.wheel4))
        list.addItem("can.wheel1Pressure", valueOrDash(can.wheelsPressure.value.wheel1))
        list.addItem("can.wheel2Pressure", valueOrDash(can.wheelsPressure.value.wheel2))
        list.addItem("can.wheel3Pressure", valueOrDash(can.wheelsPressure.value.wheel3))
        list.addItem("can.wheel4Pressure", valueOrDash(can.wheelsPressure.value.wheel4))
        list.addItem("can.wheel1Temperature", valueOrDash(can.wheelsTemperature.value.wheel1))
        list.addItem("can.wheel2Temperature", valueOrDash(can.wheelsTemperature.value.wheel2))
        list.addItem("can.wheel3Temperature", valueOrDash(can.wheelsTemperature.value.wheel3))
        list.addItem("can.wheel4Temperature", valueOrDash(can.wheelsTemperature.value.wheel4))
        list.addItem("can.climateSetTemperature1", valueOrDash(can.climateSetTemperature1.value))
        list.addItem("can.engineRPM", valueOrDash(can.engineRPM.value))
        list.addItem("can.param1", valueOrDash(can.param1.value))
        list.addItem("can.param2", valueOrDash(can.param2.value))
        list.addItem("can.param3", valueOrDash(can.param3.value))
        list.addItem("can.param4", valueOrDash(can.param4.value))
        list.addItem("can.steerAngle", valueOrDash(can.steerAngle.value))
        list.addItem("can.steerSpeed", valueOrDash(can.steerSpeed.value))
        list.addItem("can.engineTemperature", valueOrDash(can.engineTemperature.value))
        list.addItem("can.odometer", valueOrDash(can.odometer.value))
        list.addItem("can.distanceToNextMaintenance", valueOrDash(can.distanceToNextMaintenance.value))
        list.addItem("can.distanceToFuelEmpty", valueOrDash(can.distanceToFuelEmpty.value))
        list.addItem("can.breakingForce", valueOrDash(can.breakingForce.value))
        list.addItem("can.carSpeed", valueOrDash(can.carSpeed.value))
        list.addItem("can.carSpeedAccurate", valueOrDash(can.carSpeedAccurate.value))
        list.addItem("can.voltage", valueOrDash(can.voltage.value))
        list.addItem("can.fuelLevelPercentage", valueOrDash(can.fuelLevelPercentage.value))
        list.addItem("can.fuelLevelPercentageFiltered", valueOrDash(can.fuelLevelPercentageFiltered.value))
        list.addItem("can.gearBoxMode", can.gearBoxMode.value)
        list.addItem("can.gearBoxCurrentGear", valueOrDash(can.gearBoxCurrentGear.value))
        list.addItem("can.gearBoxPreparedGear", valueOrDash(can.gearBoxPreparedGear.value))
        list.addItem("can.gearBoxChangeGear", valueOrDash(can.gearBoxChangeGear.value))
        list.addItem("can.gearBoxOilTemperature", valueOrDash(can.gearBoxOilTemperature.value))
        list.addItem("can.gearBoxDriveMode", can.gearBoxDriveMode.value)
        list.addItem("can.gearBoxWork", can.gearBoxWork.value)
        list.addItem("can.frontLeftSeatMode", valueOrDash(can.frontLeftSeatMode.value))
        list.addItem("can.frontRightSeatMode", valueOrDash(can.frontRightSeatMode.value))
        list.addItem("can.outsideTemperature", valueOrDash(can.outsideTemperature.value))
        list.addItem("can.insideTemperature", valueOrDash(can.insideTemperature.value))
        list.addItem("can.isWindowsBlocked", valueOrDash(can.isWindowsBlocked.value))
        list.addItem("can.ids", joinList(can.canIds.value))

        list.addHeader("CYCLE")
        val cycle = CycleDataRepository
        list.addItem("cycle.odometer", valueOrDash(cycle.odometer.value))
        list.addItem("cycle.carSpeed", valueOrDash(cycle.carSpeed.value))
        list.addItem("cycle.voltage", valueOrDash(cycle.voltage.value))
        list.addItem("cycle.longitudinalAcceleration", valueOrDash(cycle.longitudinalAcceleration.value))
        list.addItem("cycle.lateralAcceleration", valueOrDash(cycle.lateralAcceleration.value))
        list.addItem("cycle.yawRate", valueOrDash(cycle.yawRate.value))
        list.addItem("cycle.pressure1", valueOrDash(cycle.pressure1.value))
        list.addItem("cycle.pressure2", valueOrDash(cycle.pressure2.value))
        list.addItem("cycle.pressure3", valueOrDash(cycle.pressure3.value))
        list.addItem("cycle.pressure4", valueOrDash(cycle.pressure4.value))
        list.addItem("cycle.temperature1", valueOrDash(cycle.temperature1.value))
        list.addItem("cycle.temperature2", valueOrDash(cycle.temperature2.value))
        list.addItem("cycle.temperature3", valueOrDash(cycle.temperature3.value))
        list.addItem("cycle.temperature4", valueOrDash(cycle.temperature4.value))
        list.addItem("cycle.engineRPM", valueOrDash(cycle.engineRPM.value))

        return list
    }

    private fun MutableList<DisplayItem>.addHeader(title: String) {
        add(DisplayItem(title, "", isHeader = true))
    }

    private fun MutableList<DisplayItem>.addItem(label: String, value: String) {
        add(DisplayItem(label, value))
    }

    private fun MutableList<DisplayItem>.addLocValues(values: LocValues) {
        addItem("loc.rawValue", values.rawValue)
        addItem("loc.locateStatus", values.locateStatus.toString())
        addItem("loc.utcTime", formatUtcTime(values.utcTime))
        addItem("loc.longitude", values.longitude.toString())
        addItem("loc.latitude", values.latitude.toString())
        addItem("loc.altitude", values.altitude.toString())
        addItem("loc.visibleSatellites", values.visibleSatellites.toString())
        addItem("loc.usingSatellites", values.usingSatellites.toString())
        addItem("loc.speed", values.speed.toString())
        addItem("loc.trueDirection", values.trueDirection.toString())
        addItem("loc.magneticDirection", values.magneticDirection.toString())
        addItem("loc.updateTime", formatDate(values.updateTime))
    }

    private fun valueOrDash(value: Any?): String {
        return value?.toString() ?: "-"
    }

    private fun formatDate(date: Date?): String {
        return date?.let { DATE_FORMAT.format(it) } ?: "-"
    }

    private fun formatUtcTime(time: UtcTime?): String {
        if (time == null) return "-"
        return "%04d-%02d-%02d %02d:%02d:%02d".format(
            time.year,
            time.month,
            time.day,
            time.hour,
            time.minute,
            time.second
        )
    }

    private fun joinList(values: List<String>): String {
        if (values.isEmpty()) return "-"
        return values.joinToString(separator = "\n")
    }

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    }
}
