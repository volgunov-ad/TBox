package vad.dashing.tbox

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.Date
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.collections.lastOrNull

class TboxBroadcastSender(
    private val context: Context,
    private val scope: CoroutineScope
) {

    private val netStateSubscribers = CopyOnWriteArraySet<String>()
    private val generalStateSubscribers = CopyOnWriteArraySet<String>()
    private val engineStateSubscribers = CopyOnWriteArraySet<String>()
    private val gearBoxStateSubscribers = CopyOnWriteArraySet<String>()
    private val locationStateSubscribers = CopyOnWriteArraySet<String>()
    private val carStateSubscribers = CopyOnWriteArraySet<String>()
    private val canIDSubscribers = CopyOnWriteArraySet<String>()

    companion object {
        private const val TAG = "TboxBroadcastSender"
        private const val RETURN_EXTRA_NAME_1 = "return_extra_name_1"
        private const val RETURN_EXTRA_VALUE_1 = "return_extra_value_1"

        const val NET_STATE = "netState"
        const val SIGNAL_LEVEL = "signalLevel"
        const val NET_STATUS = "netStatus"
        const val REG_STATUS = "regStatus"
        const val SIM_STATUS = "simStatus"
        const val APN_STATUS = "apnStatus"

        const val GENERAL_STATE = "generalState"
        const val TBOX_CONNECTED = "tboxConnected"
        const val TBOX_CONNECTION_TIME = "tboxConnectionTime"
        const val SERVICE_START_TIME = "serviceStartTime"

        const val ENGINE_STATE = "engineState"
        const val ENGINE_RPM = "engineRPM"
        const val ENGINE_TEMPERATURE = "engineTemperature"

        const val GEAR_BOX_STATE = "gearBoxState"
        const val GEAR_BOX_CURRENT_GEAR = "gearBoxCurrentGear"
        const val GEAR_BOX_OIL_TEMPERATURE = "gearBoxOilTemperature"

        const val LOCATION_STATE = "locationState"
        const val LOCATE_STATUS = "locateStatus"
        const val IS_LOC_VALUES_TRUE = "isLocValuesTrue"

        const val CAR_STATE = "carState"
        const val FUEL_LEVEL_PERCENTAGE = "fuelLevelPercentage"
        const val FUEL_LEVEL_PERCENTAGE_FILTERED = "fuelLevelPercentageFiltered"
        const val CRUISE_SET_SPEED = "cruiseSetSpeed"
        const val VOLTAGE = "voltage"
        const val INSIDE_TEMPERATURE = "insideTemperature"

        const val CAN_ID = "canID"
    }

    private var netStateListenerJob: Job? = null
    private var generalStateListenerJob: Job? = null
    private var engineStateListenerJob: Job? = null
    private var gearBoxStateListenerJob: Job? = null
    private var locationStateListenerJob: Job? = null
    private var carStateListenerJob: Job? = null
    private var canIDListenerJob: Job? = null

    private fun sendResponse(
        sender: String,
        action: String,
        returnExtraName1: String,
        returnExtraValue1: Any,
    ) {
        try {
            val responseIntent = Intent(action).apply {
                setPackage(sender)
                putExtra(RETURN_EXTRA_NAME_1, returnExtraName1)
                when (returnExtraValue1) {
                    is Int -> putExtra(RETURN_EXTRA_VALUE_1, returnExtraValue1)
                    is String -> putExtra(RETURN_EXTRA_VALUE_1, returnExtraValue1)
                    is Boolean -> putExtra(RETURN_EXTRA_VALUE_1, returnExtraValue1)
                    is Date -> putExtra(RETURN_EXTRA_VALUE_1, returnExtraValue1.time)
                    is Float -> putExtra(RETURN_EXTRA_VALUE_1, returnExtraValue1)
                    is ByteArray -> putExtra(RETURN_EXTRA_VALUE_1, returnExtraValue1)
                }
            }

            context.sendBroadcast(responseIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send response", e)
        }
    }

    // Добавить подписчика
    fun addSubscriber(packageName: String, extraName: String, extraValue: String) {
        val key = createSubscriberKey(packageName, extraName, extraValue)

        when (extraName) {
            NET_STATE -> handleAddSubscriber(
                packageName = packageName,
                key = key,
                subscribers = netStateSubscribers,
                listenerJob = netStateListenerJob,
                startListener = ::startNetStatesListener,
                logTag = "netSubscribers"
            )
            GENERAL_STATE -> handleAddSubscriber(
                packageName = packageName,
                key = key,
                subscribers = generalStateSubscribers,
                listenerJob = generalStateListenerJob,
                startListener = ::startGeneralStateListener,
                logTag = "statusSubscribers"
            )
            ENGINE_STATE -> handleAddSubscriber(
                packageName = packageName,
                key = key,
                subscribers = engineStateSubscribers,
                listenerJob = engineStateListenerJob,
                startListener = ::startEngineStateListener,
                logTag = "engineStateSubscribers"
            )
            GEAR_BOX_STATE -> handleAddSubscriber(
                packageName = packageName,
                key = key,
                subscribers = gearBoxStateSubscribers,
                listenerJob = gearBoxStateListenerJob,
                startListener = ::startGearBoxStateListener,
                logTag = "gearBoxStateSubscribers"
            )
            LOCATION_STATE -> handleAddSubscriber(
                packageName = packageName,
                key = key,
                subscribers = locationStateSubscribers,
                listenerJob = locationStateListenerJob,
                startListener = ::startLocationStateListener,
                logTag = "locationStateSubscribers"
            )
            CAR_STATE -> handleAddSubscriber(
                packageName = packageName,
                key = key,
                subscribers = carStateSubscribers,
                listenerJob = carStateListenerJob,
                startListener = ::startCarStateListener,
                logTag = "carStateSubscribers"
            )
            CAN_ID -> handleAddSubscriber(
                packageName = packageName,
                key = key,
                subscribers = canIDSubscribers,
                listenerJob = canIDListenerJob,
                startListener = ::startCANIDListener,
                logTag = "canIDSubscribers"
            )
            else -> return
        }

        // Отправляем текущее значение
        sendCurrentValue(packageName, extraName, extraValue)
        /*scope.launch {
            delay(2000) // Задержка 2 секунды
            sendCurrentValue(packageName, extraName, extraValue)
        }*/
    }

    private fun handleAddSubscriber(
        packageName: String,
        key: String,
        subscribers: CopyOnWriteArraySet<String>,
        listenerJob: Job?,
        startListener: () -> Unit,
        logTag: String
    ) {
        if (subscribers.contains(key)) {
            Log.d(
                TAG, "$key already added: $packageName. " +
                        "Total $logTag: ${getUniquePackageNames(subscribers).size} (${subscribers.size})"
            )
        } else {
            subscribers.add(key)
            Log.d(
                TAG, "$key added: $packageName. " +
                        "Total $logTag: ${getUniquePackageNames(subscribers).size} (${subscribers.size})"
            )

            // Запускаем listener только при добавлении первого подписчика
            if (subscribers.size == 1 && listenerJob?.isActive != true) {
                startListener()
            }
        }
    }


    fun sendCurrentValue(packageName: String, extraName: String, extraValue: String) {
        when (extraName) {
            NET_STATE -> {
                when (extraValue) {
                    in arrayOf(SIGNAL_LEVEL, NET_STATUS, REG_STATUS, SIM_STATUS) ->
                        sendNetState(TboxRepository.netState.value, arrayOf(packageName, extraName, extraValue))
                    APN_STATUS -> sendAPNStatus(TboxRepository.apnStatus.value, arrayOf(packageName, extraName, extraValue))
                }
            }
            GENERAL_STATE -> {
                when (extraValue) {
                    TBOX_CONNECTED -> {
                        sendTboxConnected(TboxRepository.tboxConnected.value, arrayOf(packageName, extraName, extraValue))
                    }
                    TBOX_CONNECTION_TIME -> {
                        sendTime(TboxRepository.tboxConnectionTime.value, arrayOf(packageName, extraName, extraValue), extraValue)
                    }
                    SERVICE_START_TIME -> {
                        sendTime(TboxRepository.serviceStartTime.value, arrayOf(packageName, extraName, extraValue), extraValue)
                    }
                }
            }

            ENGINE_STATE -> {
                when (extraValue) {
                    ENGINE_RPM -> {
                        sendFloat(CanDataRepository.engineRPM.value, arrayOf(packageName, extraName, extraValue), extraValue)
                    }
                    ENGINE_TEMPERATURE -> {
                        sendFloat(CanDataRepository.engineTemperature.value, arrayOf(packageName, extraName, extraValue), extraValue)
                    }
                }
            }

            GEAR_BOX_STATE -> {
                when (extraValue) {
                    GEAR_BOX_CURRENT_GEAR -> {
                        sendInt(CanDataRepository.gearBoxCurrentGear.value, arrayOf(packageName, extraName, extraValue), extraValue)
                    }
                    GEAR_BOX_OIL_TEMPERATURE -> {
                        sendInt(CanDataRepository.gearBoxOilTemperature.value, arrayOf(packageName, extraName, extraValue), extraValue)
                    }
                }
            }

            LOCATION_STATE -> {
                when (extraValue) {
                    LOCATE_STATUS -> {
                        sendBoolean(TboxRepository.locValues.value.locateStatus, arrayOf(packageName, extraName, extraValue), extraValue)
                    }
                    IS_LOC_VALUES_TRUE -> {
                        sendBoolean(TboxRepository.isLocValuesTrue.value, arrayOf(packageName, extraName, extraValue), extraValue)
                    }
                }
            }

            CAR_STATE -> {
                when (extraValue) {
                    FUEL_LEVEL_PERCENTAGE -> {
                        sendUInt(CanDataRepository.fuelLevelPercentage.value, arrayOf(packageName, extraName, extraValue), extraValue)
                    }
                    FUEL_LEVEL_PERCENTAGE_FILTERED -> {
                        sendUInt(CanDataRepository.fuelLevelPercentageFiltered.value, arrayOf(packageName, extraName, extraValue), extraValue)
                    }
                    CRUISE_SET_SPEED -> {
                        sendUInt(CanDataRepository.cruiseSetSpeed.value, arrayOf(packageName, extraName, extraValue), extraValue)
                    }
                    VOLTAGE -> {
                        sendFloat(CanDataRepository.voltage.value, arrayOf(packageName, extraName, extraValue), extraValue)
                    }
                    INSIDE_TEMPERATURE -> {
                        sendFloat(CanDataRepository.insideTemperature.value, arrayOf(packageName, extraName, extraValue), extraValue)
                    }
                }
            }

            CAN_ID -> {
                sendByteArray(CanDataRepository.getLastFrameForId(extraValue)?.rawValue, arrayOf(packageName, extraName, extraValue), extraValue)
            }
        }
    }

    // Удалить подписчика
    fun deleteSubscriber(packageName: String, extraName: String, extraValue: String) {
        val key = createSubscriberKey(packageName, extraName, extraValue)

        when (extraName) {
            NET_STATE -> handleDeleteSubscriber(
                packageName = packageName,
                key = key,
                subscribers = netStateSubscribers,
                listenerJob = netStateListenerJob,
                logTag = "netSubscribers"
            )
            GENERAL_STATE -> handleDeleteSubscriber(
                packageName = packageName,
                key = key,
                subscribers = generalStateSubscribers,
                listenerJob = generalStateListenerJob,
                logTag = "statusSubscribers"
            )
            ENGINE_STATE -> handleDeleteSubscriber(
                packageName = packageName,
                key = key,
                subscribers = engineStateSubscribers,
                listenerJob = engineStateListenerJob,
                logTag = "engineStateSubscribers"
            )
            GEAR_BOX_STATE -> handleDeleteSubscriber(
                packageName = packageName,
                key = key,
                subscribers = gearBoxStateSubscribers,
                listenerJob = gearBoxStateListenerJob,
                logTag = "gearBoxStateSubscribers"
            )
            LOCATION_STATE -> handleDeleteSubscriber(
                packageName = packageName,
                key = key,
                subscribers = locationStateSubscribers,
                listenerJob = locationStateListenerJob,
                logTag = "locationStateSubscribers"
            )
            CAR_STATE -> handleDeleteSubscriber(
                packageName = packageName,
                key = key,
                subscribers = carStateSubscribers,
                listenerJob = carStateListenerJob,
                logTag = "carStateSubscribers"
            )
            CAN_ID -> handleDeleteSubscriber(
                packageName = packageName,
                key = key,
                subscribers = canIDSubscribers,
                listenerJob = canIDListenerJob,
                logTag = "canIDSubscribers"
            )
        }
    }

    private fun handleDeleteSubscriber(
        packageName: String,
        key: String,
        subscribers: MutableSet<String>,
        listenerJob: Job?,
        logTag: String
    ) {
        val wasRemoved = subscribers.remove(key)
        if (!wasRemoved) return

        Log.d(
            TAG, "$key removed: $packageName. " +
                    "Total $logTag: ${getUniquePackageNames(subscribers).size} (${subscribers.size})"
        )

        // Останавливаем слушатель, если не осталось подписчиков
        if (subscribers.isEmpty()) {
            listenerJob?.cancel()
        }
    }

    // Очистить всех подписчиков
    fun clearSubscribers() {
        netStateSubscribers.clear()
        generalStateSubscribers.clear()
        engineStateSubscribers.clear()
        gearBoxStateSubscribers.clear()
        locationStateSubscribers.clear()
        carStateSubscribers.clear()
        canIDSubscribers.clear()
        stopListeners()
        Log.d(TAG, "All subscribers cleared")
    }

    @OptIn(FlowPreview::class)
    private fun startNetStatesListener() {
        if (netStateListenerJob?.isActive == true) {
            Log.d(TAG, "State broadcast listener is already running")
            return
        }

        netStateListenerJob = scope.launch {
            launch {
                TboxRepository.netState
                    .sampleWithTimeout(periodMillis = 900, timeoutMillis = 10000)
                    .collect { netState ->
                        if (netStateSubscribers.isNotEmpty()) {
                            netStateSubscribers.forEach { subscriberKey ->
                                val subscriber = parseSubscriberKey(subscriberKey)
                                sendNetState(netState, subscriber)
                            }
                        }
                    }
            }

            launch {
                TboxRepository.apnStatus
                    .sampleWithTimeout(periodMillis = 900, timeoutMillis = 20000)
                    .collect { apnStatus ->
                        if (netStateSubscribers.isNotEmpty()) {
                            netStateSubscribers.forEach { subscriberKey ->
                                val subscriber = parseSubscriberKey(subscriberKey)
                                sendAPNStatus(apnStatus, subscriber)
                            }
                        }
                    }
            }
        }
        Log.d(TAG, "Net State broadcast listener started")
    }

    private fun sendNetState(netState: NetState, subscriber: Array<String>) {
        try {
            when (subscriber[2]) {
                SIGNAL_LEVEL -> sendResponse(subscriber[0], TboxBroadcastReceiver.GET_STATE, subscriber[2], netState.signalLevel)
                NET_STATUS -> sendResponse(subscriber[0], TboxBroadcastReceiver.GET_STATE, subscriber[2], netState.netStatus)
                REG_STATUS -> sendResponse(subscriber[0], TboxBroadcastReceiver.GET_STATE, subscriber[2], netState.regStatus)
                SIM_STATUS -> sendResponse(subscriber[0], TboxBroadcastReceiver.GET_STATE, subscriber[2], netState.simStatus)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send NetState response", e)
        }
    }

    private fun sendAPNStatus(apnStatus: Boolean, subscriber: Array<String>) {
        try {
            if (subscriber[2] == APN_STATUS) {
                sendResponse(
                    subscriber[0],
                    TboxBroadcastReceiver.GET_STATE,
                    subscriber[2],
                    apnStatus
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send APN status response", e)
        }
    }

    @OptIn(FlowPreview::class)
    private fun startGeneralStateListener() {
        if (generalStateListenerJob?.isActive == true) {
            Log.d(TAG, "General State listener is already running")
            return
        }

        generalStateListenerJob = scope.launch {
            launch {
                TboxRepository.tboxConnected
                    .sampleWithTimeout(periodMillis = 900, timeoutMillis = 60000)
                    .collect { tboxConnected ->
                        if (generalStateSubscribers.isNotEmpty()) {
                            generalStateSubscribers.forEach { subscriberKey ->
                                val subscriber = parseSubscriberKey(subscriberKey)
                                sendTboxConnected(tboxConnected, subscriber)
                            }
                        }
                    }
            }

            launch {
                TboxRepository.tboxConnectionTime
                    .sampleWithTimeout(periodMillis = 5000, timeoutMillis = 120000)
                    .collect { tboxConnectionTime ->
                        if (generalStateSubscribers.isNotEmpty()) {
                            generalStateSubscribers.forEach { subscriberKey ->
                                val subscriber = parseSubscriberKey(subscriberKey)
                                sendTime(tboxConnectionTime, subscriber, TBOX_CONNECTION_TIME)
                            }
                        }
                    }
            }

            launch {
                TboxRepository.serviceStartTime
                    .sampleWithTimeout(periodMillis = 5000, timeoutMillis = 120000)
                    .collect { serviceStartTime ->
                        if (generalStateSubscribers.isNotEmpty()) {
                            generalStateSubscribers.forEach { subscriberKey ->
                                val subscriber = parseSubscriberKey(subscriberKey)
                                sendTime(serviceStartTime, subscriber, SERVICE_START_TIME)
                            }
                        }
                    }
            }
        }
        Log.d(TAG, "General State broadcast listener started")
    }

    private fun sendTboxConnected(tboxConnected: Boolean, subscriber: Array<String>) {
        try {
            if (subscriber[2] == TBOX_CONNECTED) {
                sendResponse(
                    subscriber[0],
                    TboxBroadcastReceiver.GET_STATE,
                    subscriber[2],
                    tboxConnected
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send tboxConnected response", e)
        }
    }

    private fun sendTime(timeValue: Date, subscriber: Array<String>, extraName: String) {
        try {
            if (subscriber[2] == extraName) {
                sendResponse(
                    subscriber[0],
                    TboxBroadcastReceiver.GET_STATE,
                    subscriber[2],
                    timeValue
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send $extraName response", e)
        }
    }

    private fun sendFloat(value: Float?, subscriber: Array<String>, extraName: String) {
        if (value == null) return
        try {
            if (subscriber[2] == extraName) {
                sendResponse(
                    subscriber[0],
                    TboxBroadcastReceiver.GET_STATE,
                    subscriber[2],
                    value
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send $extraName response", e)
        }
    }

    private fun sendByteArray(value: ByteArray?, subscriber: Array<String>, extraName: String) {
        if (value == null) return
        try {
            if (subscriber[2] == extraName) {
                sendResponse(
                    subscriber[0],
                    TboxBroadcastReceiver.GET_STATE,
                    subscriber[2],
                    value
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send $extraName response", e)
        }
    }

    private fun sendInt(value: Int?, subscriber: Array<String>, extraName: String) {
        if (value == null) return
        try {
            if (subscriber[2] == extraName) {
                sendResponse(
                    subscriber[0],
                    TboxBroadcastReceiver.GET_STATE,
                    subscriber[2],
                    value
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send $extraName response", e)
        }
    }

    private fun sendUInt(value: UInt?, subscriber: Array<String>, extraName: String) {
        if (value == null) return
        try {
            if (subscriber[2] == extraName) {
                sendResponse(
                    subscriber[0],
                    TboxBroadcastReceiver.GET_STATE,
                    subscriber[2],
                    value.toInt()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send $extraName response", e)
        }
    }

    private fun sendBoolean(value: Boolean?, subscriber: Array<String>, extraName: String) {
        if (value == null) return
        try {
            if (subscriber[2] == extraName) {
                sendResponse(
                    subscriber[0],
                    TboxBroadcastReceiver.GET_STATE,
                    subscriber[2],
                    value
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send $extraName response", e)
        }
    }

    @OptIn(FlowPreview::class)
    private fun startEngineStateListener() {
        if (engineStateListenerJob?.isActive == true) {
            Log.d(TAG, "Engine State listener is already running")
            return
        }

        engineStateListenerJob = scope.launch(
            CoroutineExceptionHandler { _, exception ->
                Log.e(TAG, "Engine State listener error", exception)
                // Перезапускаем listener при ошибке
                if (engineStateSubscribers.isNotEmpty()) {
                    startEngineStateListener()
                }
            }
        ) {
            launch {
                CanDataRepository.engineTemperature
                    .sampleWithTimeout(periodMillis = 900, timeoutMillis = 10000)
                    .collect { engineTemperature ->
                        if (engineStateSubscribers.isNotEmpty()) {
                            engineStateSubscribers.forEach { subscriberKey ->
                                val subscriber = parseSubscriberKey(subscriberKey)
                                sendFloat(engineTemperature, subscriber, ENGINE_TEMPERATURE)
                            }
                        }
                    }
            }

            launch {
                CanDataRepository.engineRPM
                    .sampleWithTimeout(periodMillis = 900, timeoutMillis = 10000)
                    .collect { engineRPM ->
                        if (engineStateSubscribers.isNotEmpty()) {
                            engineStateSubscribers.forEach { subscriberKey ->
                                val subscriber = parseSubscriberKey(subscriberKey)
                                sendFloat(engineRPM, subscriber, ENGINE_RPM)
                            }
                        }
                    }
            }
        }
        Log.d(TAG, "Engine State broadcast listener started")
    }

    @OptIn(FlowPreview::class)
    private fun startGearBoxStateListener() {
        if (gearBoxStateListenerJob?.isActive == true) {
            Log.d(TAG, "Gear box State listener is already running")
            return
        }

        gearBoxStateListenerJob = scope.launch(
            CoroutineExceptionHandler { _, exception ->
                Log.e(TAG, "Gear Box State listener error", exception)
                // Перезапускаем listener при ошибке
                if (gearBoxStateSubscribers.isNotEmpty()) {
                    startGearBoxStateListener()
                }
            }
        ) {
            launch {
                CanDataRepository.gearBoxCurrentGear
                    .sampleWithTimeout(periodMillis = 900, timeoutMillis = 10000)
                    .collect { gearBoxCurrentGear ->
                        if (gearBoxStateSubscribers.isNotEmpty()) {
                            gearBoxStateSubscribers.forEach { subscriberKey ->
                                val subscriber = parseSubscriberKey(subscriberKey)
                                sendInt(gearBoxCurrentGear, subscriber, GEAR_BOX_CURRENT_GEAR)
                            }
                        }
                    }
            }

            launch {
                CanDataRepository.gearBoxOilTemperature
                    .sampleWithTimeout(periodMillis = 900, timeoutMillis = 10000)
                    .collect { gearBoxOilTemperature ->
                        if (gearBoxStateSubscribers.isNotEmpty()) {
                            gearBoxStateSubscribers.forEach { subscriberKey ->
                                val subscriber = parseSubscriberKey(subscriberKey)
                                sendInt(gearBoxOilTemperature, subscriber, GEAR_BOX_OIL_TEMPERATURE)
                            }
                        }
                    }
            }
        }
        Log.d(TAG, "Gear box State broadcast listener started")
    }

    @OptIn(FlowPreview::class)
    private fun startCarStateListener() {
        if (carStateListenerJob?.isActive == true) {
            Log.d(TAG, "Car State listener is already running")
            return
        }

        carStateListenerJob = scope.launch(
            CoroutineExceptionHandler { _, exception ->
                Log.e(TAG, "Car State listener error", exception)
                // Перезапускаем listener при ошибке
                if (carStateSubscribers.isNotEmpty()) {
                    startCarStateListener()
                }
            }
        ) {
            launch {
                CanDataRepository.fuelLevelPercentage
                    .sampleWithTimeout(periodMillis = 5000, timeoutMillis = 60000)
                    .collect { fuelLevelPercentage ->
                        if (carStateSubscribers.isNotEmpty()) {
                            carStateSubscribers.forEach { subscriberKey ->
                                val subscriber = parseSubscriberKey(subscriberKey)
                                sendUInt(fuelLevelPercentage, subscriber, FUEL_LEVEL_PERCENTAGE)
                            }
                        }
                    }
            }

            launch {
                CanDataRepository.fuelLevelPercentageFiltered
                    .sampleWithTimeout(periodMillis = 5000, timeoutMillis = 60000)
                    .collect { fuelLevelPercentageFiltered ->
                        if (carStateSubscribers.isNotEmpty()) {
                            carStateSubscribers.forEach { subscriberKey ->
                                val subscriber = parseSubscriberKey(subscriberKey)
                                sendUInt(fuelLevelPercentageFiltered, subscriber, FUEL_LEVEL_PERCENTAGE_FILTERED)
                            }
                        }
                    }
            }

            launch {
                CanDataRepository.cruiseSetSpeed
                    .sampleWithTimeout(periodMillis = 900, timeoutMillis = 10000)
                    .collect { cruiseSetSpeed ->
                        if (carStateSubscribers.isNotEmpty()) {
                            carStateSubscribers.forEach { subscriberKey ->
                                val subscriber = parseSubscriberKey(subscriberKey)
                                sendUInt(cruiseSetSpeed, subscriber, CRUISE_SET_SPEED)
                            }
                        }
                    }
            }

            launch {
                CanDataRepository.voltage
                    .sampleWithTimeout(periodMillis = 2000, timeoutMillis = 10000)
                    .collect { voltage ->
                        if (carStateSubscribers.isNotEmpty()) {
                            carStateSubscribers.forEach { subscriberKey ->
                                val subscriber = parseSubscriberKey(subscriberKey)
                                sendFloat(voltage, subscriber, VOLTAGE)
                            }
                        }
                    }
            }

            launch {
                CanDataRepository.insideTemperature
                    .sampleWithTimeout(periodMillis = 900, timeoutMillis = 10000)
                    .collect { insideTemperature ->
                        if (carStateSubscribers.isNotEmpty()) {
                            carStateSubscribers.forEach { subscriberKey ->
                                val subscriber = parseSubscriberKey(subscriberKey)
                                sendFloat(insideTemperature, subscriber, INSIDE_TEMPERATURE)
                            }
                        }
                    }
            }
        }
        Log.d(TAG, "Car State broadcast listener started")
    }

    @OptIn(FlowPreview::class)
    private fun startLocationStateListener() {
        if (locationStateListenerJob?.isActive == true) {
            Log.d(TAG, "Location State listener is already running")
            return
        }

        locationStateListenerJob = scope.launch(
            CoroutineExceptionHandler { _, exception ->
                Log.e(TAG, "Location State listener error", exception)
                // Перезапускаем listener при ошибке
                if (locationStateSubscribers.isNotEmpty()) {
                    startLocationStateListener()
                }
            }
        ) {
            launch {
                TboxRepository.locValues
                    .sampleWithTimeout(periodMillis = 900, timeoutMillis = 10000)
                    .collect { locValues ->
                        if (locationStateSubscribers.isNotEmpty()) {
                            locationStateSubscribers.forEach { subscriberKey ->
                                val subscriber = parseSubscriberKey(subscriberKey)
                                sendBoolean(locValues.locateStatus, subscriber, LOCATE_STATUS)
                            }
                        }
                    }
            }

            launch {
                TboxRepository.isLocValuesTrue
                    .sampleWithTimeout(periodMillis = 2000, timeoutMillis = 10000)
                    .collect { isLocValuesTrue ->
                        if (locationStateSubscribers.isNotEmpty()) {
                            locationStateSubscribers.forEach { subscriberKey ->
                                val subscriber = parseSubscriberKey(subscriberKey)
                                sendBoolean(isLocValuesTrue, subscriber, IS_LOC_VALUES_TRUE)
                            }
                        }
                    }
            }
        }
        Log.d(TAG, "Location State broadcast listener started")
    }

    @OptIn(FlowPreview::class)
    private fun startCANIDListener() {
        if (canIDListenerJob?.isActive == true) {
            Log.d(TAG, "CANID listener is already running")
            return
        }

        canIDListenerJob = scope.launch(
            CoroutineExceptionHandler { _, exception ->
                Log.e(TAG, "CANID listener error", exception)
                // Перезапускаем listener при ошибке
                if (canIDSubscribers.isNotEmpty()) {
                    startCANIDListener()
                }
            }
        ) {
            var previousFrames: Map<String, List<CanFrame>> = emptyMap()
            CanDataRepository.canFramesStructured
                .sampleWithTimeout(periodMillis = 900, timeoutMillis = 10000)
                .collect { canFramesStructured ->
                    if (canIDSubscribers.isNotEmpty()) {
                        canIDSubscribers.forEach { subscriberKey ->
                            val subscriber = parseSubscriberKey(subscriberKey)
                            val canId = subscriber[2]
                            val current = canFramesStructured[canId]?.lastOrNull()?.rawValue
                            val previous = previousFrames[canId]?.lastOrNull()?.rawValue

                            if (current != null && !current.contentEquals(previous)) {
                                sendByteArray(current, subscriber, canId)
                            }
                        }
                        previousFrames = canFramesStructured
                    }
                }
        }
        Log.d(TAG, "CANID broadcast listener started")
    }

    fun stopListeners() {
        netStateListenerJob?.cancel()
        netStateListenerJob = null

        generalStateListenerJob?.cancel()
        generalStateListenerJob = null

        engineStateListenerJob?.cancel()
        engineStateListenerJob = null

        gearBoxStateListenerJob?.cancel()
        gearBoxStateListenerJob = null

        locationStateListenerJob?.cancel()
        locationStateListenerJob = null

        carStateListenerJob?.cancel()
        carStateListenerJob = null

        canIDListenerJob?.cancel()
        canIDListenerJob = null

        Log.d(TAG, "Listeners stopped")
    }

    private fun createSubscriberKey(packageName: String, extraName: String, extraValue: String): String {
        return "$packageName|$extraName|$extraValue"
    }

    private fun parseSubscriberKey(key: String): Array<String> {
        return key.split("|").toTypedArray()
    }

    private fun getUniquePackageNames(subscribers: Set<String>): Set<String> {
        return subscribers.map { key ->
            parseSubscriberKey(key)[0] // packageName - первый элемент
        }.toSet()
    }
}

private fun <T> Flow<T>.sampleWithTimeout(
    periodMillis: Long,
    timeoutMillis: Long
): Flow<T> = channelFlow {
    var lastValue: T? = null
    var lastEmittedTime = System.currentTimeMillis()

    // Коллектим оригинальный поток
    launch {
        collect { value ->
            lastValue = value
            val currentTime = System.currentTimeMillis()

            // Отправляем если прошло больше periodMillis
            if (currentTime - lastEmittedTime >= periodMillis && lastValue != null) {
                send(lastValue!!)
                lastEmittedTime = currentTime
            }
        }
    }

    // Таймер для гарантированной отправки
    launch {
        while (true) {
            delay(timeoutMillis)
            val currentTime = System.currentTimeMillis()

            // Если есть значение и прошло больше таймаута с последней отправки
            if (lastValue != null && currentTime - lastEmittedTime >= timeoutMillis) {
                send(lastValue!!)
                lastEmittedTime = currentTime
            }
        }
    }
}