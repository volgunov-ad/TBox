package vad.dashing.tbox

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteCallbackList
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TboxDataAidlService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val callbacks = RemoteCallbackList<ITboxDataListener>()
    private val broadcastMutex = Mutex()
    private val observersRunning = AtomicBoolean(false)
    private val listenerCount = AtomicInteger(0)

    private var tboxJob: Job? = null
    private var canJob: Job? = null
    private var cycleJob: Job? = null

    private val binder = object : ITboxDataService.Stub() {
        override fun getTboxData(): Bundle = buildTboxBundle()

        override fun getCanData(): Bundle = buildCanBundle()

        override fun getCycleData(): Bundle = buildCycleBundle()

        override fun getCanIds(): MutableList<String> {
            return ArrayList(CanDataRepository.getAllCanIds())
        }

        override fun getLastCanFrame(canId: String?): Bundle {
            return buildLastCanFrameBundle(canId)
        }

        override fun registerListener(listener: ITboxDataListener?, flags: Int) {
            if (listener == null) return
            val normalizedFlags = normalizeFlags(flags)
            if (callbacks.unregister(listener)) {
                listenerCount.decrementAndGet()
            }
            if (callbacks.register(listener, normalizedFlags)) {
                listenerCount.incrementAndGet()
            }
            startObserversIfNeeded()
            sendInitial(listener, normalizedFlags)
        }

        override fun unregisterListener(listener: ITboxDataListener?) {
            if (listener == null) return
            if (callbacks.unregister(listener)) {
                if (listenerCount.decrementAndGet() <= 0) {
                    listenerCount.set(0)
                    stopObservers()
                }
            } else if (listenerCount.get() <= 0) {
                stopObservers()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        stopObservers()
        callbacks.kill()
        scope.cancel()
        super.onDestroy()
    }

    private fun normalizeFlags(flags: Int): Int {
        val masked = flags and ITboxDataService.FLAG_ALL
        return if (masked == 0) ITboxDataService.FLAG_ALL else masked
    }

    private fun startObserversIfNeeded() {
        if (observersRunning.compareAndSet(false, true)) {
            tboxJob = scope.launch { observeTboxChanges() }
            canJob = scope.launch { observeCanChanges() }
            cycleJob = scope.launch { observeCycleChanges() }
        }
    }

    private fun stopObservers() {
        observersRunning.set(false)
        tboxJob?.cancel()
        tboxJob = null
        canJob?.cancel()
        canJob = null
        cycleJob?.cancel()
        cycleJob = null
    }

    private suspend fun observeTboxChanges() {
        val flows = listOf(
            TboxRepository.netState.map { Unit },
            TboxRepository.netValues.map { Unit },
            TboxRepository.apnState.map { Unit },
            TboxRepository.apn2State.map { Unit },
            TboxRepository.apnStatus.map { Unit },
            TboxRepository.voltages.map { Unit },
            TboxRepository.hdm.map { Unit },
            TboxRepository.logs.map { Unit },
            TboxRepository.atLogs.map { Unit },
            TboxRepository.tboxConnected.map { Unit },
            TboxRepository.preventRestartSend.map { Unit },
            TboxRepository.suspendTboxAppSend.map { Unit },
            TboxRepository.tboxAppStoped.map { Unit },
            TboxRepository.tboxAppVersionAnswer.map { Unit },
            TboxRepository.tboxConnectionTime.map { Unit },
            TboxRepository.serviceStartTime.map { Unit },
            TboxRepository.locationUpdateTime.map { Unit },
            TboxRepository.modemStatus.map { Unit },
            TboxRepository.locValues.map { Unit },
            TboxRepository.isLocValuesTrue.map { Unit },
            TboxRepository.currentTheme.map { Unit },
            TboxRepository.canFrameTime.map { Unit },
            TboxRepository.cycleSignalTime.map { Unit },
            TboxRepository.ipList.map { Unit },
            TboxRepository.didDataCSV.map { Unit },
            TboxRepository.floatingDashboardShownIds.map { Unit },
        )

        merge(flows).collect {
            notifyTboxData()
        }
    }

    private suspend fun observeCanChanges() {
        val flows = listOf(
            CanDataRepository.cruiseSetSpeed.map { Unit },
            CanDataRepository.wheelsSpeed.map { Unit },
            CanDataRepository.wheelsPressure.map { Unit },
            CanDataRepository.wheelsTemperature.map { Unit },
            CanDataRepository.climateSetTemperature1.map { Unit },
            CanDataRepository.engineRPM.map { Unit },
            CanDataRepository.param1.map { Unit },
            CanDataRepository.param2.map { Unit },
            CanDataRepository.param3.map { Unit },
            CanDataRepository.param4.map { Unit },
            CanDataRepository.steerAngle.map { Unit },
            CanDataRepository.steerSpeed.map { Unit },
            CanDataRepository.engineTemperature.map { Unit },
            CanDataRepository.odometer.map { Unit },
            CanDataRepository.distanceToNextMaintenance.map { Unit },
            CanDataRepository.distanceToFuelEmpty.map { Unit },
            CanDataRepository.breakingForce.map { Unit },
            CanDataRepository.carSpeed.map { Unit },
            CanDataRepository.carSpeedAccurate.map { Unit },
            CanDataRepository.voltage.map { Unit },
            CanDataRepository.fuelLevelPercentage.map { Unit },
            CanDataRepository.fuelLevelPercentageFiltered.map { Unit },
            CanDataRepository.gearBoxMode.map { Unit },
            CanDataRepository.gearBoxCurrentGear.map { Unit },
            CanDataRepository.gearBoxPreparedGear.map { Unit },
            CanDataRepository.gearBoxChangeGear.map { Unit },
            CanDataRepository.gearBoxOilTemperature.map { Unit },
            CanDataRepository.gearBoxDriveMode.map { Unit },
            CanDataRepository.gearBoxWork.map { Unit },
            CanDataRepository.frontLeftSeatMode.map { Unit },
            CanDataRepository.frontRightSeatMode.map { Unit },
            CanDataRepository.outsideTemperature.map { Unit },
            CanDataRepository.insideTemperature.map { Unit },
            CanDataRepository.isWindowsBlocked.map { Unit },
            CanDataRepository.canFramesStructured.map { Unit },
        )

        merge(flows).collect {
            notifyCanData()
        }
    }

    private suspend fun observeCycleChanges() {
        val flows = listOf(
            CycleDataRepository.odometer.map { Unit },
            CycleDataRepository.carSpeed.map { Unit },
            CycleDataRepository.voltage.map { Unit },
            CycleDataRepository.longitudinalAcceleration.map { Unit },
            CycleDataRepository.lateralAcceleration.map { Unit },
            CycleDataRepository.yawRate.map { Unit },
            CycleDataRepository.pressure1.map { Unit },
            CycleDataRepository.pressure2.map { Unit },
            CycleDataRepository.pressure3.map { Unit },
            CycleDataRepository.pressure4.map { Unit },
            CycleDataRepository.temperature1.map { Unit },
            CycleDataRepository.temperature2.map { Unit },
            CycleDataRepository.temperature3.map { Unit },
            CycleDataRepository.temperature4.map { Unit },
            CycleDataRepository.engineRPM.map { Unit },
        )

        merge(flows).collect {
            notifyCycleData()
        }
    }

    private fun sendInitial(listener: ITboxDataListener, flags: Int) {
        try {
            if (flags and ITboxDataService.FLAG_TBOX != 0) {
                listener.onTboxDataChanged(buildTboxBundle())
            }
            if (flags and ITboxDataService.FLAG_CAN != 0) {
                listener.onCanDataChanged(buildCanBundle())
            }
            if (flags and ITboxDataService.FLAG_CYCLE != 0) {
                listener.onCycleDataChanged(buildCycleBundle())
            }
        } catch (_: Exception) {
            // Ignore remote failures
        }
    }

    private suspend fun notifyTboxData() {
        val data = buildTboxBundle()
        broadcastToListeners(ITboxDataService.FLAG_TBOX) { listener ->
            listener.onTboxDataChanged(data)
        }
    }

    private suspend fun notifyCanData() {
        val data = buildCanBundle()
        broadcastToListeners(ITboxDataService.FLAG_CAN) { listener ->
            listener.onCanDataChanged(data)
        }
    }

    private suspend fun notifyCycleData() {
        val data = buildCycleBundle()
        broadcastToListeners(ITboxDataService.FLAG_CYCLE) { listener ->
            listener.onCycleDataChanged(data)
        }
    }

    private suspend fun broadcastToListeners(
        flag: Int,
        send: (ITboxDataListener) -> Unit
    ) {
        broadcastMutex.withLock {
            val count = callbacks.beginBroadcast()
            try {
                for (index in 0 until count) {
                    val listener = callbacks.getBroadcastItem(index)
                    val listenerFlags = callbacks.getBroadcastCookie(index) as? Int
                        ?: ITboxDataService.FLAG_ALL
                    if (listenerFlags and flag == 0) continue
                    try {
                        send(listener)
                    } catch (_: Exception) {
                        // Ignore remote failures
                    }
                }
            } finally {
                callbacks.finishBroadcast()
            }
        }
    }

    private fun buildTboxBundle(): Bundle {
        return Bundle().apply {
            putBundle("netState", TboxRepository.netState.value.toBundle())
            putBundle("netValues", TboxRepository.netValues.value.toBundle())
            putBundle("apnState", TboxRepository.apnState.value.toBundle())
            putBundle("apn2State", TboxRepository.apn2State.value.toBundle())
            putBoolean("apnStatus", TboxRepository.apnStatus.value)
            putBundle("voltages", TboxRepository.voltages.value.toBundle())
            putBundle("hdm", TboxRepository.hdm.value.toBundle())
            putStringArrayList("logs", ArrayList(TboxRepository.logs.value))
            putStringArrayList("atLogs", ArrayList(TboxRepository.atLogs.value))
            putBoolean("tboxConnected", TboxRepository.tboxConnected.value)
            putBoolean("preventRestartSend", TboxRepository.preventRestartSend.value)
            putBoolean("suspendTboxAppSend", TboxRepository.suspendTboxAppSend.value)
            putBoolean("tboxAppStoped", TboxRepository.tboxAppStoped.value)
            putBoolean("tboxAppVersionAnswer", TboxRepository.tboxAppVersionAnswer.value)
            putLong("tboxConnectionTime", TboxRepository.tboxConnectionTime.value.time)
            putLong("serviceStartTime", TboxRepository.serviceStartTime.value.time)
            putDateIfNotNull("locationUpdateTime", TboxRepository.locationUpdateTime.value)
            putInt("modemStatus", TboxRepository.modemStatus.value)
            putBundle("locValues", TboxRepository.locValues.value.toBundle())
            putBoolean("isLocValuesTrue", TboxRepository.isLocValuesTrue.value)
            putInt("currentTheme", TboxRepository.currentTheme.value)
            putDateIfNotNull("canFrameTime", TboxRepository.canFrameTime.value)
            putDateIfNotNull("cycleSignalTime", TboxRepository.cycleSignalTime.value)
            putStringArrayList("ipList", ArrayList(TboxRepository.ipList.value))
            putStringArrayList("didDataCSV", ArrayList(TboxRepository.didDataCSV.value))
            putStringArrayList(
                "floatingDashboardShownIds",
                ArrayList(TboxRepository.floatingDashboardShownIds.value)
            )
        }
    }

    private fun buildCanBundle(): Bundle {
        return Bundle().apply {
            putUIntIfNotNull("cruiseSetSpeed", CanDataRepository.cruiseSetSpeed.value)
            putBundle("wheelsSpeed", CanDataRepository.wheelsSpeed.value.toBundle())
            putBundle("wheelsPressure", CanDataRepository.wheelsPressure.value.toBundle())
            putBundle("wheelsTemperature", CanDataRepository.wheelsTemperature.value.toBundle())
            putFloatIfNotNull("climateSetTemperature1", CanDataRepository.climateSetTemperature1.value)
            putFloatIfNotNull("engineRPM", CanDataRepository.engineRPM.value)
            putFloatIfNotNull("param1", CanDataRepository.param1.value)
            putFloatIfNotNull("param2", CanDataRepository.param2.value)
            putFloatIfNotNull("param3", CanDataRepository.param3.value)
            putFloatIfNotNull("param4", CanDataRepository.param4.value)
            putFloatIfNotNull("steerAngle", CanDataRepository.steerAngle.value)
            putIntIfNotNull("steerSpeed", CanDataRepository.steerSpeed.value)
            putFloatIfNotNull("engineTemperature", CanDataRepository.engineTemperature.value)
            putUIntIfNotNull("odometer", CanDataRepository.odometer.value)
            putUIntIfNotNull(
                "distanceToNextMaintenance",
                CanDataRepository.distanceToNextMaintenance.value
            )
            putUIntIfNotNull("distanceToFuelEmpty", CanDataRepository.distanceToFuelEmpty.value)
            putUIntIfNotNull("breakingForce", CanDataRepository.breakingForce.value)
            putFloatIfNotNull("carSpeed", CanDataRepository.carSpeed.value)
            putFloatIfNotNull("carSpeedAccurate", CanDataRepository.carSpeedAccurate.value)
            putFloatIfNotNull("voltage", CanDataRepository.voltage.value)
            putUIntIfNotNull("fuelLevelPercentage", CanDataRepository.fuelLevelPercentage.value)
            putUIntIfNotNull(
                "fuelLevelPercentageFiltered",
                CanDataRepository.fuelLevelPercentageFiltered.value
            )
            putString("gearBoxMode", CanDataRepository.gearBoxMode.value)
            putIntIfNotNull("gearBoxCurrentGear", CanDataRepository.gearBoxCurrentGear.value)
            putIntIfNotNull("gearBoxPreparedGear", CanDataRepository.gearBoxPreparedGear.value)
            putBooleanIfNotNull("gearBoxChangeGear", CanDataRepository.gearBoxChangeGear.value)
            putIntIfNotNull("gearBoxOilTemperature", CanDataRepository.gearBoxOilTemperature.value)
            putString("gearBoxDriveMode", CanDataRepository.gearBoxDriveMode.value)
            putString("gearBoxWork", CanDataRepository.gearBoxWork.value)
            putUIntIfNotNull("frontLeftSeatMode", CanDataRepository.frontLeftSeatMode.value)
            putUIntIfNotNull("frontRightSeatMode", CanDataRepository.frontRightSeatMode.value)
            putFloatIfNotNull("outsideTemperature", CanDataRepository.outsideTemperature.value)
            putFloatIfNotNull("insideTemperature", CanDataRepository.insideTemperature.value)
            putBooleanIfNotNull("isWindowsBlocked", CanDataRepository.isWindowsBlocked.value)
            putStringArrayList("canIds", ArrayList(CanDataRepository.getAllCanIds()))
        }
    }

    private fun buildCycleBundle(): Bundle {
        return Bundle().apply {
            putUIntIfNotNull("odometer", CycleDataRepository.odometer.value)
            putFloatIfNotNull("carSpeed", CycleDataRepository.carSpeed.value)
            putFloatIfNotNull("voltage", CycleDataRepository.voltage.value)
            putFloatIfNotNull(
                "longitudinalAcceleration",
                CycleDataRepository.longitudinalAcceleration.value
            )
            putFloatIfNotNull("lateralAcceleration", CycleDataRepository.lateralAcceleration.value)
            putFloatIfNotNull("yawRate", CycleDataRepository.yawRate.value)
            putFloatIfNotNull("pressure1", CycleDataRepository.pressure1.value)
            putFloatIfNotNull("pressure2", CycleDataRepository.pressure2.value)
            putFloatIfNotNull("pressure3", CycleDataRepository.pressure3.value)
            putFloatIfNotNull("pressure4", CycleDataRepository.pressure4.value)
            putFloatIfNotNull("temperature1", CycleDataRepository.temperature1.value)
            putFloatIfNotNull("temperature2", CycleDataRepository.temperature2.value)
            putFloatIfNotNull("temperature3", CycleDataRepository.temperature3.value)
            putFloatIfNotNull("temperature4", CycleDataRepository.temperature4.value)
            putFloatIfNotNull("engineRPM", CycleDataRepository.engineRPM.value)
        }
    }

    private fun buildLastCanFrameBundle(canId: String?): Bundle {
        val bundle = Bundle()
        if (canId.isNullOrBlank()) {
            bundle.putBoolean(KEY_CAN_FRAME_HAS_DATA, false)
            return bundle
        }

        val frame = CanDataRepository.getLastFrameForId(canId)
        if (frame == null) {
            bundle.putBoolean(KEY_CAN_FRAME_HAS_DATA, false)
            return bundle
        }

        bundle.putBoolean(KEY_CAN_FRAME_HAS_DATA, true)
        bundle.putLong(KEY_CAN_FRAME_TIMESTAMP, frame.date.time)
        bundle.putByteArray(KEY_CAN_FRAME_DATA, frame.rawValue)
        return bundle
    }

    private fun NetState.toBundle(): Bundle = Bundle().apply {
        putInt("csq", csq)
        putInt("signalLevel", signalLevel)
        putString("netStatus", netStatus)
        putString("regStatus", regStatus)
        putString("simStatus", simStatus)
        putDateIfNotNull("connectionChangeTime", connectionChangeTime)
    }

    private fun NetValues.toBundle(): Bundle = Bundle().apply {
        putString("imei", imei)
        putString("iccid", iccid)
        putString("imsi", imsi)
        putString("operator", operator)
    }

    private fun APNState.toBundle(): Bundle = Bundle().apply {
        putBooleanIfNotNull("apnStatus", apnStatus)
        putString("apnType", apnType)
        putString("apnIP", apnIP)
        putString("apnGate", apnGate)
        putString("apnDNS1", apnDNS1)
        putString("apnDNS2", apnDNS2)
        putDateIfNotNull("changeTime", changeTime)
    }

    private fun VoltagesState.toBundle(): Bundle = Bundle().apply {
        putFloatIfNotNull("voltage1", voltage1)
        putFloatIfNotNull("voltage2", voltage2)
        putFloatIfNotNull("voltage3", voltage3)
        putDateIfNotNull("updateTime", updateTime)
    }

    private fun HdmData.toBundle(): Bundle = Bundle().apply {
        putBoolean("isPower", isPower)
        putBoolean("isIgnition", isIgnition)
        putBoolean("isCan", isCan)
    }

    private fun LocValues.toBundle(): Bundle = Bundle().apply {
        putString("rawValue", rawValue)
        putBoolean("locateStatus", locateStatus)
        utcTime?.let { putBundle("utcTime", it.toBundle()) }
        putDouble("longitude", longitude)
        putDouble("latitude", latitude)
        putDouble("altitude", altitude)
        putInt("visibleSatellites", visibleSatellites)
        putInt("usingSatellites", usingSatellites)
        putFloat("speed", speed)
        putFloat("trueDirection", trueDirection)
        putFloat("magneticDirection", magneticDirection)
        putDateIfNotNull("updateTime", updateTime)
    }

    private fun UtcTime.toBundle(): Bundle = Bundle().apply {
        putInt("year", year)
        putInt("month", month)
        putInt("day", day)
        putInt("hour", hour)
        putInt("minute", minute)
        putInt("second", second)
    }

    private fun Wheels.toBundle(): Bundle = Bundle().apply {
        putFloatIfNotNull("wheel1", wheel1)
        putFloatIfNotNull("wheel2", wheel2)
        putFloatIfNotNull("wheel3", wheel3)
        putFloatIfNotNull("wheel4", wheel4)
    }

    private fun Bundle.putFloatIfNotNull(key: String, value: Float?) {
        if (value != null) putFloat(key, value)
    }

    private fun Bundle.putIntIfNotNull(key: String, value: Int?) {
        if (value != null) putInt(key, value)
    }

    private fun Bundle.putBooleanIfNotNull(key: String, value: Boolean?) {
        if (value != null) putBoolean(key, value)
    }

    private fun Bundle.putUIntIfNotNull(key: String, value: UInt?) {
        if (value != null) putLong(key, value.toLong())
    }

    private fun Bundle.putDateIfNotNull(key: String, value: Date?) {
        if (value != null) putLong(key, value.time)
    }

    companion object {
        private const val KEY_CAN_FRAME_HAS_DATA = "hasData"
        private const val KEY_CAN_FRAME_TIMESTAMP = "timestamp"
        private const val KEY_CAN_FRAME_DATA = "rawValue"
    }
}
